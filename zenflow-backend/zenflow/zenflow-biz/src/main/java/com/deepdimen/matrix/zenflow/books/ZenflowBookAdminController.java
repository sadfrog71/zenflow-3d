package com.deepdimen.matrix.zenflow.books;

import java.util.List;
import java.util.Map;
import java.nio.charset.StandardCharsets;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/zenflow-admin/v1/books")
public class ZenflowBookAdminController {
	private final ZenflowBookAdmin books;

	public ZenflowBookAdminController(ZenflowBookAdmin books) { this.books = books; }

	@GetMapping public List<Map<String, Object>> list() { return books.list(); }
	@PostMapping("/import") public Map<String, Object> importBook(@RequestBody Map<String, Object> body) { return books.importBook(body); }
	@GetMapping("/{id}") public Map<String, Object> detail(@PathVariable String id) { return books.detail(id); }
	@PutMapping("/{id}") public Map<String, Object> update(@PathVariable String id, @RequestBody Map<String, Object> body) { return books.updateMetadata(id, body); }
	@PutMapping("/{id}/chapters") public Map<String, Object> chapters(@PathVariable String id, @RequestBody Map<String, Object> body) { return books.saveSourceChapters(id, body); }
	@PostMapping("/{id}/source-review") public Map<String, Object> reviewSource(@PathVariable String id, @RequestBody Map<String, Object> body) { return books.reviewSource(id, body); }
	@GetMapping("/{id}/translations") public List<Map<String, Object>> translations(@PathVariable String id) { return books.translations(id); }
	@PostMapping("/{id}/translations") public Map<String, Object> translate(@PathVariable String id, @RequestBody Map<String, Object> body) { return books.createTranslation(id, body); }
	@GetMapping("/{id}/translations/{translation}") public Map<String, Object> translation(@PathVariable String id, @PathVariable String translation) { return books.translationDetail(id, translation); }
	@PostMapping("/{id}/translations/import") public Map<String, Object> importTranslation(@PathVariable String id, @RequestBody Map<String, Object> body) { return books.importTranslation(id, body); }
	@GetMapping("/{id}/translations/{translation}/asset") public ResponseEntity<byte[]> translationAsset(@PathVariable String id, @PathVariable String translation) {
		var file = books.translationAsset(id, translation);
		return ResponseEntity.ok().contentType(MediaType.APPLICATION_OCTET_STREAM)
				.header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment().filename(file.filename(), StandardCharsets.UTF_8).build().toString())
				.body(file.content());
	}
	@PostMapping("/{id}/translations/{translation}/retry") public Map<String, Object> retry(@PathVariable String id, @PathVariable String translation) { return books.retryTranslation(id, translation); }
	@PutMapping("/{id}/translations/{translation}/chapters/{chapter}") public Map<String, Object> editTranslation(@PathVariable String id, @PathVariable String translation, @PathVariable String chapter, @RequestBody Map<String, Object> body) { return books.updateTranslationChapter(id, translation, chapter, body); }
	@PostMapping("/{id}/translations/{translation}/qa") public Map<String, Object> qualityCheck(@PathVariable String id, @PathVariable String translation) { return books.runQualityCheck(id, translation); }
	@PostMapping("/{id}/translations/{translation}/chapters/{chapter}/review") public Map<String, Object> reviewChapter(@PathVariable String id, @PathVariable String translation, @PathVariable String chapter, @RequestBody Map<String, Object> body) { return books.reviewTranslationChapter(id, translation, chapter, body); }
	@PostMapping("/{id}/translations/{translation}/publish") public Map<String, Object> publish(@PathVariable String id, @PathVariable String translation, @RequestBody Map<String, Object> body) { return books.publish(id, translation, body); }
	@PostMapping("/{id}/translations/{translation}/unpublish") public Map<String, Object> unpublish(@PathVariable String id, @PathVariable String translation, @RequestBody Map<String, Object> body) { return books.unpublish(id, translation, body); }
	@PostMapping("/{id}/revoke") public Map<String, Object> revoke(@PathVariable String id, @RequestBody Map<String, Object> body) { return books.revoke(id, body); }
}
