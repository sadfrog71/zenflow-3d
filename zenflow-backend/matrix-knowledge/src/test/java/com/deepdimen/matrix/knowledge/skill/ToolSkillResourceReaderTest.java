package com.deepdimen.matrix.knowledge.skill;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import java.util.List;
import org.junit.jupiter.api.Test;
import com.deepdimen.matrix.skill.api.SkillContract.ResourceRef;

class ToolSkillResourceReaderTest {

	final WritingSkillTemplateReader writing = mock(WritingSkillTemplateReader.class);

	final OcrSkillConfigurationReader ocr = mock(OcrSkillConfigurationReader.class);

	final ReportSkillConfigurationReader reports = mock(ReportSkillConfigurationReader.class);

	final ToolSkillResourceReader reader = new ToolSkillResourceReader(writing, ocr, reports);

	final String digest = "a".repeat(64);

	@Test
	void publishedReferencesContainOnlyOriginalIdsAndDigestsAndResolveAuthorizedContent() {
		var template = new WritingSkillTemplateReader.Snapshot("7", "8", "private template", digest);
		var fields = new OcrSkillConfigurationReader.Snapshot("7", "9", "private rule", List.of(), digest);
		var report = new ReportSkillConfigurationReader.Capture(new ReportSkillConfigurationReader.Snapshot("7", "10",
				"title", "private rule", "private fields", "bucket/file", "b".repeat(64), digest), new byte[] { 1, 2 });
		when(writing.capture(8L)).thenReturn(template);
		when(ocr.capture(9L)).thenReturn(fields);
		when(reports.capture(10L)).thenReturn(report);
		var writingRef = reader.captureWriting(8L);
		assertEquals(new ResourceRef("writing-template:8", digest, digest), writingRef);
		assertSame(template, reader.requireWriting(writingRef));
		assertSame(fields, reader.requireOcr(reader.captureOcr(9L)));
		assertSame(report, reader.requireReport(reader.captureReport(10L)));
		assertFalse(writingRef.toString().contains("private"));
	}

	@Test
 void resourceChangesAndRevokedPermissionsNeverReturnContent() {
  when(writing.capture(8L)).thenReturn(new WritingSkillTemplateReader.Snapshot("7", "8", "changed", "b".repeat(64)));
  var ref = new ResourceRef("writing-template:8", digest, digest);
  assertEquals("EXECUTOR_VERSION_DRIFT", assertThrows(IllegalArgumentException.class, () -> reader.requireWriting(ref)).getMessage());
  when(writing.capture(8L)).thenThrow(new IllegalArgumentException("NOT_AUTHORIZED"));
  assertEquals("NOT_AUTHORIZED", assertThrows(IllegalArgumentException.class, () -> reader.requireWriting(ref)).getMessage());
 }

	@Test
	void wrongTypeVersionAndOutOfRangeIdsAreRejectedBeforeLookup() {
		for (var ref : List.of(new ResourceRef("ocr-configuration:8", digest, digest),
				new ResourceRef("writing-template:8", "latest", digest),
				new ResourceRef("writing-template:9999999999999999999", digest, digest))) {
			assertThrows(IllegalArgumentException.class, () -> reader.requireWriting(ref));
		}
		assertThrows(IllegalArgumentException.class, () -> reader.requireOcr(null));
		verifyZeroInteractions(writing, ocr, reports);
	}

}
