package com.deepdimen.matrix.zenflow.admin;

import java.util.List;
import java.util.Map;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/zenflow-admin/v1/embedding-models")
public class ZenflowEmbeddingController {

	private final ZenflowAdminAccess access;

	private final ZenflowEmbeddingModels models;

	private final ZenflowEmbeddingGateway gateway;

	public ZenflowEmbeddingController(ZenflowAdminAccess access, ZenflowEmbeddingModels models,
			ZenflowEmbeddingGateway gateway) {
		this.access = access;
		this.models = models;
		this.gateway = gateway;
	}

	@GetMapping
	public List<Map<String, Object>> list() {
		access.require();
		return models.list();
	}

	@PostMapping
	public Map<String, Object> create(@RequestBody Map<String, Object> body) {
		access.require();
		return models.save(null, body);
	}

	@PutMapping("/{id}")
	public Map<String, Object> update(@PathVariable String id, @RequestBody Map<String, Object> body) {
		access.require();
		return models.save(id, body);
	}

	@PostMapping("/{id}/test")
	public Map<String, Object> test(@PathVariable String id, @RequestBody Map<String, Object> body) {
		access.require();
		gateway.probe(id, version(body));
		return Map.of("tested", true, "modelId", id);
	}

	@PostMapping("/{id}/activate")
	public Map<String, Object> activate(@PathVariable String id, @RequestBody Map<String, Object> body) {
		access.require();
		return models.activate(id, version(body));
	}

	private static int version(Map<String, Object> body) {
		if (body == null || !body.keySet().equals(java.util.Set.of("expectedVersion"))
				|| !(body.get("expectedVersion") instanceof Number value) || !Double.isFinite(value.doubleValue())
				|| value.doubleValue() != value.intValue() || value.intValue() < 1)
			throw new org.springframework.web.server.ResponseStatusException(
					org.springframework.http.HttpStatus.BAD_REQUEST, "EXPECTED_VERSION_REQUIRED");
		return ((Number) body.get("expectedVersion")).intValue();
	}

}
