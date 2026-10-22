package com.deepdimen.matrix.discover.service.impl;

import com.deepdimen.matrix.discover.model.Agent;
import com.deepdimen.matrix.discover.model.AgentDetail;
import com.deepdimen.matrix.discover.model.AgentResponse;
import com.deepdimen.matrix.discover.service.AgentService;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.core.io.support.ResourcePatternResolver;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

@Service
@AllArgsConstructor
@Slf4j
public class AgentServiceImpl implements AgentService {

	private final ObjectMapper objectMapper;

	private final ResourceLoader resourceLoader;

	@Override
	public AgentResponse getAllAgents(String locale) {
		try {
			log.info("Getting all agents for locale: {}", locale);

			// 创建响应对象
			AgentResponse response = new AgentResponse();
			response.setSchemaVersion(1);

			// 获取所有代理
			List<Agent> agents = new ArrayList<>();
			Set<String> allTags = new HashSet<>();

			// 使用ResourcePatternResolver加载资源
			ResourcePatternResolver resolver = new PathMatchingResourcePatternResolver();

			// 构建资源路径模式
			String pattern = "classpath:agents/*." + locale + ".json";
			log.info("Searching for agents with pattern: {}", pattern);

			try {
				Resource[] resources = resolver.getResources(pattern);
				log.info("Found {} agent files", resources.length);

				if (resources.length == 0) {
					log.warn("No agent files found for locale: {}", locale);
					response.setAgents(agents);
					response.setTags(new ArrayList<>(allTags));
					return response;
				}

				// 处理找到的资源
				for (Resource resource : resources) {
					String filename = resource.getFilename();
					log.info("Processing agent file: {}", filename);

					try (InputStream inputStream = resource.getInputStream()) {
						// 读取并解析JSON文件
						Agent agent = objectMapper.readValue(inputStream, Agent.class);
						agents.add(agent);
						log.info("Successfully loaded agent: {} - {}", agent.getIdentifier(),
								agent.getMeta() != null ? agent.getMeta().getTitle() : "Unknown");

						// 收集所有标签
						if (agent.getMeta() != null && agent.getMeta().getTags() != null) {
							for (String tag : agent.getMeta().getTags()) {
								allTags.add(tag);
							}
						}
					}
					catch (IOException e) {
						log.error("Error parsing agent file: {}", filename, e);
						// 继续处理其他文件，不中断整个流程
					}
				}

			}
			catch (IOException e) {
				log.error("Error loading agent resources with pattern: {}", pattern, e);
				throw new RuntimeException("Failed to load agent resources: " + e.getMessage(), e);
			}

			// 设置响应数据
			response.setAgents(agents);
			response.setTags(new ArrayList<>(allTags));

			log.info("Successfully loaded {} agents with {} unique tags", agents.size(), allTags.size());
			return response;

		}
		catch (Exception e) {
			log.error("Unexpected error while processing agent files", e);
			throw new RuntimeException("Error processing agent files: " + e.getMessage(), e);
		}
	}

	@Override
	public AgentDetail getAgentByIdentifier(String identifier, String locale) {
		try {
			log.info("Getting agent by identifier: {} for locale: {}", identifier, locale);

			// 构建资源路径
			String resourcePath = "classpath:agents/" + identifier + "." + locale + ".json";
			log.info("Looking for agent at: {}", resourcePath);

			// 使用ResourceLoader加载资源
			Resource resource = resourceLoader.getResource(resourcePath);

			if (!resource.exists()) {
				log.warn("Agent file not found: {}", resourcePath);
				return null;
			}

			try (InputStream inputStream = resource.getInputStream()) {
				AgentDetail agentDetail = objectMapper.readValue(inputStream, AgentDetail.class);
				log.info("Successfully loaded agent detail: {} - {}", agentDetail.getIdentifier(),
						agentDetail.getMeta() != null ? agentDetail.getMeta().getTitle() : "Unknown");
				return agentDetail;
			}

		}
		catch (IOException e) {
			log.error("Error reading agent file for identifier: {} and locale: {}", identifier, locale, e);
			throw new RuntimeException("Error reading agent file: " + e.getMessage(), e);
		}
	}

}