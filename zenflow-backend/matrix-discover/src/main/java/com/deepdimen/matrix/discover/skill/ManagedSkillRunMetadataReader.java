package com.deepdimen.matrix.discover.skill;

import com.deepdimen.matrix.common.data.tenant.TenantContextHolder;
import com.deepdimen.matrix.discover.mapper.AiSkillRunMapper;
import com.deepdimen.matrix.skill.api.SkillContract.Consumer;
import com.deepdimen.matrix.skill.api.SkillRunMetadataReader;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import java.util.Objects;

@Component
@RequiredArgsConstructor
public class ManagedSkillRunMetadataReader implements SkillRunMetadataReader {

	private final AiSkillRunMapper runs;

	public Metadata find(String tenant, String actor, Consumer consumer, String business) {
		Long current = TenantContextHolder.getTenantId();
		if (current == null || !current.toString().equals(tenant) || actor == null || actor.isBlank()
				|| consumer == null || business == null || business.isBlank())
			throw new IllegalArgumentException("CONSUMER_NOT_AUTHORIZED");
		var matches = runs.findBusiness(tenant, actor, consumer.type(), consumer.id(), consumer.slotCode(),
				consumer.bindingKey(), business);
		if (matches.isEmpty())
			return null;
		if (matches.size() != 1)
			throw new IllegalStateException("SKILL_RUN_ASSOCIATION_AMBIGUOUS");
		var row = matches.get(0);
		if (!tenant.equals(row.getTenantId()) || !actor.equals(row.getActorId()) || !actor.equals(row.getSubjectId())
				|| !Objects.equals(consumer.type(), row.getConsumerType())
				|| !Objects.equals(consumer.id(), row.getConsumerId())
				|| !Objects.equals(consumer.slotCode(), row.getSlotCode())
				|| !Objects.equals(consumer.bindingKey(), row.getBindingKey())
				|| !business.equals(row.getBusinessRunId()))
			throw new IllegalArgumentException("CONSUMER_NOT_AUTHORIZED");
		return new Metadata(row.getId().toString(), row.getReleaseId().toString(),
				row.getBindingRevisionId().toString(), row.getStatus(), row.getErrorCode());
	}

}
