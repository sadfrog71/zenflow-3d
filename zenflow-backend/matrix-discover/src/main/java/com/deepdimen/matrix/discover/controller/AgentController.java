package com.deepdimen.matrix.discover.controller;

import cn.hutool.core.date.DatePattern;
import cn.hutool.core.date.DateUtil;
import cn.hutool.core.map.MapUtil;
import com.deepdimen.matrix.common.security.annotation.Inner;
import com.deepdimen.matrix.discover.feign.RemoteDatasetService;
import com.deepdimen.matrix.discover.model.Agent;
import com.deepdimen.matrix.discover.model.AgentConfig;
import com.deepdimen.matrix.discover.model.AgentDetail;
import com.deepdimen.matrix.discover.model.AgentMeta;
import com.deepdimen.matrix.discover.model.AgentResponse;
import com.deepdimen.matrix.discover.service.AgentService;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.AllArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

// AgentController.java
@RestController
@AllArgsConstructor
@RequestMapping("/agents")
@Tag(description = "agent", name = "ai代理模块")
public class AgentController {

	/**
	 * The AgentService instance used to handle agent-related operations.
	 */
	private final AgentService agentService;

	/**
	 * The AiDatasetService instance used to handle dataset-related operations.
	 */
	private final RemoteDatasetService datasetService;

	@Inner(value = false)
	@GetMapping("/index.{locale}.json")
	public ResponseEntity<AgentResponse> getAllAgents(@PathVariable String locale) {
		AgentResponse agentDetail = agentService.getAllAgents(locale);
		List<Map<String, Object>> list = datasetService.getShares().getData();
		if (list != null && !list.isEmpty()) {
			List<Agent> agents = new ArrayList<>();
			List<String> tags = new ArrayList<>();
			for (Map<String, Object> aiDatasetEntity : list) {
				Agent agent = new Agent();
				agent.setAuthor("caoychn");
				agent.setHomepage("https://agents.deepdimen.com");
				agent.setCreatedAt(DateUtil.format(DateUtil.date(MapUtil.getLong(aiDatasetEntity, "createTime")),
						DateTimeFormatter.ofPattern(DatePattern.NORM_DATE_PATTERN)));
				agent.setIdentifier(MapUtil.getStr(aiDatasetEntity, "id"));
				agent.setSchemaVersion(1);

				AgentMeta meta = new AgentMeta();
				meta.setAvatar("https://api.deepdimen.com" + MapUtil.getStr(aiDatasetEntity, "avatarUrl"));
				meta.setDescription(MapUtil.getStr(aiDatasetEntity, "description"));
				meta.setTitle(MapUtil.getStr(aiDatasetEntity, "name"));
				meta.setCategory(MapUtil.getStr(aiDatasetEntity, "category"));
				meta.setTags(MapUtil.get(aiDatasetEntity, "tags", String[].class));
				agent.setMeta(meta);

				AgentConfig config = new AgentConfig();
				config.setModel("qwen-chat");
				config.setProvider("matrix");
				config.setDatasetId(MapUtil.getLong(aiDatasetEntity, "id"));
				config.setSystemRole(MapUtil.getStr(aiDatasetEntity, "welcomeMsg"));
				agent.setConfig(config);

				agents.add(agent);
				tags.addAll(List.of(meta.getTags()));
			}
			agentDetail.getAgents().addAll(agents);
			agentDetail.getTags().addAll(tags);
		}
		return ResponseEntity.ok(agentDetail);
	}

	@Inner(value = false)
	@GetMapping("/{identifier}.{locale}.json")
	public ResponseEntity<AgentDetail> getAgentByIdentifier(@PathVariable String identifier,
			@PathVariable String locale) {
		AgentDetail agentDetail = agentService.getAgentByIdentifier(identifier, locale);
		if (agentDetail == null) {
			Map<String, Object> aiDatasetEntity = datasetService.info(Long.valueOf(identifier)).getData();
			if (aiDatasetEntity != null) {
				agentDetail = new AgentDetail();
				agentDetail.setIdentifier(MapUtil.getStr(aiDatasetEntity, "id"));
				agentDetail.setSchemaVersion(1);
				AgentMeta meta = new AgentMeta();
				meta.setAvatar("https://api.deepdimen.com" + MapUtil.getStr(aiDatasetEntity, "avatarUrl"));
				meta.setDescription(MapUtil.getStr(aiDatasetEntity, "description"));
				meta.setTitle(MapUtil.getStr(aiDatasetEntity, "name"));
				meta.setCategory(MapUtil.getStr(aiDatasetEntity, "category"));
				meta.setTags(MapUtil.get(aiDatasetEntity, "tags", String[].class));
				agentDetail.setMeta(meta);

				AgentConfig config = new AgentConfig();
				config.setModel("qwen-chat");
				config.setProvider("matrix");
				config.setSystemRole(MapUtil.getStr(aiDatasetEntity, "welcomeMsg"));
				config.setDatasetId(MapUtil.getLong(aiDatasetEntity, "id"));
				agentDetail.setConfig(config);

				agentDetail.setCreatedAt(DateUtil.format(DateUtil.date(MapUtil.getLong(aiDatasetEntity, "createTime")),
						DateTimeFormatter.ofPattern(DatePattern.NORM_DATE_PATTERN)));
				agentDetail.setHomepage("https://www.shenwei-ai.com");
				agentDetail.setSchemaVersion(1);
				agentDetail.setAuthor("caoychn");
			}
		}
		return ResponseEntity.ok(agentDetail);
	}

}