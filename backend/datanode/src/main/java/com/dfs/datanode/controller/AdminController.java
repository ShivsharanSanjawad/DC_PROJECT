package com.dfs.datanode.controller;

import com.dfs.shared.model.NodeStatus;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/admin")
public class AdminController {

	@Value("${node.id:dn1}")
	private String nodeId;
	@Value("${leader.id:nn1}")
	private String leaderId;

	@GetMapping("/status")
	public NodeStatus status() {
		NodeStatus s = new NodeStatus();
		s.nodeId = nodeId;
		s.role = "DATANODE";
		s.leaderId = leaderId;
		s.currentTimeMs = System.currentTimeMillis();
		s.maintenance = false;
		s.loadMetric = 0.0;
		return s;
	}
}


