package com.deepdimen.matrix.knowledge.skill;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Component;

import com.deepdimen.matrix.common.core.constant.enums.YesNoEnum;
import com.deepdimen.matrix.skill.api.SkillContract.*;
import com.github.houbb.sensitive.word.bs.SensitiveWordBs;

/** Reuses the ordinary dataset sensitive-word switch and dictionary before any egress. */
@Component
public class KnowledgeSkillInputPolicy {

	private final KnowledgeSkillResources resources;

	private final ObjectProvider<SensitiveWordBs> dictionaries;

	public KnowledgeSkillInputPolicy(KnowledgeSkillResources resources, ObjectProvider<SensitiveWordBs> dictionaries) {
		this.resources = resources;
		this.dictionaries = dictionaries;
	}

	/** Null means allowed; a non-null value is the configured fixed reply. */
	public String fixedReply(Identity identity, Consumer consumer, ResolvedSkillSnapshot release, String question) {
		var current = resources.verify(identity, consumer, release);
		boolean checked = false;
		for (String datasetId : current.configuration().datasetIds()) {
			var dataset = resources.resolveRetrieval(identity, consumer, release, Long.valueOf(datasetId)).dataset();
			// Same semantics as SensitiveWordsRule: only explicit NO bypasses the check.
			if (YesNoEnum.NO.getCode().equals(dataset.getSensitiveFlag()) || checked)
				continue;
			var dictionary = dictionaries.getIfAvailable();
			if (dictionary == null)
				throw new IllegalStateException("KNOWLEDGE_INPUT_POLICY_UNAVAILABLE");
			if (dictionary.contains(question)) {
				String reply = dataset.getSensitiveMsg();
				if (reply == null || reply.isBlank() || reply.length() > 16000)
					throw new IllegalArgumentException("KNOWLEDGE_SENSITIVE_REPLY_UNCONFIGURED");
				return reply;
			}
			checked = true;
		}
		return null;
	}

}
