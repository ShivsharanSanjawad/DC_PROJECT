package com.dfs.datanode.block;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.PostConstruct;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.*;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

@Component
public class BlockServer {

    @Value("${DATANODE_BLOCK_PORT:10001}")
    private int port;

    @Value("${DATANODE_DATA_DIR:./data/dn1}")
    private String dataDir;

    private final ObjectMapper mapper = new ObjectMapper();
    private final ExecutorService executor = Executors.newFixedThreadPool(20);

    @PostConstruct
    public void start() {
        System.out.println("🧭 BlockServer @PostConstruct called");
        System.out.println("   Port: " + port);
        System.out.println("   DataDir: " + dataDir);
        
        Thread serverThread = new Thread(this::run, "BlockServer-" + port);
        serverThread.setDaemon(false);
        serverThread.start();
        
        System.out.println("✅ BlockServer thread started for port " + port);
    }

    private void run() {
        System.out.println("🚀 BlockServer.run() started on thread: " + Thread.currentThread().getName());
        
        try {
            Path blocksDir = Path.of(dataDir, "blocks");
            Files.createDirectories(blocksDir);
            System.out.println("📁 Created directory: " + blocksDir.toAbsolutePath());
            
            ServerSocket serverSocket = new ServerSocket();
            serverSocket.setReuseAddress(true);
            serverSocket.bind(new java.net.InetSocketAddress("0.0.0.0", port));
            
            System.out.println("🎯 ============================================");
            System.out.println("🎯 DATANODE BLOCK SERVER READY");
            System.out.println("🎯 Listening on: 0.0.0.0:" + port);
            System.out.println("🎯 Storage Dir: " + blocksDir.toAbsolutePath());
            System.out.println("🎯 ============================================");
            
            while (true) {
                try {
                    Socket client = serverSocket.accept();
                    String clientAddr = client.getInetAddress().getHostAddress() + ":" + client.getPort();
                    System.out.println("🔗 New connection from: " + clientAddr);
                    executor.submit(() -> handleClient(client));
                } catch (IOException e) {
                    System.err.println("⚠️ Error accepting connection: " + e.getMessage());
                }
            }
        } catch (IOException e) {
            System.err.println("❌❌❌ CRITICAL: BlockServer FAILED to start on port " + port);
            System.err.println("Error: " + e.getMessage());
            e.printStackTrace();
            System.err.println("❌❌❌ DataNode will NOT accept block transfers!");
        }
    }

    
    private void handleClient(Socket socket) {
    String clientInfo = socket.getInetAddress().getHostAddress() + ":" + socket.getPort();
    System.out.println("👋 Handling client: " + clientInfo);
    
    BufferedReader reader = null;
    
    try {
        socket.setSoTimeout(30000); // 30 second timeout
        
        reader = new BufferedReader(
                new InputStreamReader(socket.getInputStream(), StandardCharsets.UTF_8)
        );
        
        String headerLine = reader.readLine();
        System.out.println("📨 Received header: " + headerLine);
        
        if (headerLine == null || headerLine.isBlank()) {
            System.err.println("❌ Empty or null header received");
            sendError(socket, "Empty request");
            return;
        }

        JsonNode header = mapper.readTree(headerLine);
        String action = header.path("action").asText("");
        System.out.println("⚡ Action: " + action);

        switch (action) {
            case "upload_block" -> handleUpload(socket, header);
            case "download_block" -> handleDownload(socket, header);
            case "delete_block" -> handleDelete(socket, header);
            case "check_block" -> handleCheck(socket, header);
            default -> {
                System.err.println("❓ Unknown action: " + action);
                sendError(socket, "Unknown action: " + action);
            }
        }

    } catch (Exception e) {
        System.err.println("❌ Error handling client " + clientInfo + ": " + e.getMessage());
        e.printStackTrace();
        try {
            sendError(socket, "Internal error: " + e.getMessage());
        } catch (Exception ignored) {}
    } finally {
        try {
            if (reader != null) reader.close();
            if (socket != null && !socket.isClosed()) {
                socket.close();
                System.out.println("🔌 Closed connection to " + clientInfo);
            }
        } catch (IOException e) {
            System.err.println("⚠️ Error closing socket: " + e.getMessage());
        }
    }
}

private void handleUpload(Socket socket, JsonNode header) {
    String blockId = header.path("block_id").asText("blk-unknown");
    long blockSize = header.path("block_size").asLong(0);

    System.out.println("📥 UPLOAD REQUEST: " + blockId + " (" + blockSize + " bytes)");

    if (blockSize <= 0) {
        System.err.println("❌ Invalid block size: " + blockSize);
        sendError(socket, "Invalid block size");
        return;
    }

    Path blockPath = Path.of(dataDir, "blocks", blockId + ".bin");
    FileOutputStream fileOut = null;
    
    try {
        InputStream in = socket.getInputStream();
        fileOut = new FileOutputStream(blockPath.toFile());
        BufferedOutputStream bufferedOut = new BufferedOutputStream(fileOut, 8192);

        byte[] buf = new byte[8192];
        long remaining = blockSize;
        long totalRead = 0;

        System.out.println("📡 Starting to receive data...");
        
        while (remaining > 0) {
            int toRead = (int) Math.min(buf.length, remaining);
            int read = in.read(buf, 0, toRead);
            
            if (read == -1) {
                bufferedOut.close();
                fileOut.close();
                fileOut = null;
                
                // Delete incomplete file
                try {
                    Files.deleteIfExists(blockPath);
                } catch (Exception ignored) {}
                
                throw new IOException("Unexpected EOF - received " + totalRead + "/" + blockSize + " bytes");
            }
            
            bufferedOut.write(buf, 0, read);
            remaining -= read;
            totalRead += read;
            
            if (totalRead % 32768 == 0 || remaining == 0) {
                System.out.println("   Progress: " + totalRead + "/" + blockSize + " bytes");
            }
        }
        
        bufferedOut.flush();
        bufferedOut.close();
        fileOut.close();
        fileOut = null;

        // Verify file size
        long actualSize = Files.size(blockPath);
        if (actualSize != blockSize) {
            Files.deleteIfExists(blockPath);
            throw new IOException("File size mismatch: expected " + blockSize + " but got " + actualSize);
        }

        System.out.println("✅ Block stored successfully: " + blockId);
        System.out.println("   Size: " + blockSize + " bytes");
        System.out.println("   Path: " + blockPath.toAbsolutePath());
        
        // Send success response
        sendSuccess(socket, "Block uploaded successfully");

    } catch (Exception e) {
        System.err.println("❌ Upload failed for " + blockId);
        System.err.println("   Error: " + e.getMessage());
        e.printStackTrace();
        
        // Clean up partial file
        try {
            if (Files.exists(blockPath)) {
                Files.deleteIfExists(blockPath);
                System.out.println("🗑️ Cleaned up partial file: " + blockPath);
            }
        } catch (Exception ignored) {}
        
        sendError(socket, "Upload failed: " + e.getMessage());
    } finally {
        if (fileOut != null) {
            try {
                fileOut.close();
            } catch (IOException ignored) {}
        }
    }
}

    private void handleDownload(Socket socket, JsonNode header) {
        String blockId = header.path("block_id").asText();
        Path blockPath = Path.of(dataDir, "blocks", blockId + ".bin");

        System.out.println("📤 DOWNLOAD REQUEST: " + blockId);

        try {
            if (!Files.exists(blockPath)) {
                System.err.println("❌ Block not found: " + blockId);
                sendError(socket, "Block not found: " + blockId);
                return;
            }

            long size = Files.size(blockPath);
            String response = "{\"status\":\"success\",\"block_id\":\"" + blockId +
                    "\",\"block_size\":" + size + "}\n";
            
            OutputStream out = socket.getOutputStream();
            out.write(response.getBytes(StandardCharsets.UTF_8));
            out.flush();

            try (InputStream in = Files.newInputStream(blockPath)) {
                byte[] buffer = new byte[8192];
                int bytesRead;
                long totalSent = 0;
                
                while ((bytesRead = in.read(buffer)) != -1) {
                    out.write(buffer, 0, bytesRead);
                    totalSent += bytesRead;
                }
                out.flush();
                
                System.out.println("✅ Sent block " + blockId + " (" + totalSent + " bytes)");
            }

        } catch (IOException e) {
            System.err.println("❌ Download failed: " + e.getMessage());
            e.printStackTrace();
            sendError(socket, "Download failed: " + e.getMessage());
        }
    }

    private void handleDelete(Socket socket, JsonNode header) {
        String blockId = header.path("block_id").asText();
        Path blockPath = Path.of(dataDir, "blocks", blockId + ".bin");

        System.out.println("🗑️ DELETE REQUEST: " + blockId);

        try {
            if (Files.exists(blockPath)) {
                Files.delete(blockPath);
                sendSuccess(socket, "Block deleted successfully");
                System.out.println("✅ Deleted block " + blockId);
            } else {
                System.err.println("⚠️ Block not found: " + blockId);
                sendError(socket, "Block not found: " + blockId);
            }
        } catch (IOException e) {
            System.err.println("❌ Delete failed: " + e.getMessage());
            e.printStackTrace();
            sendError(socket, "Delete failed: " + e.getMessage());
        }
    }

    private void handleCheck(Socket socket, JsonNode header) {
        String blockId = header.path("block_id").asText();
        Path blockPath = Path.of(dataDir, "blocks", blockId + ".bin");

        System.out.println("🔍 CHECK REQUEST: " + blockId);

        try {
            boolean exists = Files.exists(blockPath);
            long size = exists ? Files.size(blockPath) : 0;
            String response = "{\"status\":\"success\",\"exists\":" + exists +
                    ",\"block_id\":\"" + blockId + "\",\"size\":" + size + "}\n";
            
            OutputStream out = socket.getOutputStream();
            out.write(response.getBytes(StandardCharsets.UTF_8));
            out.flush();
            
            System.out.println("✅ Check result - exists: " + exists + ", size: " + size);
        } catch (IOException e) {
            System.err.println("❌ Check failed: " + e.getMessage());
            e.printStackTrace();
            sendError(socket, "Check failed: " + e.getMessage());
        }
    }

    private void sendSuccess(Socket socket, String message) {
        try {
            String resp = "{\"status\":\"success\",\"message\":\"" + message + "\"}\n";
            OutputStream out = socket.getOutputStream();
            out.write(resp.getBytes(StandardCharsets.UTF_8));
            out.flush();
            System.out.println("✅ Sent success response: " + message);
        } catch (IOException e) {
            System.err.println("❌ Failed to send success response: " + e.getMessage());
        }
    }

    private void sendError(Socket socket, String message) {
        try {
            String resp = "{\"status\":\"error\",\"message\":\"" + message + "\"}\n";
            OutputStream out = socket.getOutputStream();
            out.write(resp.getBytes(StandardCharsets.UTF_8));
            out.flush();
            System.out.println("⚠️ Sent error response: " + message);
        } catch (IOException e) {
            System.err.println("❌ Failed to send error response: " + e.getMessage());
        }
    }
}