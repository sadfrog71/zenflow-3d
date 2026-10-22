package com.deepdimen.matrix.knowledge.skill;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Map;
import org.springframework.ai.chat.prompt.PromptTemplate;
import org.springframework.core.io.ClassPathResource;
import com.deepdimen.matrix.knowledge.support.constant.ActionPrompts;

/**
 * Captures only the writing consumer's existing source; never rewrites shared templates.
 */
public final class WritingSkillInstructions {

	private WritingSkillInstructions() {
	}

	public record Source(String action, String template, String instruction, String sha256) {
		/**
		 * Business data cannot override action, instructions, or the captured template.
		 */
		public String render(String text, String systemTime) {
			if (text == null || systemTime == null)
				throw new IllegalArgumentException("WRITING_INPUT_INVALID");
			return new PromptTemplate(template)
				.render(Map.of("action", action, "instruction", instruction, "text", text, "systemTime", systemTime));
		}
	}

	public static Source capture(String action) {
		if (action == null)
			throw new IllegalArgumentException("WRITING_ACTION_UNSUPPORTED");
		String instruction = ActionPrompts.ACTION_PROMPTS.get(action);
		if (instruction == null || instruction.isBlank())
			throw new IllegalArgumentException("WRITING_ACTION_UNSUPPORTED");
		try {
			String template = new ClassPathResource("prompts/ai-writer-text-ops.st")
				.getContentAsString(StandardCharsets.UTF_8);
			String material = "writing-text-v1\n" + frame(action) + frame(template) + frame(instruction);
			String digest = HexFormat.of()
				.formatHex(MessageDigest.getInstance("SHA-256").digest(material.getBytes(StandardCharsets.UTF_8)));
			return new Source(action, template, instruction, digest);
		}
		catch (IOException | NoSuchAlgorithmException failure) {
			throw new IllegalStateException("WRITING_SOURCE_UNAVAILABLE", failure);
		}
	}

	private static String frame(String value) {
		return value.getBytes(StandardCharsets.UTF_8).length + ":" + value;
	}

}
