package com.deepdimen.matrix.knowledge.support.provider;

import cn.hutool.core.util.EnumUtil;
import cn.hutool.core.util.StrUtil;
import com.github.houbb.heaven.util.util.OsUtil;
import com.deepdimen.matrix.knowledge.config.properties.AiKnowledgeProperties;
import com.deepdimen.matrix.knowledge.entity.AiMcpConfigEntity;
import com.deepdimen.matrix.knowledge.support.constant.McpDefaultCommandEnums;
import com.deepdimen.matrix.knowledge.support.constant.McpTypeEnums;
import dev.langchain4j.mcp.client.DefaultMcpClient;
import dev.langchain4j.mcp.client.McpClient;
import dev.langchain4j.mcp.client.logging.DefaultMcpLogMessageHandler;
import dev.langchain4j.mcp.client.transport.McpTransport;
import dev.langchain4j.mcp.client.transport.http.HttpMcpTransport;
import dev.langchain4j.mcp.client.transport.http.StreamableHttpMcpTransport;
import dev.langchain4j.mcp.client.transport.stdio.StdioMcpTransport;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.*;

/**
 * MCP客户端提供者
 * <p>
 * 负责创建、缓存和管理MCP（Model Context Protocol）客户端实例 支持SSE和STDIO两种通信方式，避免重复创建客户端，提升性能
 * 提供客户端生命周期管理功能
 *
 * @author caoyuan
 * @date 2025/07/11
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class McpClientProvider {

	private final AiKnowledgeProperties knowledgeProperties;

	/**
	 * MCP客户端缓存池 key为客户端唯一标识，value为MCP客户端实例
	 */
	private static final Map<String, McpClient> MCP_CLIENT_MAP = new HashMap<>();

	/**
	 * 获取或创建MCP客户端
	 * <p>
	 * 如果客户端已存在于缓存中则直接返回，否则创建新的客户端实例并缓存
	 * @param mcpConfig MCP配置信息
	 * @return MCP客户端实例
	 */
	public McpClient getOrCreateMcpClient(AiMcpConfigEntity mcpConfig) {
		return MCP_CLIENT_MAP.computeIfAbsent(mcpConfig.getName(), key -> {
			log.info("创建新的MCP客户端: {}", mcpConfig.getName());
			return createMcpClient(mcpConfig);
		});
	}

	/**
	 * 移除指定的MCP客户端缓存
	 * <p>
	 * 用于配置更新或客户端失效时清理缓存
	 * @param mcpConfig MCP配置信息
	 */
	public void removeMcpClient(AiMcpConfigEntity mcpConfig) {
		McpClient removed = MCP_CLIENT_MAP.remove(mcpConfig.getName());
		if (removed != null) {
			log.info("移除MCP客户端缓存: {}", mcpConfig.getName());
		}
	}

	/**
	 * 清空所有MCP客户端缓存
	 * <p>
	 * 用于系统重置或批量更新配置时使用
	 */
	public void clearAllMcpClients() {
		MCP_CLIENT_MAP.clear();
		log.info("清空所有MCP客户端缓存");
	}

	/**
	 * 创建MCP客户端实例
	 * <p>
	 * 根据配置的通信类型（SSE或STDIO）创建对应的传输器和客户端
	 * @param mcpConfig MCP配置信息
	 * @return MCP客户端实例
	 */
	private McpClient createMcpClient(AiMcpConfigEntity mcpConfig) {
		// 根据MCP类型创建适当的传输器
		McpTransport transport;
		if (McpTypeEnums.SSE.getType().equals(mcpConfig.getMcpType())) {
			transport = createHttpTransport(mcpConfig);
		}
		else if (McpTypeEnums.STEAMABLE.getType().equals(mcpConfig.getMcpType())) {
			transport = createStreamableTransport(mcpConfig);
		}
		else {
			transport = createStdioTransport(mcpConfig);
		}

		// 配置并构建MCP客户端
		try {
			return new DefaultMcpClient.Builder().transport(transport)
				.logHandler(new DefaultMcpLogMessageHandler())
				.build();
		}
		catch (Exception e) {
			log.error("创建MCP客户端失败: {}, 错误信息: {}", mcpConfig.getName(), e.getMessage(), e);
			return null;
		}
	}

	/**
	 * 向下兼容，spring ai 还不支持 StreamableHttpMcpTransport 创建Http的McpTransport对象
	 * @param mcpConfig Mcp配置实体
	 * @return 可流式传输的McpTransport对象
	 */
	@SuppressWarnings("removal")
	private McpTransport createHttpTransport(AiMcpConfigEntity mcpConfig) {
		Map<String, String> envMap = parseEnvironmentVariables(mcpConfig, ":");
		return new HttpMcpTransport.Builder().sseUrl(mcpConfig.getSseUrl())
			.customHeaders(envMap)
			.logRequests(knowledgeProperties.isShowLog())
			.logResponses(knowledgeProperties.isShowLog())
			.build();
	}

	/**
	 * 创建可流式传输的McpTransport对象
	 * @param mcpConfig Mcp配置实体
	 * @return 可流式传输的McpTransport对象
	 */
	private McpTransport createStreamableTransport(AiMcpConfigEntity mcpConfig) {
		Map<String, String> envMap = parseEnvironmentVariables(mcpConfig, ":");

		return new StreamableHttpMcpTransport.Builder().url(mcpConfig.getSseUrl())
			.customHeaders(envMap)
			.logRequests(knowledgeProperties.isShowLog())
			.logResponses(knowledgeProperties.isShowLog())
			.build();
	}

	/**
	 * 创建STDIO传输器
	 * <p>
	 * 用于标准输入输出的子进程通信方式
	 */
	private McpTransport createStdioTransport(AiMcpConfigEntity mcpConfig) {
		List<String> command = buildCommand(mcpConfig);
		Map<String, String> envMap = parseEnvironmentVariables(mcpConfig, "=");

		return new StdioMcpTransport.Builder().command(command)
			.environment(envMap)
			.logEvents(knowledgeProperties.isShowLog())
			.build();
	}

	/**
	 * 构建命令行参数列表
	 * <p>
	 * 根据操作系统和配置构建完整的命令行参数
	 */
	private List<String> buildCommand(AiMcpConfigEntity mcpConfig) {
		List<String> command = new ArrayList<>();

		// Windows系统需要通过cmd执行
		if (OsUtil.isWindows()) {
			command.add("cmd.exe");
			command.add("/c");
		}
		else {
			command.add(mcpConfig.getCommand());
		}

		// 处理预定义的默认命令
		McpDefaultCommandEnums commandEnum = EnumUtil.fromStringQuietly(McpDefaultCommandEnums.class,
				mcpConfig.getCommand());

		if (Objects.nonNull(commandEnum)) {
			command.add(commandEnum.command);
		}

		// 添加额外的命令参数
		if (StrUtil.isNotBlank(mcpConfig.getParameters())) {
			List<String> paramsList = StrUtil.split(mcpConfig.getParameters(), StrUtil.LF);
			command.addAll(paramsList);
		}

		return command;
	}

	/**
	 * 解析环境变量配置
	 * <p>
	 * 将配置中的环境变量字符串解析为键值对映射
	 */
	private Map<String, String> parseEnvironmentVariables(AiMcpConfigEntity mcpConfig, String delimiter) {
		Map<String, String> envMap = new HashMap<>();

		if (StrUtil.isNotBlank(mcpConfig.getEnvironmentVariables())) {
			List<String> envList = StrUtil.split(mcpConfig.getEnvironmentVariables(), StrUtil.LF);
			for (String env : envList) {
				List<String> envPair = StrUtil.split(env, delimiter);
				if (envPair.size() >= 2) {
					envMap.put(envPair.get(0), envPair.get(1));
				}
			}
		}

		return envMap;
	}

}
