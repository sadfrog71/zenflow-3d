package com.deepdimen.matrix.discover.mapper;

import com.deepdimen.matrix.discover.entity.MarketSkillRequestEntity;
import org.apache.ibatis.annotations.*;

@Mapper
public interface MarketSkillRequestMapper {

	@Insert("""
			INSERT INTO ai_market_skill_request(id,tenant_id,actor_id,agent_id,binding_key,client_request_id,input_digest,input_snapshot)
			VALUES(#{r.id},#{r.tenantId},#{r.actorId},#{r.agentId},#{r.bindingKey},#{r.clientRequestId},#{r.inputDigest},#{r.inputSnapshot})
			ON DUPLICATE KEY UPDATE id=ai_market_skill_request.id
			""")
	int reserve(@Param("r") MarketSkillRequestEntity row);

	@Select("SELECT * FROM ai_market_skill_request WHERE tenant_id=#{tenant} AND actor_id=#{actor} AND client_request_id=#{key}")
	MarketSkillRequestEntity findRequest(@Param("tenant") Long tenant, @Param("actor") Long actor,
			@Param("key") String key);

	@Select("SELECT * FROM ai_market_skill_request WHERE tenant_id=#{tenant} AND actor_id=#{actor} AND id=#{id}")
	MarketSkillRequestEntity findOwned(@Param("tenant") Long tenant, @Param("actor") Long actor, @Param("id") Long id);

	@Update("""
			UPDATE ai_market_skill_request SET skill_run_id=#{run},skill_output_json=#{output}
			WHERE tenant_id=#{tenant} AND actor_id=#{actor} AND id=#{id} AND input_digest=#{digest}
			AND skill_run_id IS NULL AND skill_output_json IS NULL
			""")
	int saveOnce(@Param("tenant") Long tenant, @Param("actor") Long actor, @Param("id") Long id,
			@Param("digest") String digest, @Param("run") Long run, @Param("output") String output);

	@Select("""
			SELECT q.input_snapshot, q.input_digest, q.binding_key,
			       CAST(r.release_id AS CHAR) AS release_id, r.executor_version
			FROM ai_market_skill_request q JOIN ai_skill_run r ON r.id=q.skill_run_id
			  AND r.tenant_id=CAST(q.tenant_id AS CHAR) AND r.actor_id=CAST(q.actor_id AS CHAR)
			  AND r.subject_id=r.actor_id AND r.consumer_type='MARKET_AGENT'
			  AND r.consumer_id=CAST(q.agent_id AS CHAR) AND r.slot_code='CHAT'
			  AND r.binding_key=q.binding_key AND r.business_run_id=CAST(q.id AS CHAR)
			WHERE q.tenant_id=#{tenant} AND q.actor_id=#{actor} AND q.agent_id=#{agent}
			  AND r.status='SUCCEEDED'
			  AND JSON_UNQUOTE(JSON_EXTRACT(q.skill_output_json,'$.conversationId'))=#{conversation}
			  AND COALESCE(JSON_UNQUOTE(JSON_EXTRACT(q.input_snapshot,'$.conversation_id')),'')=''
			LIMIT 2
			""")
	java.util.List<ConversationOrigin> findConversationOrigin(@Param("tenant") Long tenant, @Param("actor") Long actor,
			@Param("agent") Long agent, @Param("conversation") String conversation);

	@Select("""
			SELECT EXISTS(SELECT 1 FROM ai_market_skill_request
			  WHERE tenant_id=#{tenant} AND actor_id=#{actor} AND agent_id=#{agent}
			    AND (observed_conversation_id=#{conversation}
			      OR JSON_UNQUOTE(JSON_EXTRACT(input_snapshot,'$.conversation_id'))=#{conversation}
			      OR JSON_UNQUOTE(JSON_EXTRACT(skill_output_json,'$.conversationId'))=#{conversation}))
			""")
	boolean hasManagedConversation(@Param("tenant") Long tenant, @Param("actor") Long actor, @Param("agent") Long agent,
			@Param("conversation") String conversation);

	@Update("""
			UPDATE ai_market_skill_request SET observed_conversation_id=#{conversation}
			WHERE id=#{id} AND tenant_id=#{tenant} AND actor_id=#{actor} AND agent_id=#{agent}
			  AND input_digest=#{digest}
			  AND (observed_conversation_id IS NULL OR observed_conversation_id=#{conversation})
			""")
	int observeConversation(@Param("id") Long id, @Param("tenant") Long tenant, @Param("actor") Long actor,
			@Param("agent") Long agent, @Param("digest") String digest, @Param("conversation") String conversation);

	@lombok.Data
	class ConversationOrigin {

		private String inputSnapshot;

		private String inputDigest;

		private String bindingKey;

		private String releaseId;

		private String executorVersion;

	}

}
