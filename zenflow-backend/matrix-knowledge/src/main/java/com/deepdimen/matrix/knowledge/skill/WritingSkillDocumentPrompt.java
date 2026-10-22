package com.deepdimen.matrix.knowledge.skill;

import java.util.Map;
import java.util.Set;
import org.springframework.stereotype.Component;
import com.deepdimen.matrix.skill.api.SkillContract.ResourceRef;

/** Prepares the original document prompt from two independently pinned resources. */
@Component
public class WritingSkillDocumentPrompt {

	private final ToolSkillResourceReader resources;

	public WritingSkillDocumentPrompt(ToolSkillResourceReader resources) {
		this.resources = resources;
	}

	public record Pinned(ResourceRef instructions, ResourceRef template) {
	}

	public Pinned capture(Long templateId) {
		var source = source();
		return new Pinned(new ResourceRef("writing-document-instructions", source.sha256(), source.sha256()),
				resources.captureWriting(templateId));
	}

	public String render(Pinned pinned, Map<String, String> input, String trustedSystemTime) {
		var source = source();
		var expected = new ResourceRef("writing-document-instructions", source.sha256(), source.sha256());
		if (pinned == null || !expected.equals(pinned.instructions()))
			throw new IllegalArgumentException("EXECUTOR_VERSION_DRIFT");
		if (input == null || !input.keySet().equals(Set.of("title", "abstractContent")) || input.get("title") == null
				|| input.get("abstractContent") == null || trustedSystemTime == null)
			throw new IllegalArgumentException("WRITING_INPUT_INVALID");
		var template = resources.requireWriting(pinned.template());
		return source.render(Map.of("title", input.get("title"), "abstractContent", input.get("abstractContent"),
				"doc_template", template.content(), "systemTime", trustedSystemTime));
	}

	private static ToolSkillPromptSources.Source source() {
		return ToolSkillPromptSources.capture(ToolSkillPromptSources.Slot.WRITING_DOCUMENT);
	}

}
