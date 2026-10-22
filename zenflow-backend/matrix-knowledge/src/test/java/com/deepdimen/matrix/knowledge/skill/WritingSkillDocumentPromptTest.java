package com.deepdimen.matrix.knowledge.skill;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import java.util.Map;
import org.junit.jupiter.api.Test;
import com.deepdimen.matrix.skill.api.SkillContract.ResourceRef;
import com.deepdimen.matrix.knowledge.support.util.PromptBuilder;

class WritingSkillDocumentPromptTest {

	final ToolSkillResourceReader resources = mock(ToolSkillResourceReader.class);

	final WritingSkillDocumentPrompt prompt = new WritingSkillDocumentPrompt(resources);

	final ResourceRef ref = new ResourceRef("writing-template:8", "a".repeat(64), "a".repeat(64));

	@Test
 void preservesOriginalDocumentPromptWithAuthorizedPinnedTemplate() {
  when(resources.captureWriting(8L)).thenReturn(ref);
  when(resources.requireWriting(ref)).thenReturn(new WritingSkillTemplateReader.Snapshot("7", "8", "<p>模板</p>", ref.digest()));
  var pinned = prompt.capture(8L);
  var rendered = prompt.render(pinned, Map.of("title", "标题", "abstractContent", "资料 {doc_template}"), "2026-09-27");
  assertEquals(PromptBuilder.render("ai-writter-document.st", Map.of("title", "标题", "abstractContent", "资料 {doc_template}", "doc_template", "<p>模板</p>", "systemTime", "2026-09-27")), rendered);
  verify(resources).requireWriting(ref);
 }

	@Test
 void rejectsChangedInstructionsAndBusinessOverridesBeforeReadingTemplate() {
  when(resources.captureWriting(8L)).thenReturn(ref);
  var pinned = prompt.capture(8L);
  var changed = new WritingSkillDocumentPrompt.Pinned(new ResourceRef("writing-document-instructions", "b".repeat(64), "b".repeat(64)), ref);
  assertThrows(IllegalArgumentException.class, () -> prompt.render(changed, Map.of("title", "标题", "abstractContent", "资料"), "now"));
  for (String key : new String[] { "doc_template", "systemTime", "executorRef", "instruction" }) {
   assertThrows(IllegalArgumentException.class, () -> prompt.render(pinned, Map.of("title", "标题", "abstractContent", "资料", key, "override"), "now"));
  }
  verify(resources, never()).requireWriting(any());
 }

	@Test
 void doesNotRenderWhenOriginalResourceIsChangedOrNoLongerAuthorized() {
  when(resources.captureWriting(8L)).thenReturn(ref);
  var pinned = prompt.capture(8L);
  when(resources.requireWriting(ref)).thenThrow(new IllegalArgumentException("EXECUTOR_VERSION_DRIFT"));
  assertThrows(IllegalArgumentException.class, () -> prompt.render(pinned, Map.of("title", "标题", "abstractContent", "资料"), "now"));
 }

}
