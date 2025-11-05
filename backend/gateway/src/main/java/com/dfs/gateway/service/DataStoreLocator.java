package com.dfs.gateway.service;

import com.dfs.shared.cluster.ClusterConfig;
import org.springframework.stereotype.Component;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

@Component
public class DataStoreLocator {

	private final Path blocksDir;

	public DataStoreLocator() {
		this.blocksDir = resolveBlocksDir();
	}

	public Path getBlocksDir() {
		return blocksDir;
	}

	private Path resolveBlocksDir() {
		// Try to locate cluster.json relative to module dir
		Path[] candidates = new Path[] {
			Paths.get("..", "..", "cluster.json").normalize(),
			Paths.get("..", "cluster.json").normalize(),
			Paths.get("cluster.json").normalize()
		};
		for (Path p : candidates) {
			try {
				if (Files.exists(p)) {
					ClusterConfig cfg = ClusterConfig.load(p);
					if (cfg.datanodes != null && !cfg.datanodes.isEmpty()) {
						String dataDir = cfg.datanodes.get(0).dataDir;
						Path dir = Paths.get(p.getParent() != null ? p.getParent().toString() : ".", dataDir, "blocks").normalize();
						Files.createDirectories(dir);
						return dir;
					}
				}
			} catch (Exception ignored) {}
		}
		// Fallback to local data dir under project
		Path fallback = Paths.get("..", "datanode", "data", "dn1", "blocks").normalize();
		try {
			Files.createDirectories(fallback);
		} catch (Exception ignored) {}
		return fallback;
	}
}


