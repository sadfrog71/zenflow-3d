package com.deepdimen.matrix.discover.skill;

import com.deepdimen.matrix.discover.entity.AiSkillReleaseEntity;
import com.deepdimen.matrix.skill.api.SkillContract;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;

/** Maps persisted frozen fields to the cross-module contract. */
public final class ManagedSkillReleaseCodec {

	private final ObjectMapper json;

	public ManagedSkillReleaseCodec(ObjectMapper json) {
		this.json = json;
	}

	public SkillContract.ResolvedSkillSnapshot decode(String revisionId, AiSkillReleaseEntity release) {
		try {
			return new SkillContract.ResolvedSkillSnapshot(String.valueOf(release.getSkillId()),
					String.valueOf(release.getId()), release.getReleaseDigest(), revisionId,
					SkillContract.RuntimeKind.valueOf(release.getRuntimeKind()),
					json.readValue(release.getExecutorRef(), SkillContract.ExecutorRef.class), release.getInputSchema(),
					release.getOutputSchema(), release.getContentSnapshot(), json.readValue(release.getResourceRefs(),
							new com.fasterxml.jackson.core.type.TypeReference<List<SkillContract.ResourceRef>>() {
							}),
					release.getLimitsJson(), json.readValue(release.getAllowedParameters(),
							new com.fasterxml.jackson.core.type.TypeReference<List<String>>() {
							}));
		}
		catch (java.io.IOException ex) {
			throw new IllegalArgumentException("发布快照无效", ex);
		}
	}

}
