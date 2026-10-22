package com.deepdimen.matrix.work.brief;

import com.deepdimen.matrix.admin.api.service.TenantUserDirectory;
import com.deepdimen.matrix.common.data.tenant.TenantContextHolder;
import com.deepdimen.matrix.common.security.util.SecurityUtils;
import com.deepdimen.matrix.skill.api.SkillResultStore;
import com.deepdimen.matrix.skill.api.SkillContract.*;
import com.deepdimen.matrix.work.entity.WorkBriefRunEntity;
import com.deepdimen.matrix.work.mapper.WorkBriefSkillResultMapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * Raw model output stays with the business run; only reviewed reports are user-facing.
 */
@Component
@RequiredArgsConstructor
public class WorkBriefSkillResultStore implements SkillResultStore {

	private final WorkBriefSkillResultMapper results;

	private final TenantUserDirectory users;

	private final ObjectMapper json;

	@Override
	public String consumerType() {
		return "WORK_BRIEF";
	}

	@Override
	public String authorizeInput(Identity identity, Consumer consumer, String businessRunId, String inputJson) {
		var run = authorized(identity, consumer, businessRunId);
		try {
			var reader = json.reader()
				.with(com.fasterxml.jackson.databind.DeserializationFeature.FAIL_ON_TRAILING_TOKENS);
			var supplied = reader.readTree(inputJson);
			var saved = reader.readTree(run.getInputSnapshot());
			if (supplied == null || saved == null || !supplied.isObject() || !supplied.equals(saved))
				throw invalid();
			return saved.toString();
		}
		catch (java.io.IOException ex) {
			throw invalid();
		}
	}

	@Override
	public String save(Identity identity, Consumer consumer, String businessRunId, String skillRunId,
			String outputJson) {
		var prior = authorized(identity, consumer, businessRunId);
		long skillRun = id(skillRunId);
		if (outputJson == null || outputJson.length() > 65536)
			throw invalid();
		try {
			var output = json.reader()
				.with(com.fasterxml.jackson.databind.DeserializationFeature.FAIL_ON_TRAILING_TOKENS)
				.readTree(outputJson);
			if (output == null || !output.isObject())
				throw invalid();
			String normalized = output.toString();
			int saved = results.saveOnce(id(identity.tenantId()), id(identity.subjectId()), id(consumer.id()),
					id(businessRunId), skillRun, normalized);
			if (saved != 1) {
				var existing = authorized(identity, consumer, businessRunId);
				if (!Long.valueOf(skillRun).equals(existing.getSkillRunId())
						|| !output.equals(json.readTree(existing.getSkillOutputJson()))) {
					throw new IllegalStateException("业务运行已有不同结果或已结束");
				}
			}
			return reference(businessRunId, skillRunId);
		}
		catch (java.io.IOException ex) {
			throw invalid();
		}
	}

	@Override
	public String read(Identity identity, Consumer consumer, String businessRunId, String skillRunId,
			String resultRef) {
		if (!reference(businessRunId, skillRunId).equals(resultRef))
			throw invalid();
		var run = authorized(identity, consumer, businessRunId);
		if (!Long.valueOf(id(skillRunId)).equals(run.getSkillRunId()) || run.getSkillOutputJson() == null)
			throw invalid();
		return run.getSkillOutputJson();
	}

	private WorkBriefRunEntity authorized(Identity identity, Consumer consumer, String businessRunId) {
		var user = SecurityUtils.getUser();
		var auth = org.springframework.security.core.context.SecurityContextHolder.getContext().getAuthentication();
		if (auth == null || !auth.isAuthenticated() || user == null || user.getId() == null
				|| !user.getId().toString().equals(identity.actorId())
				|| !identity.actorId().equals(identity.subjectId())
				|| !String.valueOf(TenantContextHolder.getTenantId()).equals(identity.tenantId())
				|| !consumerType().equals(consumer.type()) || !"WORK_BRIEF".equals(consumer.slotCode())
				|| !"default".equals(consumer.bindingKey())
				|| !users.isAssignableUser(id(identity.tenantId()), id(identity.subjectId())))
			throw invalid();
		var run = results.findOwned(id(identity.tenantId()), id(identity.subjectId()), id(consumer.id()),
				id(businessRunId));
		if (run == null || run.getOrgId() == null
				|| !users.isOrganizationMember(id(identity.tenantId()), id(identity.subjectId()), run.getOrgId()))
			throw invalid();
		return run;
	}

	private String reference(String businessRunId, String skillRunId) {
		return "work-brief-run:" + id(businessRunId) + ":skill:" + id(skillRunId);
	}

	private long id(String value) {
		try {
			long number = Long.parseLong(value);
			if (number <= 0 || !Long.toString(number).equals(value))
				throw invalid();
			return number;
		}
		catch (NumberFormatException ex) {
			throw invalid();
		}
	}

	private IllegalArgumentException invalid() {
		return new IllegalArgumentException("RESULT_NOT_ACCESSIBLE");
	}

}
