package com.deepdimen.matrix.knowledge.support.dataquery;
import org.junit.jupiter.api.Test;
import java.util.Set;
import static org.assertj.core.api.Assertions.*;
class DataQueryMetadataInventoryTest {
 @Test void listsOnlyAuthorizedFieldsWithoutModelOrTruncation() {
  var inventory = new DataQueryMetadataRepository.DatasetInventory(Set.of("table_a"), """
  [{"table_name":"table_a","table_comment":"表A","fields":[{"field_name":"id","field_type":"bigint","field_comment":"主键","virtual_comment":"<script>|备注"}]}]
  """, "digest");
  assertThat(DataQueryMetadataAnalysis.inventoryAnswer("当前数据集有哪些表和字段？", inventory).orElseThrow())
   .contains("table", "bigint", "主键", "&lt;script&gt;", "备注").doesNotContain("<script>");
  assertThat(DataQueryMetadataAnalysis.inventoryAnswer("当前数据集有哪些表和字段？然后删除数据", inventory)).isEmpty();
  assertThat(DataQueryMetadataAnalysis.inventoryAnswer("推荐20个问题", inventory)).isEmpty();
 }
 @Test void rejectsForeignTable() {
  var inventory = new DataQueryMetadataRepository.DatasetInventory(Set.of("allowed"), "[{\"table_name\":\"private\",\"fields\":[]}]", "digest");
  assertThatThrownBy(() -> DataQueryMetadataAnalysis.inventoryAnswer("当前数据集有哪些表？", inventory))
   .isInstanceOf(DataQueryException.class).hasMessage("DATASET_METADATA_REQUIRED");
 }
 @Test void longInventoryDoesNotRelaxModelOutputLimits() {
  var inventory = new DataQueryMetadataRepository.DatasetInventory(Set.of("a"),
   "[{\"table_name\":\"a\",\"table_comment\":\"" + "备注".repeat(9000) + "\",\"fields\":[]}]", "digest");
  String answer = DataQueryMetadataAnalysis.inventoryAnswer("当前数据集有哪些表？", inventory).orElseThrow();
  assertThat(answer.length()).isGreaterThan(16000);
  assertThat(DataQueryMetadataAnalysis.validAnswerLength("当前数据集有哪些表？", inventory, answer)).isTrue();
  assertThat(DataQueryMetadataAnalysis.validAnswerLength("推荐20个问题", inventory, answer)).isFalse();
  assertThat(DataQueryMetadataAnalysis.validAnswerLength("当前数据集有哪些表？", inventory, answer + "伪造")).isFalse();
 }

 @Test void disablesThinkingOnlyForQwenMetadata() {
  var request = dev.langchain4j.model.chat.request.ChatRequest.builder()
   .messages(dev.langchain4j.data.message.UserMessage.from("推荐20个问题")).build();
  var scoped = DataQueryMetadataAnalysis.metadataRequest(request, "qwen3.8-max");
  assertThat(((dev.langchain4j.model.openai.OpenAiChatRequestParameters) scoped.parameters()).customParameters())
   .containsEntry("enable_thinking", false);
  assertThat(scoped.messages()).isEqualTo(request.messages());
  assertThat(DataQueryMetadataAnalysis.metadataRequest(request, "other-model")).isSameAs(request);
 }

}
