package com.deepdimen.matrix.discover.service;

import com.deepdimen.matrix.discover.model.AgentResponse;
import com.deepdimen.matrix.discover.model.AgentDetail;
import com.deepdimen.matrix.discover.service.impl.AgentServiceImpl;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.core.io.ResourceLoader;
import org.springframework.core.io.DefaultResourceLoader;

import static org.junit.jupiter.api.Assertions.*;

public class AgentServiceTest {

	private AgentService agentService;

	private ObjectMapper objectMapper;

	private ResourceLoader resourceLoader;

	@BeforeEach
	void setUp() {
		objectMapper = new ObjectMapper();
		resourceLoader = new DefaultResourceLoader();
		agentService = new AgentServiceImpl(objectMapper, resourceLoader);
	}

	@Test
	void testGetAllAgents() {
		// 测试获取所有agents
		AgentResponse response = agentService.getAllAgents("zh-CN");

		assertNotNull(response);
		assertNotNull(response.getAgents());
		assertNotNull(response.getTags());

		// 验证是否加载了agents
		assertTrue(response.getAgents().size() > 0, "应该加载到至少一个agent");

		// 验证agents的基本字段
		response.getAgents().forEach(agent -> {
			assertNotNull(agent.getIdentifier(), "Agent identifier不能为空");
			assertNotNull(agent.getMeta(), "Agent meta不能为空");
			assertNotNull(agent.getMeta().getTitle(), "Agent title不能为空");
			assertNotNull(agent.getMeta().getDescription(), "Agent description不能为空");
		});

		System.out.println("成功加载了 " + response.getAgents().size() + " 个agents");
		System.out.println("收集到 " + response.getTags().size() + " 个标签");
	}

	@Test
	void testGetAgentByIdentifier() {
		// 测试根据identifier获取特定agent
		AgentDetail detail = agentService.getAgentByIdentifier("business-automation-expert", "zh-CN");

		assertNotNull(detail, "应该能找到business-automation-expert");
		assertEquals("business-automation-expert", detail.getIdentifier());
		assertNotNull(detail.getMeta());
		assertNotNull(detail.getConfig());
		assertNotNull(detail.getConfig().getSystemRole());

		System.out.println("成功加载agent详情: " + detail.getMeta().getTitle());

		// 测试不存在的agent
		AgentDetail notFound = agentService.getAgentByIdentifier("non-existent-agent", "zh-CN");
		assertNull(notFound, "不存在的agent应该返回null");
	}

}
