package com.deepdimen.matrix.knowledge.skill;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Map;
import java.util.Set;
import org.springframework.ai.chat.prompt.PromptTemplate;
import org.springframework.core.io.ClassPathResource;

/** Explicit non-routing consumer sources; does not intercept PromptBuilder or Flow. */
public final class ToolSkillPromptSources {

	private ToolSkillPromptSources() {
	}

	public enum Slot {

		WRITING_DOCUMENT("ai-writter-document.st", Set.of("systemTime", "title", "doc_template", "abstractContent")),

		REPORT_POLISH("report-polish.st", Set.of("reportTitle", "reportPrompt", "prompt", "username", "systemTime")),
		REPORT_SYSTEM("report-system-json.st", Set.of("systemTime")),
		REPORT_INPUT("report-user-json.st", Set.of("userInput", "jsonSchema")),
		OCR_IMAGE("ocr-image.st", Set.of("systemTime")),
		OCR_IMAGE_SCHEMA("ocr-image-user-json.st", Set.of("jsonSchema")),
		OCR_SYSTEM("ocr-system-json.st", Set.of("systemTime")),
		OCR_INPUT("ocr-user-json.st", Set.of("userInput", "jsonSchema"));

		private final String file;

		private final Set<String> parameters;

		Slot(String file, Set<String> parameters) {
			this.file = file;
			this.parameters = parameters;
		}

		public String file() {
			return file;
		}

	}

	public record Source(Slot slot, String template, String sha256) {
		/** Parameters must already come from authorized, fixed business resources. */
		public String render(Map<String, Object> parameters) {
			if (parameters == null || !slot.parameters.containsAll(parameters.keySet()))
				throw new IllegalArgumentException("SKILL_PARAMETER_NOT_ALLOWED");
			return new PromptTemplate(template).render(Map.copyOf(parameters));
		}
	}

	public static Source capture(Slot slot) {
		if (slot == null)
			throw new IllegalArgumentException("TOOL_SKILL_SLOT_REQUIRED");
		try {
			String template = new ClassPathResource("prompts/" + slot.file).getContentAsString(StandardCharsets.UTF_8);
			String material = "tool-prompt-v1\n" + slot.name() + "\n" + template;
			return new Source(slot, template, HexFormat.of()
				.formatHex(MessageDigest.getInstance("SHA-256").digest(material.getBytes(StandardCharsets.UTF_8))));
		}
		catch (IOException | NoSuchAlgorithmException failure) {
			throw new IllegalStateException("TOOL_SKILL_SOURCE_UNAVAILABLE", failure);
		}
	}

}
