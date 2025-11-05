package com.dfs.shared.model;

public class NodeStatus {
	public String nodeId;
	public String role; // NAMENODE, DATANODE, GATEWAY
	public String leaderId;
	public long currentTimeMs;
	public boolean maintenance;
	public double loadMetric;
}


