package com.dfs.shared.cluster;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

public class ClusterConfig {

	public List<NodeRef> namenodes;
	public List<DataNodeRef> datanodes;
	public List<NodeRef> gateways;
	public Defaults defaults;

	@JsonIgnoreProperties(ignoreUnknown = true)
	public static class NodeRef {
		public String id;
		public String host;
		public int restPort;
	}

	@JsonIgnoreProperties(ignoreUnknown = true)
	public static class DataNodeRef extends NodeRef {
		public int blockPort;
		public String dataDir;
	}

	@JsonIgnoreProperties(ignoreUnknown = true)
	public static class Defaults {
		public int replicationFactor;
		public int blockSizeMB;
		public int heartbeatIntervalSec;
		public int clockSyncIntervalSec;
	}

	public static ClusterConfig load(Path path) {
		ObjectMapper mapper = new ObjectMapper();
		try (InputStream in = Files.newInputStream(path)) {
			return mapper.readValue(in, new TypeReference<>() {});
		} catch (IOException e) {
			throw new RuntimeException("Failed to load cluster config from " + path, e);
		}
	}
}


