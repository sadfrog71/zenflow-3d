package com.deepdimen.matrix.zenflow.admin;
import static org.junit.jupiter.api.Assertions.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
class ZenflowModelUsageTest {
 @Test void anthropicCountsAreCumulativeAndIncludeCachedInputWithoutDoubleCounting()throws Exception{
  var c=new ZenflowModelUsage.Counts();var json=new ObjectMapper();
  c.accept(json.readTree("{\"type\":\"message_start\",\"message\":{\"usage\":{\"input_tokens\":10,\"output_tokens\":0,\"cache_read_input_tokens\":20,\"cache_creation_input_tokens\":30}}}"),true);
  assertEquals(60L,c.input);assertFalse(c.finalOutput);
  c.accept(json.readTree("{\"type\":\"message_delta\",\"usage\":{\"output_tokens\":5}}"),true);c.accept(json.readTree("{\"type\":\"message_delta\",\"usage\":{\"input_tokens\":10,\"output_tokens\":8}}"),true);
  assertEquals(8L,c.output);assertTrue(c.finalOutput);assertEquals(60L,c.input);
 }
 @Test void invalidCountsAreUnknownAndOpenAiUsesReportedCountsOnly()throws Exception{
  var c=new ZenflowModelUsage.Counts();var json=new ObjectMapper();c.accept(json.readTree("{\"usage\":{\"prompt_tokens\":-1,\"completion_tokens\":1.5}}"),false);assertNull(c.input);assertNull(c.output);
  c.accept(json.readTree("{\"usage\":{\"prompt_tokens\":100,\"completion_tokens\":25,\"total_tokens\":125}}"),false);assertEquals(100L,c.input);assertEquals(25L,c.output);assertTrue(c.finalOutput);
 }
}
