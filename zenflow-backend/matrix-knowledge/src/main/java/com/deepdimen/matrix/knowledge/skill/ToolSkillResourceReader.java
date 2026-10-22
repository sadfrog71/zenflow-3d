package com.deepdimen.matrix.knowledge.skill;

import org.springframework.stereotype.Component;
import com.deepdimen.matrix.skill.api.SkillContract.ResourceRef;

/** Adapts existing business resources to the shared fixed-release reference contract. */
@Component
public class ToolSkillResourceReader {

	private final WritingSkillTemplateReader writing;

	private final OcrSkillConfigurationReader ocr;

	private final ReportSkillConfigurationReader reports;

	public ToolSkillResourceReader(WritingSkillTemplateReader writing, OcrSkillConfigurationReader ocr,
			ReportSkillConfigurationReader reports) {
		this.writing = writing;
		this.ocr = ocr;
		this.reports = reports;
	}

	public ResourceRef captureWriting(Long id) {
		var current = writing.capture(id);
		return reference("writing-template", current.templateId(), current.digest());
	}

	public ResourceRef captureOcr(Long id) {
		var current = ocr.capture(id);
		return reference("ocr-configuration", current.configurationId(), current.digest());
	}

	public ResourceRef captureReport(Long id) {
		var current = reports.capture(id).snapshot();
		return reference("report-configuration", current.configurationId(), current.digest());
	}

	public WritingSkillTemplateReader.Snapshot requireWriting(ResourceRef expected) {
		var current = writing.capture(id(expected, "writing-template"));
		requireDigest(expected, current.digest());
		return current;
	}

	public OcrSkillConfigurationReader.Snapshot requireOcr(ResourceRef expected) {
		var current = ocr.capture(id(expected, "ocr-configuration"));
		requireDigest(expected, current.digest());
		return current;
	}

	public ReportSkillConfigurationReader.Capture requireReport(ResourceRef expected) {
		var current = reports.capture(id(expected, "report-configuration"));
		requireDigest(expected, current.snapshot().digest());
		return current;
	}

	private static ResourceRef reference(String type, String id, String digest) {
		return new ResourceRef(type + ":" + id, digest, digest);
	}

	private static long id(ResourceRef ref, String type) {
		if (ref == null || ref.resourceId() == null || ref.digest() == null || !ref.digest().matches("[a-f0-9]{64}")
				|| !ref.digest().equals(ref.version()) || !ref.resourceId().matches(type + ":[1-9][0-9]{0,18}"))
			throw new IllegalArgumentException("TOOL_RESOURCE_REFERENCE_INVALID");
		try {
			return Long.parseLong(ref.resourceId().substring(type.length() + 1));
		}
		catch (NumberFormatException failure) {
			throw new IllegalArgumentException("TOOL_RESOURCE_REFERENCE_INVALID", failure);
		}
	}

	private static void requireDigest(ResourceRef expected, String actual) {
		if (!expected.digest().equals(actual))
			throw new IllegalArgumentException("EXECUTOR_VERSION_DRIFT");
	}

}
