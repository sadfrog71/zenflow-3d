package com.deepdimen.matrix.discover.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * 技能请求体处理器
 * <p>
 * 旧 Chat 不再提供 Skill 执行旁路。对旧客户端残留的 enabled_skills 字段显式移除， 不查询 Skill、不注入
 * Prompt，也不产生“已执行”语义。
 *
 * @author matrix
 * @date 2026-04-25
 */
@Slf4j
@Component
public class SkillProcessor {

	/**
	 * 忽略旧 Chat 请求体中的 enabled_skills 字段。
	 * @param bodyNode 请求体 JSON（会被原地修改）
	 */
	public void apply(ObjectNode bodyNode) {
		JsonNode enabledSkillsNode = bodyNode.remove("enabled_skills");
		JsonNode inputsNode = bodyNode.get("inputs");
		JsonNode extendedSystemPromptNode = inputsNode instanceof ObjectNode inputs
				? inputs.remove("extended_system_prompt") : null;
		if (enabledSkillsNode != null || extendedSystemPromptNode != null) {
			log.info("[SkillProcessor] 旧 Chat 已移除 Skill 旁路字段，Skill 请在统一对话中使用");
		}
	}

}
