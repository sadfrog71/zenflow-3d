package com.deepdimen.matrix.zenflow.admin;
import com.fasterxml.jackson.databind.JsonNode;
import com.deepdimen.matrix.assistant.api.AssistantModelContract.AssistantModelRequest;
import org.springframework.stereotype.Service;
import org.springframework.jdbc.core.JdbcTemplate;
/** Supplier-reported counts only, using the inherited assistant execution accounting fields. */
@Service
public class ZenflowModelUsage {
 private final JdbcTemplate jdbc;
 public ZenflowModelUsage(JdbcTemplate jdbc){this.jdbc=jdbc;}
 static final class Counts {
  Long input,output,plainInput,cacheRead=0L,cacheCreated=0L;boolean finalOutput;
  void accept(JsonNode node,boolean anthropic){
   JsonNode usage=anthropic&&"message_start".equals(node.path("type").asText())?node.path("message").path("usage"):node.path("usage");
   Long in=number(usage.path(anthropic?"input_tokens":"prompt_tokens"));
   Long out=number(usage.path(anthropic?"output_tokens":"completion_tokens"));
   if(in!=null)plainInput=in;
   if(anthropic){Long read=number(usage.path("cache_read_input_tokens")),created=number(usage.path("cache_creation_input_tokens"));if(read!=null)cacheRead=read;if(created!=null)cacheCreated=created;}
   if(plainInput!=null)input=plainInput+(anthropic?cacheRead+cacheCreated:0);
   if(out!=null){output=out;if(!anthropic||"message_delta".equals(node.path("type").asText()))finalOutput=true;}
  }
  private static Long number(JsonNode node){return node.isIntegralNumber()&&node.canConvertToLong()&&node.longValue()>=0&&node.longValue()<=1000000000L?node.longValue():null;}
 }
 void record(AssistantModelRequest request,Counts counts,boolean complete){
  if(!"APP_USER".equals(request.identity().subjectType())||(counts.input==null&&counts.output==null))return;
  long in=counts.input==null?0:counts.input,out=counts.output==null?0:counts.output;
  String source=complete&&counts.input!=null&&counts.finalOutput?"PROVIDER_REPORTED":"PROVIDER_PARTIAL";
  jdbc.update("UPDATE ai_assistant_execution_run SET prompt_tokens=COALESCE(prompt_tokens,0)+?,completion_tokens=COALESCE(completion_tokens,0)+?,total_tokens=COALESCE(total_tokens,0)+?,usage_source=CASE WHEN usage_source='PROVIDER_PARTIAL' OR ?='PROVIDER_PARTIAL' THEN 'PROVIDER_PARTIAL' ELSE ? END WHERE id=? AND tenant_id=? AND session_id IN (SELECT id FROM ai_assistant_session WHERE tenant_id=? AND owner_user_id=? AND owner_subject_type='APP_USER')",in,out,in+out,source,source,request.runId(),request.identity().tenantId(),request.identity().tenantId(),request.identity().userId());
 }
}
