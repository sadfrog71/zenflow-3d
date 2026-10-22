package com.deepdimen.matrix.zenflow.admin;

import java.util.Map;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/zenflow-admin/v1/users")
public class ZenflowUserController {

	private final ZenflowUsers users;

	public ZenflowUserController(ZenflowUsers users) {
		this.users = users;
	}

	@GetMapping
	public Map<String, Object> list(@RequestParam(required = false) String query,
			@RequestParam(required = false) String state, @RequestParam(required = false) String before,
			@RequestParam(defaultValue = "25") int limit) {
		return users.list(query, state, before, limit);
	}

	@PostMapping("/{id}/state")
	public Map<String, Object> state(@PathVariable String id, @RequestBody Map<String, Object> body) {
		return users.state(id, body);
	}

}
