package com.dfs.namenode.controller;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.multipart.MultipartFile;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.*;

/**
 * Simplified NameNode API controller (self-replication fixed)
 */
@RestController
@RequestMapping("/api")
public class ApiController {

    // --- Dependencies ---
    private final RestTemplate restTemplate;
    private final String nodeId;
    private final int serverPort; // Hardcoded mapping from namenode id

    // --- Constants ---
    private static final int BLOCK_SIZE = 128 * 1024 * 1024; // 128MB
    private static final List<String> UPLOAD_NODES = List.of(
            "http://localhost:8001", "http://localhost:8002", "http://localhost:8003"
    );
    private static final List<String> REPLICA_NODES = List.of(
            "http://localhost:10001", "http://localhost:10002", "http://localhost:10003"
    );
    private static final List<String> OTHER_NAMENODES = List.of(
            "http://localhost:9001/api/sync_state",
            "http://localhost:9002/api/sync_state",
            "http://localhost:9003/api/sync_state"
    );
    private static final String STORAGE_FILE = "namenode_metadata.json";
    private static final ObjectMapper mapper = new ObjectMapper();

    // --- State ---
    private final Map<String, FileMetadata> fileTable = new HashMap<>();
    private final Map<String, Long> nodeLoadTable = new HashMap<>();
    private final Object stateLock = new Object();

    // ======== Constructor ========
    public ApiController(RestTemplate restTemplate, @Value("${namenode.id}") String nodeId) {
        this.restTemplate = restTemplate;
        this.nodeId = nodeId;

        // ✅ EASY FIX: Hardcode port based on namenode id
        if (nodeId.equalsIgnoreCase("nn1")) this.serverPort = 9001;
        else if (nodeId.equalsIgnoreCase("nn2")) this.serverPort = 9002;
        else this.serverPort = 9003;

        System.out.println("🧩 This NameNode (" + nodeId + ") runs on port: " + serverPort);

        for (String node : UPLOAD_NODES) {
            nodeLoadTable.put(node, 0L);
        }

        loadFromDisk();
    }

    // ======== DTO Classes ========
    public static class FileMetadata {
        public String filename;
        public long filesize;
        public String uploadNode;
        public List<BlockMetadata> blocks = new ArrayList<>();
    }

    public static class BlockMetadata {
        public String blockId;
        public long size;
        public List<String> replicaNodes;
    }

    // ======== API Endpoints ========

    @GetMapping("/node-id")
    public ResponseEntity<String> getNodeId() {
        return ResponseEntity.ok(nodeId);
    }

    @PostMapping("/upload")
    public Map<String, Object> uploadFile(@RequestParam("file") MultipartFile file) {
        System.out.println("📥 Received upload request for file: " + file.getOriginalFilename() +
                " (" + file.getSize() + " bytes)");

        String filename = file.getOriginalFilename();
        long filesize = file.getSize();

        FileMetadata meta;
        int ackCount;

        synchronized (stateLock) {
            System.out.println("🔒 Acquired state lock for file upload...");

            // Choose upload node
            String uploadNode = chooseLeastLoadedNode();
            System.out.println("📡 Chosen upload node: " + uploadNode);

            // Calculate block count
            int blocks = (int) Math.max(1, (filesize + BLOCK_SIZE - 1) / BLOCK_SIZE);
            System.out.println("📦 File will be split into " + blocks + " block(s)");

            List<BlockMetadata> blockList = new ArrayList<>();

            // Generate blocks
            for (int i = 0; i < blocks; i++) {
                BlockMetadata block = new BlockMetadata();
                block.blockId = String.format("blk-%s-%04d", filename, i + 1);
                block.size = Math.min(BLOCK_SIZE, filesize - (long) i * BLOCK_SIZE);
                block.replicaNodes = List.of(
                        REPLICA_NODES.get(i % REPLICA_NODES.size()),
                        REPLICA_NODES.get((i + 1) % REPLICA_NODES.size()),
                        REPLICA_NODES.get((i + 2) % REPLICA_NODES.size())
                );

                System.out.println("🧩 Block created: ID=" + block.blockId +
                        ", size=" + block.size +
                        ", replicas=" + block.replicaNodes);

                blockList.add(block);
            }

            // Create metadata
            meta = new FileMetadata();
            meta.filename = filename;
            meta.filesize = filesize;
            meta.uploadNode = uploadNode;
            meta.blocks = blockList;

            fileTable.put(filename, meta);
            nodeLoadTable.put(uploadNode, nodeLoadTable.getOrDefault(uploadNode, 0L) + filesize);

            // Save to disk
            try {
                saveToDisk();
                System.out.println("💾 Metadata saved to disk.");
            } catch (Exception e) {
                System.err.println("❌ Failed to save metadata: " + e.getMessage());
            }

            // Replicate to followers
            ackCount = replicateToFollowers(meta);
        }

        Map<String, Object> resp = new HashMap<>();
        resp.put("file", filename);
        resp.put("upload_to", meta.uploadNode);
        resp.put("blocks", meta.blocks);
        resp.put("followers_acknowledged", ackCount);
        resp.put("status", ackCount >= 1 ? "COMMITTED" : "FAILED");

        System.out.println("✅ Upload completed for " + filename + " | Status: " + resp.get("status"));
        return resp;
    }

    @RequestMapping(
            value = "/sync_state",
            method = {RequestMethod.POST, RequestMethod.GET},
            consumes = "application/json",
            produces = "application/json"
    )
    public ResponseEntity<String> syncState(@RequestBody(required = false) FileMetadata meta) {
        if (meta == null) {
            return ResponseEntity.ok("{\"status\":\"ok\",\"node\":\"" + nodeId + "\"}");
        }

        synchronized (stateLock) {
            try {
                System.out.println("📦 Received replication for file: " + meta.filename);
                fileTable.put(meta.filename, meta);
                nodeLoadTable.put(meta.uploadNode,
                        nodeLoadTable.getOrDefault(meta.uploadNode, 0L) + meta.filesize);
                saveToDisk();
                System.out.println("✅ Synced file: " + meta.filename);
            } catch (Exception e) {
                return ResponseEntity.status(500).body("{\"error\":\"" + e.getMessage() + "\"}");
            }
        }
        return ResponseEntity.ok("{\"status\":\"ack\"}");
    }

    @GetMapping("/list-files")
    public ResponseEntity<?> getFileList() {
        List<Map<String, Object>> list = new ArrayList<>();
        for (FileMetadata meta : fileTable.values()) {
            Map<String, Object> fileInfo = new HashMap<>();
            fileInfo.put("filename", meta.filename);
            fileInfo.put("filesize", meta.filesize);
            list.add(fileInfo);
        }
        return ResponseEntity.ok(list);
    }

    @GetMapping("/file-info")
    public ResponseEntity<?> getFileInfo(@RequestParam("filename") String filename) {
        FileMetadata meta = fileTable.get(filename);
        if (meta == null) return ResponseEntity.notFound().build();
        return ResponseEntity.ok(meta);
    }

    @GetMapping("/node-loads")
    public ResponseEntity<?> getNodeLoads() {
        return ResponseEntity.ok(nodeLoadTable);
    }

    // ======== Utility ========

    private String chooseLeastLoadedNode() {
        return nodeLoadTable.entrySet()
                .stream()
                .min(Comparator.comparingLong(Map.Entry::getValue))
                .map(Map.Entry::getKey)
                .orElse(UPLOAD_NODES.get(0));
    }

    private int replicateToFollowers(FileMetadata meta) {
        int success = 0;
        System.out.println("🔁 Starting metadata replication...");

        for (String url : OTHER_NAMENODES) {
            // ✅ Skip self node based on port
            if (url.contains(":" + serverPort)) {
                System.out.println("🚫 Skipping self-replication for " + url);
                continue;
            }

            long start = System.currentTimeMillis();
            try {
                ResponseEntity<String> res = restTemplate.postForEntity(url, meta, String.class);
                long time = System.currentTimeMillis() - start;

                if (res.getStatusCode().is2xxSuccessful()) {
                    success++;
                    System.out.println("✅ Replicated to " + url + " | Time: " + time + "ms");
                } else {
                    System.out.println("⚠️ Follower " + url + " returned " + res.getStatusCode());
                }
            } catch (Exception e) {
                System.out.println("❌ Replication failed for " + url + " | " + e.getMessage());
            }
        }

        System.out.println("📬 Replication done: " + success + "/" + OTHER_NAMENODES.size());
        return success;
    }

    // ======== Persistence ========

    private void saveToDisk() {
        try {
            File file = new File(STORAGE_FILE);
            mapper.writerWithDefaultPrettyPrinter().writeValue(file, fileTable);
        } catch (IOException e) {
            System.err.println("❌ Failed to save metadata: " + e.getMessage());
        }
    }

    private void loadFromDisk() {
        try {
            File file = new File(STORAGE_FILE);
            if (file.exists()) {
                String json = Files.readString(file.toPath());
                if (!json.isBlank()) {
                    Map<String, FileMetadata> loaded = mapper.readValue(json, new TypeReference<>() {});
                    fileTable.putAll(loaded);
                    System.out.println("📂 Loaded " + loaded.size() + " files from disk.");

                    nodeLoadTable.replaceAll((node, load) -> 0L);
                    for (FileMetadata meta : fileTable.values()) {
                        nodeLoadTable.put(meta.uploadNode,
                                nodeLoadTable.getOrDefault(meta.uploadNode, 0L) + meta.filesize);
                    }
                    System.out.println("✅ Node loads recalculated.");
                }
            }
        } catch (Exception e) {
            System.err.println("⚠ Could not load previous metadata: " + e.getMessage());
        }
    }
}
