package com.dfs.namenode.controller;

import java.util.Map;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api")
public class ElectionController {

    private final NameNodeService service;
    
    public ElectionController(NameNodeService service) {
        this.service = service;
    }

    // Support both GET and POST for manual election trigger
    @PostMapping("/startElection")
    public ResponseEntity<String> startElectionPost() {
        return ResponseEntity.ok(service.startElection());
    }
    
    @GetMapping("/startElection")
    public ResponseEntity<String> startElectionGet() {
        return ResponseEntity.ok(service.startElection());
    }

    @GetMapping("/election")
    public ResponseEntity<Void> election(
            @RequestParam("candidateId") String candidateId, 
            @RequestParam("candidateHost") String candidateHost) {
        service.receiveElection(candidateId, candidateHost);
        return ResponseEntity.ok().build();
    }

    @GetMapping("/election_ok")
    public ResponseEntity<Void> electionOk(@RequestParam("responderId") String responderId) {
        // Just acknowledge - no processing needed
        System.out.println("Received OK from: " + responderId);
        return ResponseEntity.ok().build();
    }

    @GetMapping("/coordinator")
    public ResponseEntity<Void> coordinator(
            @RequestParam("leaderId") String leaderId, 
            @RequestParam("leaderHost") String leaderHost) {
        service.receiveCoordinator(leaderId, leaderHost);
        return ResponseEntity.ok().build();
    }

   @GetMapping("/leader")
public ResponseEntity<Map<String, Object>> getLeader() {
    Map<String, Object> leaderInfo = service.getCurrentLeader();

    // If leader info is missing
    if (leaderInfo == null || !leaderInfo.containsKey("leaderHost")) {
        System.out.println("No leader found — starting election...");
        String result = service.startElection();
        leaderInfo = service.getCurrentLeader();
        return ResponseEntity.ok(Map.of(
            "status", "NEW_ELECTION",
            "message", result,
            "leader", leaderInfo
        ));
    }

    // Check if leader is alive
    String leaderHost = (String) leaderInfo.get("leaderHost");
    try {
        String url = leaderHost;
        if (!url.startsWith("http")) url = "http://" + url;
        if (!url.endsWith("/")) url += "/";
        url += "api/time"; // simple heartbeat check

        Long response = service.getRestTemplate()
                .getForObject(url, Long.class);

        if (response != null) {
            System.out.println("Leader is alive: " + leaderHost);
            return ResponseEntity.ok(Map.of(
                "status", "LEADER_ALIVE",
                "leader", leaderInfo
            ));
        }
    } catch (Exception e) {
        System.out.println("Leader seems down: " + leaderHost + " -> " + e.getMessage());
    }

    // Leader was not reachable — start new election
    System.out.println("Leader unreachable — starting election...");
    String electionResult = service.startElection();
    leaderInfo = service.getCurrentLeader();

    return ResponseEntity.ok(Map.of(
        "status", "NEW_ELECTION",
        "message", electionResult,
        "leader", leaderInfo
    ));
}


    @GetMapping("/time")
    public ResponseEntity<Long> getCurrentTime() {
        return ResponseEntity.ok(service.handleTimeRequest());
    }

    @PostMapping("/startClockSync")
    public ResponseEntity<String> startClockSync() {
        return ResponseEntity.ok(service.startClockSync());
    }

    @GetMapping("/time_request")
    public ResponseEntity<Long> timeRequest() {
        return ResponseEntity.ok(service.handleTimeRequest());
    }

    @PostMapping("/time_adjust")
    public ResponseEntity<Void> timeAdjust(@RequestBody Long newTimeMillis) {
        service.handleTimeAdjust(newTimeMillis);
        return ResponseEntity.ok().build();
    }
}