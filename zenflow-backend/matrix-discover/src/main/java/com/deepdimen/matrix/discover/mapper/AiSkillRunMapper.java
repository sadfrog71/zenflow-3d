package com.deepdimen.matrix.discover.mapper;

import com.deepdimen.matrix.discover.entity.AiSkillRunEntity;
import org.apache.ibatis.annotations.*;

@Mapper
public interface AiSkillRunMapper {

	@Insert("""
			INSERT INTO ai_skill_run
			  (id,tenant_id,binding_revision_id,release_id,actor_id,subject_id,actor_type,subject_type,consumer_type,consumer_id,
			   slot_code,binding_key,parent_run_id,business_run_id,correlation_id,idempotency_key,
			   idempotency_scope_digest,input_digest,request_digest,status,revision,created_at)
			VALUES (#{r.id},#{r.tenantId},#{r.bindingRevisionId},#{r.releaseId},#{r.actorId},#{r.subjectId},#{r.actorType},#{r.subjectType},
			        #{r.consumerType},#{r.consumerId},#{r.slotCode},#{r.bindingKey},#{r.parentRunId},
			        #{r.businessRunId},#{r.correlationId},#{r.idempotencyKey},#{r.idempotencyScopeDigest},
			        #{r.inputDigest},#{r.requestDigest},'QUEUED',0,CURRENT_TIMESTAMP(3))
			ON DUPLICATE KEY UPDATE id = ai_skill_run.id
			""")
	int reserve(@Param("r") AiSkillRunEntity run);

	@Select("SELECT * FROM ai_skill_run WHERE tenant_id=#{tenant} AND idempotency_scope_digest=#{scope}")
	AiSkillRunEntity findByScope(@Param("tenant") String tenant, @Param("scope") String scope);

	@Select("SELECT * FROM ai_skill_run WHERE tenant_id=#{tenant} AND actor_id=#{actor} AND subject_id=#{actor} AND consumer_type=#{type} AND consumer_id=#{consumer} AND slot_code=#{slot} AND binding_key=#{binding} AND business_run_id=#{business} ORDER BY id DESC LIMIT 2")
	java.util.List<AiSkillRunEntity> findBusiness(@Param("tenant") String tenant, @Param("actor") String actor,
			@Param("type") String type, @Param("consumer") String consumer, @Param("slot") String slot,
			@Param("binding") String binding, @Param("business") String business);

	@Update("""
			UPDATE ai_skill_run SET status='RUNNING',revision=revision+1,started_at=CURRENT_TIMESTAMP(3)
			WHERE tenant_id=#{tenant} AND id=#{id} AND status='QUEUED' AND revision=#{revision}
			""")
	int claim(@Param("tenant") String tenant, @Param("id") Long id, @Param("revision") Long revision);

	@Update("""
			UPDATE ai_skill_run SET status=#{status},result_ref=#{resultRef},error_code=#{errorCode},
			    executor_version=#{executorVersion},usage_json=#{usage},revision=revision+1,finished_at=CURRENT_TIMESTAMP(3)
			WHERE tenant_id=#{tenant} AND id=#{id} AND status='RUNNING' AND revision=#{revision}
			""")
	int finish(@Param("tenant") String tenant, @Param("id") Long id, @Param("revision") Long revision,
			@Param("status") String status, @Param("resultRef") String resultRef, @Param("errorCode") String errorCode,
			@Param("executorVersion") String executorVersion, @Param("usage") String usage);

}
