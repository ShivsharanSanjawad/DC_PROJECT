package com.dfs.namenode.controller;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpEntity;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;
// ✅ *REMOVED*: No longer need ApiController imports, they are in ApiController.java
// import com.dfs.namenode.controller.ApiController.BlockMetadata;
// import com.dfs.namenode.controller.ApiController.FileMetadata;


// ✅ *REMOVED*: No longer need web annotations in a service
// import org.springframework.web.bind.annotation.GetMapping;

import java.time.Instant;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;

@Service
public class NameNodeService {

    private final NameNodeProperties props;
    private final RestTemplate rest;
    private volatile String leaderId;
    private volatile String leaderHost;
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
    private final Map<String, FileMetadata> fileTable = new ConcurrentHashMap<>();
    private final Map<String, Long> nodeLoadTable = new ConcurrentHashMap<>();
    private final Map<String, Long> logicalClock = new ConcurrentHashMap<>();
    private final int BLOCK_SIZE = 128 * 1024 * 1024;

    // concurrency & scheduling
    private final AtomicBoolean electionInProgress = new AtomicBoolean(false);
    private final ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor();

    /**
     * ✅ *FIX for ELECTION TIMEOUT*
     * We need to store the timeout task so we can cancel it
     * if we receive an "OK" from a higher node.
     */
    private volatile ScheduledFuture<?> electionTimeoutTask;

    public NameNodeService(NameNodeProperties props, RestTemplate rest) {
        this.props = props;
        this.rest = rest;

        // Initialize peer and host info safely
        List<String> peers = props.getPeers();
        if (peers != null) {
            for (String p : peers) {
                if (p != null && !p.isEmpty()) {
                    nodeLoadTable.put(p, 0L);
                    logicalClock.put(p, 0L);
                }
            }
        }

        String host = props.getHost();
        if (host != null && !host.isEmpty()) {
            nodeLoadTable.put(host, 0L);
            logicalClock.put(host, 0L);
        }

        // No auto-election: leader starts as null
        leaderId = null;
        leaderHost = null;
    }

    // -------------------------------------------------------
    // ELECTION LOGIC
    // -------------------------------------------------------

    private String getPeerNodeId(String peerHost) {
        if (peerHost == null) return null;
        try {
            String url = peerHost;
            if (!url.endsWith("/")) url += "/";
            url += "api/node-id";
            System.out.println("[DEBUG] Querying peer id at: " + url);
            ResponseEntity<String> resp = rest.getForEntity(url, String.class);
            if (resp != null && resp.getStatusCode().is2xxSuccessful() && resp.getBody() != null) {
                String id = resp.getBody().trim();
                if (!id.isEmpty()) {
                    System.out.println("[DEBUG] Peer " + peerHost + " returned id: " + id);
                    return id;
                }
            }
        } catch (Exception ex) {
            System.out.println("[DEBUG] Could not query peer id from " + peerHost + ": " + ex.getMessage());
        }

        String fallback = extractId(peerHost);
        System.out.println("[DEBUG] Falling back to extracted id '" + fallback + "' for peer " + peerHost);
        return fallback;
    }

    public String startElection() {
        String myId = props.getId();
        if (!electionInProgress.compareAndSet(false, true)) {
            System.out.println("[" + myId + "] Election already in progress, skipping");
            announceCoordinator();
            // return "Election already in progress";
        }

        // Ensure any previous timeout is cancelled
        cancelElectionTimeout();

        System.out.println("[" + myId + "] Starting election...");
        int myNum = getNumericId(myId);

        List<String> higher = new ArrayList<>();
        List<String> peers = props.getPeers() == null ? Collections.emptyList() : props.getPeers();
        for (String peer : peers) {
            if (peer.equals(props.getHost())) continue;

            String peerId = getPeerNodeId(peer);
            int peerNum = getNumericId(peerId);
            System.out.println("[DEBUG] Comparing my " + myNum + " vs peer " + peerNum);

            if (peerNum > myNum) {
                higher.add(peer);
                System.out.println("[" + myId + "] Found higher node: " + peerId + " at " + peer);
            }
        }

        if (higher.isEmpty()) {
            System.out.println("[" + myId + "] No higher nodes, announcing as coordinator");
            announceCoordinator();
            electionInProgress.set(false); // We are done
            return "I am leader";
        } else {
            boolean anyAlive = false;
            for (String peer : higher) {
                try {
                    String url = peer;
                    if (!url.endsWith("/")) url += "/";
                    url += "api/election?candidateId=" + myId + "&candidateHost=" + props.getHost();
                    System.out.println("[" + myId + "] Sending election to: " + url);
                    rest.getForEntity(url, String.class);
                    anyAlive = true;
                } catch (Exception e) {
                    System.out.println("[" + myId + "] Failed to contact: " + peer + " -> " + e.getMessage());
                }
            }

            if (!anyAlive) {
                System.out.println("[" + myId + "] No higher nodes responded, announcing as coordinator");
                announceCoordinator();
                electionInProgress.set(false); // We are done
                return "I am leader";
            }

            System.out.println("[" + myId + "] Election started, waiting for coordinator announcement...");
            
            // ✅ *FIX for ELECTION TIMEOUT*
            // Store the scheduled task so it can be cancelled
            this.electionTimeoutTask = scheduler.schedule(() -> {
                if (leaderId == null) {
                    System.out.println("[" + myId + "] Timeout waiting for coordinator, election ended manually (no auto-restart)");
                    electionInProgress.set(false);
                }
            }, 5, TimeUnit.SECONDS);

            return "Election started, waiting for OK or Coordinator";
        }
    }

    public void receiveElection(String candidateId, String candidateHost) {
        updateLogicalClock(props.getHost());
        String myId = props.getId();
        System.out.println("[" + myId + "] Received election from: " + candidateId);

        // Send "OK" back to the candidate
        try {
            String url = candidateHost;
            if (!url.endsWith("/")) url += "/";
            url += "api/election_ok?responderId=" + myId;
            rest.getForEntity(url, String.class);
            System.out.println("[" + myId + "] Sent OK to: " + candidateId);
        } catch (Exception e) {
            System.out.println("[" + myId + "] Failed to send OK to: " + candidateId + " -> " + e.getMessage());
        }

        int myNum = getNumericId(myId);
        int candidateNum = getNumericId(candidateId);

        // If I am higher, I start my own election
        if (myNum > candidateNum && electionInProgress.compareAndSet(false, true)) {
            System.out.println("[" + myId + "] My ID (" + myNum + ") is higher than " + candidateNum + ", starting my own election");
            new Thread(() -> {
                try {
                    // Short delay to prevent message race conditions
                    Thread.sleep(200); 
                    startElection();
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            }).start();
        }
    }

    /**
     * ✅ *FIX for ELECTION TIMEOUT*
     * This method is called by the new /api/election_ok endpoint.
     * It cancels the timeout, so this node doesn't give up.
     */
    public void receiveElectionOk(String responderId) {
        String myId = props.getId();
        System.out.println("[" + myId + "] Received OK from: " + responderId);
        
        // A higher node is alive. Cancel our timeout and wait for a coordinator msg.
        cancelElectionTimeout();
        System.out.println("[" + myId + "] Cancelled election timeout, waiting for new coordinator.");
    }

    
@Autowired
private RestTemplate restTemplate;

public RestTemplate getRestTemplate() {
    return restTemplate;
}


   public void announceCoordinator() {
    leaderId = props.getId();
    leaderHost = props.getHost();
    electionInProgress.set(false);
    cancelElectionTimeout();

    System.out.println("[" + props.getId() + "] I am the coordinator! Announcing to peers...");

    try {
        // Hardcoded all NameNode URLs
        List<String> peers = List.of(
            "http://localhost:9001/api/coordinator",
            "http://localhost:9002/api/coordinator",
            "http://localhost:9003/api/coordinator"
        );

        for (String peer : peers) {
            String url = peer + "?leaderId=" + leaderId + "&leaderHost=" + leaderHost;
            rest.getForEntity(url, String.class);
            System.out.println("[" + props.getId() + "] Announced coordinator to: " + peer);
        }

    } catch (Exception e) {
        System.out.println("[" + props.getId() + "] Error announcing coordinator: " + e.getMessage());
    }
}


    public void receiveCoordinator(String leaderId, String leaderHost) {
        this.leaderId = leaderId;
        this.leaderHost = leaderHost;
        this.electionInProgress.set(false);
        cancelElectionTimeout(); // A leader has been found, stop any pending timeouts
        updateLogicalClock(props.getHost());
        System.out.println("[" + props.getId() + "] Coordinator received: " + leaderId + " at " + leaderHost);
    }

    public Map<String, Object> getCurrentLeader() {
        Map<String, Object> info = new HashMap<>();
        if (leaderId != null && leaderHost != null) {
            info.put("leaderId", leaderId);
            info.put("leaderHost", leaderHost);
            info.put("status", "elected");
        } else {
            info.put("message", "No leader elected yet");
            info.put("status", "no_leader");
        }
        return info;
    }

    
    // ✅ *FIXED*: Removed @GetMapping. This is just a simple getter.
    // The ApiController handles the web endpoint.
    public String getNodeId() {
        return props.getId();
    }

    // -------------------------------------------------------
    // CLOCK SYNC + FILE METADATA
    // -------------------------------------------------------

    public String startClockSync() {
        Map<String, Long> offsets = new HashMap<>();
        long local = Instant.now().toEpochMilli();
        for (String peer : allNodes()) {
            try {
                ResponseEntity<Long> resp = rest.getForEntity(peer + "/api/time_request", Long.class);
                Long peerTime = resp.getBody();
                if (peerTime != null) offsets.put(peer, peerTime - local);
            } catch (Exception ignored) {}
        }

        long totalOffset = 0;
        for (Long off : offsets.values()) totalOffset += off;
        long avgOffset = offsets.isEmpty() ? 0 : Math.round((double) totalOffset / offsets.size());
        long newTime = Instant.now().toEpochMilli() + avgOffset;
        for (String peer : allNodes()) {
            try {
                HttpEntity<Long> ent = new HttpEntity<>(newTime);
                rest.postForEntity(peer + "/api/time_adjust", ent, Void.class);
            } catch (Exception ignored) {}
        }
        return "Clock sync done, offset=" + avgOffset;
    }

    public Long handleTimeRequest() {
        return Instant.now().toEpochMilli();
    }

    public void handleTimeAdjust(Long newTimeMillis) {
        updateLogicalClock(props.getHost());
    }

    public FileMetadata recordFile(String filename, long filesize, String uploadNode) {
        int blocks = (int) Math.max(1, (filesize + BLOCK_SIZE - 1) / BLOCK_SIZE);
        List<BlockMetadata> blockList = new ArrayList<>();
        List<String> replicaNodes = List.of("localhost:10001", "localhost:10002", "localhost:10003"); // Example data

        for (int i = 0; i < blocks; i++) {
            BlockMetadata b = new BlockMetadata();
            b.blockId = String.format("blk-%04d", i + 1);
            b.size = Math.min(BLOCK_SIZE, filesize - (long) i * BLOCK_SIZE);
            b.replicaNodes = List.of(
                    replicaNodes.get(i % replicaNodes.size()),
                    replicaNodes.get((i + 1) % replicaNodes.size())
            );
            blockList.add(b);
        }

        FileMetadata meta = new FileMetadata();
        meta.filename = filename;
        meta.filesize = filesize;
        meta.uploadNode = uploadNode;
        meta.blocks = blockList;
        fileTable.put(filename, meta);
        nodeLoadTable.put(uploadNode, nodeLoadTable.getOrDefault(uploadNode, 0L) + filesize);
        return meta;
    }

    public Collection<FileMetadata> listFiles() { return fileTable.values(); }
    public Map<String, Long> getNodeLoads() { return nodeLoadTable; }
    public String getLeaderHost() { return leaderHost; }

    // -------------------------------------------------------
    // HELPER METHODS
    // -------------------------------------------------------

    /**
     * ✅ *NEW HELPER*
     * Safely cancels any pending election timeout task.
     */
    private void cancelElectionTimeout() {
        if (this.electionTimeoutTask != null && !this.electionTimeoutTask.isDone()) {
            this.electionTimeoutTask.cancel(false);
            this.electionTimeoutTask = null;
        }
    }

    private List<String> allNodes() {
        List<String> all = new ArrayList<>();
        if (props.getPeers() != null) all.addAll(props.getPeers());
        if (!all.contains(props.getHost())) all.add(props.getHost());
        return all;
    }

    private void updateLogicalClock(String node) {
        logicalClock.put(node, logicalClock.getOrDefault(node, 0L) + 1);
    }

    private String extractId(String host) {
        if (host == null) return null;
        String cleaned = host.replaceFirst("^https?://", "");
        int idx = cleaned.lastIndexOf(':');
        if (idx < 0) return cleaned;
        String portStr = cleaned.substring(idx + 1);
        int slashIdx = portStr.indexOf('/');
        if (slashIdx > 0) portStr = portStr.substring(0, slashIdx);
        // System.out.println("[DEBUG] Extracted ID from " + host + " = " + portStr);
        return portStr;
    }

    /**
     * ✅ *NEW HELPER*
     * Centralized and safe way to get the numeric part of an ID.
     */
    private int getNumericId(String id) {
        if (id == null) return -1;
        try {
            String num = id.replaceAll("[^0-9]", "");
            if (num.isEmpty()) return -1;
            return Integer.parseInt(num);
        } catch (Exception e) {
            System.out.println("[DEBUG] Failed to parse numeric ID from: " + id);
            return -1;
        }
    }

    // ✅ *REMOVED*: Unused compareId method. The new getNumericId helper is cleaner.
    // private int compareId(String a, String b) { ... }
}