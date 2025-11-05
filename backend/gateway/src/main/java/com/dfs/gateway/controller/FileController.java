package com.dfs.gateway.controller;

import com.dfs.gateway.service.DataStoreLocator;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/")
public class FileController {

	private final DataStoreLocator locator;

	public FileController(DataStoreLocator locator) {
		this.locator = locator;
	}

	@GetMapping("files")
	public Map<String, Object> listFiles() throws IOException {
		Path dir = locator.getBlocksDir();
		List<Map<String, Object>> files = Files.list(dir)
			.filter(Files::isRegularFile)
			.map(p -> Map.<String, Object>of(
				"name", p.getFileName().toString(),
				"size", sizeOf(p),
				"modified", modifiedOf(p)
			))
			.collect(Collectors.toList());
		return Map.of("path", "/", "folders", List.of(), "files", files, "canWrite", true);
	}

	@GetMapping("download")
	public ResponseEntity<byte[]> download(@RequestParam("name") String name) throws IOException {
		Path file = locator.getBlocksDir().resolve(name).normalize();
		if (!Files.exists(file) || !file.startsWith(locator.getBlocksDir())) {
			return ResponseEntity.status(HttpStatus.NOT_FOUND).build();
		}
		byte[] data = Files.readAllBytes(file);
		HttpHeaders headers = new HttpHeaders();
		headers.set(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=" + name);
		headers.setContentType(MediaType.APPLICATION_OCTET_STREAM);
		return new ResponseEntity<>(data, headers, HttpStatus.OK);
	}

	private static long sizeOf(Path p) {
		try { return Files.size(p); } catch (IOException e) { return 0L; }
	}

	private static String modifiedOf(Path p) {
		try { return Files.getLastModifiedTime(p).toInstant().toString(); } catch (IOException e) { return ""; }
	}
}


