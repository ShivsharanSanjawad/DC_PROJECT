package com.dfs.namenode.controller;

import com.dfs.shared.model.NodeStatus;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/admin")
public class AdminController {

	@Value("${node.id:nn1}")
	private String nodeId;
	private volatile String leaderId = "nn1";
	private volatile boolean maintenance = false;

	@GetMapping("/status")
	public NodeStatus status() {
		NodeStatus s = new NodeStatus();
		s.nodeId = nodeId;
		s.role = "NAMENODE";
		s.leaderId = leaderId;
		s.currentTimeMs = System.currentTimeMillis();
		s.maintenance = maintenance;
		s.loadMetric = 0.0;
		return s;
	}

	@PostMapping("/election")
	public void triggerElection() {
		leaderId = nodeId; // stub: self-elect for now
	}
}


