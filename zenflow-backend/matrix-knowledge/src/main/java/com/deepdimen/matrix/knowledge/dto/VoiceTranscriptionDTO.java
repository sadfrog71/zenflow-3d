package com.deepdimen.matrix.knowledge.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Provider-neutral speech recognition result exposed through the legacy {@code R<String>}
 * response body.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class VoiceTranscriptionDTO {

	private String text;

	private String language;

	private Long durationMs;

	private Double confidence;

	private String provider;

	private String model;

	private String traceId;

}
