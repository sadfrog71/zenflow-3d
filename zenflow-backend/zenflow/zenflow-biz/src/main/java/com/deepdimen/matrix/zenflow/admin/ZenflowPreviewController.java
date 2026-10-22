package com.deepdimen.matrix.zenflow.admin;

import com.deepdimen.matrix.zenflow.companion.ZenflowPreviewService;
import com.deepdimen.matrix.assistant.run.*;
import java.util.Map;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/zenflow-admin/v1/companion/previews")
public class ZenflowPreviewController {

	private final ZenflowPreviewService previews;

	private final AssistantRunEventStreamService streams;

	public ZenflowPreviewController(ZenflowPreviewService previews, AssistantRunEventService events,
			AssistantRunEventBus bus, AssistantRunRepository runs) {
		this.previews = previews;
		this.streams = new AssistantRunEventStreamService(events, bus, runs);
	}

	@PostMapping
	@ResponseStatus(HttpStatus.ACCEPTED)
	public Map<String, Object> create(@RequestBody Map<String, Object> body) {
		return previews.create(body);
	}

	@GetMapping("/{id}")
	public Map<String, Object> detail(@PathVariable String id) {
		return previews.detail(id);
	}

	@PostMapping("/{id}/cancel")
	public Object cancel(@PathVariable String id) {
		return previews.cancel(id);
	}

	@GetMapping(value = "/{id}/events", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
	public SseEmitter events(@PathVariable String id, @RequestParam(defaultValue = "0") long afterSeq,
			@RequestHeader(value = "Last-Event-ID", required = false) String last) {
		long resume = afterSeq;
		try {
			if (last != null)
				resume = Math.max(resume, Long.parseLong(last));
		}
		catch (NumberFormatException failure) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "INVALID_EVENT_SEQUENCE");
		}
		if (afterSeq < 0 || resume < 0)
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "INVALID_EVENT_SEQUENCE");
		var observation = previews.observe(id);
		return streams.stream(observation.runId(), resume, observation.guard());
	}

}
