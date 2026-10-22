package com.deepdimen.matrix.zenflow.books;

import java.util.Map;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/zenflow/v1/books")
public class ZenflowBookReaderController {
	private final ZenflowBookReader books;

	public ZenflowBookReaderController(ZenflowBookReader books) { this.books = books; }

	@GetMapping public Map<String, Object> catalog() { return books.catalog(); }
	@GetMapping("/{id}/versions/{version}") public Map<String, Object> version(@PathVariable String id,
			@PathVariable String version, @RequestParam String language, @RequestParam(required = false) Integer chapterNo) { return books.publishedVersion(id, version, language, chapterNo); }
	@GetMapping("/state") public Map<String, Object> state() { return books.state(); }
	@PostMapping("/state") public Map<String, Object> syncState(@RequestBody Map<String, Object> body) { return books.syncState(body); }
	@DeleteMapping("/state") public Map<String, Object> clearState() { return books.clearState(); }
}
