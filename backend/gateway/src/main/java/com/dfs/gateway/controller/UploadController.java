package com.dfs.gateway.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody;

import jakarta.annotation.PreDestroy;
import java.io.*;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.SocketTimeoutException;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.*;

@RestController
@RequestMapping("/api")
public class UploadController {

    private final ExecutorService executorService = Executors.newFixedThreadPool(10);
    private final ObjectMapper objectMapper = new ObjectMapper();

    // ------------------------- UPLOAD -------------------------
    @PostMapping(value = "/upload", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<String> upload(
            @RequestParam("file") MultipartFile file,
            @RequestParam("metadata") String metadataJson
    ) throws IOException {

        System.out.println("📂 Received upload for: " + file.getOriginalFilename() +
                " (" + file.getSize() + " bytes)");

        JsonNode metadataTree = objectMapper.readTree(metadataJson);
        if (metadataTree == null || metadataTree.isEmpty()) {
            return ResponseEntity.badRequest().body("{\"error\":\"Invalid or missing metadata\"}");
        }

        JsonNode fileMeta = metadataTree.elements().next();
        JsonNode blocksArray = fileMeta.get("blocks");
        if (blocksArray == null || !blocksArray.isArray()) {
            return ResponseEntity.badRequest().body("{\"error\":\"Invalid blocks in metadata\"}");
        }

        byte[] fileBytes = file.getBytes();
        List<Future<UploadResult>> futures = new ArrayList<>();

        // ✅ FIX: Use cumulative offset instead of block index calculation
        long cumulativeOffset = 0;
        
        for (JsonNode block : blocksArray) {
            String blockId = block.get("blockId").asText();
            long blockSize = block.get("size").asLong();
            JsonNode replicas = block.get("replicaNodes");

            // Calculate actual size (might be less than blockSize for last block)
            long actualSize = Math.min(blockSize, fileBytes.length - cumulativeOffset);

            // Extract the correct byte range using cumulative offset
            byte[] blockData = Arrays.copyOfRange(fileBytes, (int) cumulativeOffset, 
                                                  (int) (cumulativeOffset + actualSize));

            System.out.println("🚀 Uploading block " + blockId + " (" + actualSize + 
                             " bytes) from offset " + cumulativeOffset);

            for (JsonNode replica : replicas) {
                String replicaAddr = replica.asText();
                Future<UploadResult> future = executorService.submit(() ->
                        uploadBlock(blockId, blockData, replicaAddr)
                );
                futures.add(future);
            }
            
            // ✅ Move offset forward for next block
            cumulativeOffset += actualSize;
        }

        int success = 0, fail = 0;
        for (Future<UploadResult> f : futures) {
            try {
                UploadResult r = f.get(30, TimeUnit.SECONDS);
                if (r.success) success++; else fail++;
            } catch (Exception e) {
                fail++;
                System.err.println("Upload future failed: " + e.getMessage());
            }
        }

        System.out.println("✅ Upload completed. Success: " + success + " | Failed: " + fail);

        return ResponseEntity.ok("{\"status\":\"success\",\"uploaded_blocks\":" + success +
                ",\"failed_blocks\":" + fail + "}");
    }

    // ------------------------- DOWNLOAD -------------------------
    @PostMapping(value = "/download", consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<StreamingResponseBody> download(@RequestBody String metadataJson) throws IOException {

        JsonNode metadataTree = objectMapper.readTree(metadataJson);
        JsonNode fileMeta = metadataTree.elements().next();
        String filename = fileMeta.get("filename").asText();
        JsonNode blocksArray = fileMeta.get("blocks");

        System.out.println("📥 ========================================");
        System.out.println("📥 STREAMING DOWNLOAD: " + filename);
        System.out.println("📥 Total blocks to download: " + blocksArray.size());
        System.out.println("📥 ========================================");

        StreamingResponseBody responseBody = outputStream -> {
            List<JsonNode> sortedBlocks = new ArrayList<>();
            blocksArray.forEach(sortedBlocks::add);

            // ✅ Sort blocks by their numeric index
            sortedBlocks.sort(Comparator.comparingInt(blockNode ->
                    extractBlockIndex(blockNode.get("blockId").asText())
            ));
            
            System.out.println("✅ Block processing order:");
            for (int i = 0; i < sortedBlocks.size(); i++) {
                System.out.println("   " + (i+1) + ". " + sortedBlocks.get(i).get("blockId").asText());
            }

            int blockNumber = 0;
            for (JsonNode blockNode : sortedBlocks) {
                blockNumber++;
                String blockId = blockNode.get("blockId").asText();
                JsonNode replicas = blockNode.get("replicaNodes");

                System.out.println("\n📦 Processing block " + blockNumber + "/" + sortedBlocks.size() + ": " + blockId);

                List<String> replicaList = new ArrayList<>();
                replicas.forEach(r -> replicaList.add(r.asText()));

                BlockData blockData = downloadBlock(blockId, replicaList);

                if (blockData != null && blockData.data.length > 0) {
                    System.out.println("   ✍️  Writing " + blockData.data.length + " bytes to output stream...");
                    outputStream.write(blockData.data);
                    outputStream.flush();
                    System.out.println("   ✅ Block " + blockNumber + " written successfully");
                } else {
                    System.err.println("❌❌❌ FATAL: Failed to download block " + blockNumber + "/" + 
                                     sortedBlocks.size() + ": " + blockId);
                    System.err.println("   This will result in incomplete/corrupted file!");
                    throw new IOException("Failed to download critical block: " + blockId + 
                                        " (block " + blockNumber + "/" + sortedBlocks.size() + ")");
                }
            }
            System.out.println("\n✅✅✅ Successfully streamed ALL " + blockNumber + " blocks for " + filename);
        };

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_OCTET_STREAM);
        headers.setContentDispositionFormData("attachment", filename);

        return ResponseEntity.ok().headers(headers).body(responseBody);
    }

    // ------------------------- SOCKET OPS -------------------------

    private UploadResult uploadBlock(String blockId, byte[] data, String nodeAddress) {
        Socket socket = null;
        OutputStream out = null;
        BufferedReader reader = null;
        
        try {
            String host = extractHost(nodeAddress);
            int port = extractPort(nodeAddress);

            System.out.println("🔌 Attempting to connect to " + host + ":" + port + " for " + blockId);

            socket = new Socket();
            socket.setTcpNoDelay(true);
            socket.connect(new InetSocketAddress(host, port), 5000);
            socket.setSoTimeout(30000);

            out = socket.getOutputStream();

            // --- Send header with newline ---
            String header = "{\"action\":\"upload_block\",\"block_id\":\"" + blockId +
                    "\",\"block_size\":" + data.length + "}\n";
            out.write(header.getBytes(StandardCharsets.UTF_8));
            out.flush();
            
            System.out.println("📤 Sent header for " + blockId + " (" + data.length + " bytes)");

            Thread.sleep(50);

            // --- Send data in chunks ---
            int chunkSize = 8192;
            int offset = 0;
            while (offset < data.length) {
                int toWrite = Math.min(chunkSize, data.length - offset);
                out.write(data, offset, toWrite);
                offset += toWrite;

                if (offset % 32768 == 0 || offset == data.length) {
                    out.flush();
                    System.out.println("   Sent: " + offset + "/" + data.length + " bytes");
                }
            }

            out.flush();
            System.out.println("✅ Finished sending data for " + blockId);

            // --- Read response ---
            reader = new BufferedReader(
                    new InputStreamReader(socket.getInputStream(), StandardCharsets.UTF_8)
            );
            
            socket.setSoTimeout(15000);
            String response = reader.readLine();

            if (response == null || response.isEmpty()) {
                System.err.println("❌ Empty or null response from " + nodeAddress);
                return new UploadResult(blockId, nodeAddress, false);
            }

            boolean success = response.contains("\"status\":\"success\"");
            System.out.println((success ? "🟩 SUCCESS" : "🟥 FAILED") + " - " + blockId + " to " + nodeAddress);
            System.out.println("   Response: " + response);

            return new UploadResult(blockId, nodeAddress, success);

        } catch (SocketTimeoutException e) {
            System.err.println("❌ Upload TIMEOUT for " + blockId + " to " + nodeAddress);
            return new UploadResult(blockId, nodeAddress, false);
        } catch (Exception e) {
            System.err.println("❌ Upload failed for " + blockId + " to " + nodeAddress);
            System.err.println("   Error: " + e.getMessage());
            e.printStackTrace();
            return new UploadResult(blockId, nodeAddress, false);
        } finally {
            try {
                if (reader != null) reader.close();
                if (out != null) out.close();
                if (socket != null && !socket.isClosed()) socket.close();
            } catch (IOException ignored) {}
        }
    }

    private BlockData downloadBlock(String blockId, List<String> replicas) {
        System.out.println("🔍 Starting download for block: " + blockId);
        System.out.println("   Available replicas: " + replicas);
        
        for (int i = 0; i < replicas.size(); i++) {
            String replica = replicas.get(i);
            System.out.println("   Attempting replica " + (i+1) + "/" + replicas.size() + ": " + replica);
            
            Socket socket = null;
            try {
                String host = extractHost(replica);
                int port = extractPort(replica);
                System.out.println("   Connecting to " + host + ":" + port);

                socket = new Socket();
                socket.connect(new InetSocketAddress(host, port), 10000);
                socket.setSoTimeout(30000);

                String request = "{\"action\":\"download_block\",\"block_id\":\"" + blockId + "\"}\n";
                System.out.println("   Sending request: " + request.trim());
                
                OutputStream out = socket.getOutputStream();
                out.write(request.getBytes(StandardCharsets.UTF_8));
                out.flush();

                BufferedReader reader = new BufferedReader(
                        new InputStreamReader(socket.getInputStream(), StandardCharsets.UTF_8)
                );
                
                System.out.println("   Waiting for header response...");
                String headerLine = reader.readLine();
                
                if (headerLine == null) {
                    System.err.println("   ❌ NULL header received from " + replica);
                    continue;
                }
                
                System.out.println("   Received header: " + headerLine);
                
                if (headerLine.contains("error")) {
                    System.err.println("   ❌ Error in header: " + headerLine);
                    continue;
                }

                JsonNode header = objectMapper.readTree(headerLine);
                int size = header.get("block_size").asInt();
                System.out.println("   Expected block size: " + size + " bytes");

                byte[] data = new byte[size];
                InputStream in = socket.getInputStream();
                int read, total = 0;
                long startTime = System.currentTimeMillis();
                
                while (total < size && (read = in.read(data, total, size - total)) != -1) {
                    total += read;
                    
                    if (total % (10 * 1024 * 1024) == 0 || total == size) {
                        long elapsed = System.currentTimeMillis() - startTime;
                        double speed = (total / 1024.0 / 1024.0) / (elapsed / 1000.0);
                        System.out.println("   Progress: " + total + "/" + size + 
                                         " bytes (" + String.format("%.2f", (total*100.0/size)) + "%) " +
                                         String.format("%.2f MB/s", speed));
                    }
                }

                if (total < size) {
                    System.err.println("   ⚠️  Incomplete read: got " + total + " bytes, expected " + size);
                    continue;
                }

                System.out.println("   ✅ Successfully downloaded " + blockId + " from " + replica +
                        " (" + total + " bytes in " + (System.currentTimeMillis() - startTime) + "ms)");
                return new BlockData(blockId, data);
                
            } catch (SocketTimeoutException e) {
                System.err.println("   ❌ TIMEOUT downloading from " + replica + ": " + e.getMessage());
            } catch (IOException e) {
                System.err.println("   ❌ IO Error from " + replica + ": " + e.getMessage());
                e.printStackTrace();
            } catch (Exception e) {
                System.err.println("   ❌ Unexpected error from " + replica + ": " + e.getMessage());
                e.printStackTrace();
            } finally {
                if (socket != null && !socket.isClosed()) {
                    try {
                        socket.close();
                    } catch (IOException ignored) {}
                }
            }
        }
        
        System.err.println("❌❌❌ CRITICAL: Failed to download block " + blockId + " from ALL replicas!");
        return null;
    }

    // ------------------------- UTIL -------------------------

    // ✅ FIX: Return zero-based index (0, 1, 2...) for proper offset calculation
    private int extractBlockIndex(String blockId) {
        try {
            String[] parts = blockId.split("-");
            // "blk-TRy.mp4-0001" returns 0, "blk-TRy.mp4-0002" returns 1, etc.
            return Integer.parseInt(parts[parts.length - 1]) - 1;
        } catch (Exception e) {
            System.err.println("Could not parse block index from: " + blockId);
            return -1;
        }
    }

    private String extractHost(String nodeAddress) {
        String hostPort = nodeAddress.replace("http://", "").replace("https://", "");
        return hostPort.split(":")[0];
    }

    private int extractPort(String nodeAddress) {
        String[] parts = nodeAddress.replace("http://", "").replace("https://", "").split(":");
        return Integer.parseInt(parts[1]);
    }

    @PreDestroy
    public void shutdown() {
        executorService.shutdown();
    }

    // ------------------------- Inner Classes -------------------------

    private static class UploadResult {
        String blockId;
        String nodeAddress;
        boolean success;
        public UploadResult(String blockId, String nodeAddress, boolean success) {
            this.blockId = blockId;
            this.nodeAddress = nodeAddress;
            this.success = success;
        }
    }

    private static class BlockData {
        String blockId;
        byte[] data;
        public BlockData(String blockId, byte[] data) {
            this.blockId = blockId;
            this.data = data;
        }
    }
}