package com.deepdimen.matrix.zenflow.speech;

import java.util.List;

import com.deepdimen.matrix.common.core.util.R;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/zenflow/v1/voice")
public class ZenflowVoiceCorrectionController {

	private final ZenflowVoiceCorrectionService corrections;

	public ZenflowVoiceCorrectionController(ZenflowVoiceCorrectionService corrections) {
		this.corrections = corrections;
	}

	@PostMapping("/corrections")
	public R<CorrectionResponse> correct(@RequestBody CorrectionRequest request) {
		return R.ok(corrections.correct(request));
	}

	public record CorrectionRequest(String sessionId, int sentenceId, int revision, String rawText,
			List<String> previousSentences) {
	}

	public record CorrectionResponse(String sessionId, int sentenceId, int revision, String baseTextHash,
			String status, String text) {
	}
}
