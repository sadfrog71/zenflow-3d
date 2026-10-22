package com.deepdimen.matrix.knowledge.service.impl;

import cn.hutool.core.collection.CollUtil;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.deepdimen.matrix.common.core.util.SpringContextHolder;
import com.deepdimen.matrix.knowledge.config.AiEmbedInitRunner;
import com.deepdimen.matrix.knowledge.dto.AiMcpToolsDTO;
import com.deepdimen.matrix.knowledge.entity.AiMcpConfigEntity;
import com.deepdimen.matrix.knowledge.mapper.AiMcpConfigMapper;
import com.deepdimen.matrix.knowledge.service.AiMcpConfigService;
import com.deepdimen.matrix.knowledge.support.provider.McpClientProvider;
import dev.langchain4j.agent.tool.ToolSpecification;
import dev.langchain4j.mcp.client.McpClient;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.io.Serializable;
import java.util.Collection;
import java.util.List;

/**
 * MCP配置服务实现类
 * <p>
 * 管理Model Context Protocol（MCP）配置 支持MCP客户端的创建、更新和删除 提供工具列表查询和向量化存储
 *
 * @author matrix
 * @date 2025-03-22 13:36:32
 */
@Service
@RequiredArgsConstructor
public class AiMcpConfigServiceImpl extends ServiceImpl<AiMcpConfigMapper, AiMcpConfigEntity>
		implements AiMcpConfigService {

	/**
	 * MCP客户端提供者 管理MCP客户端的生命周期
	 */
	private final McpClientProvider mcpClientProvider;

	/**
	 * 保存或更新MCP配置
	 * <p>
	 * 更新时清理旧的客户端缓存 将配置信息向量化存储供快速检索
	 * @param mcpConfigEntity MCP配置实体
	 * @return 保存成功返回true
	 */
	@Override
	public boolean saveOrUpdateMcp(AiMcpConfigEntity mcpConfigEntity) {
		// 如果是更新操作，先清理旧的客户端缓存
		if (mcpConfigEntity.getMcpId() != null) {
			AiMcpConfigEntity oldConfig = baseMapper.selectById(mcpConfigEntity.getMcpId());
			if (oldConfig != null) {
				mcpClientProvider.removeMcpClient(oldConfig);
			}
		}

		baseMapper.insertOrUpdate(mcpConfigEntity);
		SpringContextHolder.publishEvent(new AiEmbedInitRunner.EmbedInitEvent(this));
		return true;
	}

	/**
	 * 删除MCP配置
	 * <p>
	 * 删除前清理对应的客户端缓存 确保资源正确释放
	 * @param id MCP配置 ID
	 * @return 删除成功返回true
	 */
	@Override
	public boolean removeById(Serializable id) {
		AiMcpConfigEntity mcpConfig = baseMapper.selectById(id);
		if (mcpConfig != null) {
			mcpClientProvider.removeMcpClient(mcpConfig);
		}
		super.removeById(id);
		SpringContextHolder.publishEvent(new AiEmbedInitRunner.EmbedInitEvent(this));
		return true;
	}

	/**
	 * 批量删除MCP配置
	 * <p>
	 * 逐个清理客户端缓存后删除 确保所有相关资源被正确释放
	 * @param list 配置ID列表
	 * @return 删除成功返回true
	 */
	@Override
	public boolean removeBatchByIds(Collection<?> list) {
		// 删除前先清理客户端缓存
		for (Object id : list) {
			AiMcpConfigEntity mcpConfig = baseMapper.selectById((Serializable) id);
			if (mcpConfig != null) {
				mcpClientProvider.removeMcpClient(mcpConfig);
			}
		}
		super.removeBatchByIds(list);
		SpringContextHolder.publishEvent(new AiEmbedInitRunner.EmbedInitEvent(this));
		return true;
	}

	/**
	 * 获取MCP工具列表
	 * <p>
	 * 连接MCP服务器获取可用工具 转换为简化的DTO格式返回
	 * @param aiMcpConfig 包含mcpId的配置实体
	 * @return 工具列表，无工具时返回null
	 */
	@Override
	public List<AiMcpToolsDTO> listTools(AiMcpConfigEntity aiMcpConfig) {
		AiMcpConfigEntity aiMcpConfigEntity = baseMapper.selectById(aiMcpConfig.getMcpId());
		McpClient mcpClient = mcpClientProvider.getOrCreateMcpClient(aiMcpConfigEntity);

		List<ToolSpecification> toolSpecifications = mcpClient.listTools();
		if (CollUtil.isEmpty(toolSpecifications)) {
			return null;
		}

		return mcpClient.listTools().stream().map(toolSpecification -> {
			AiMcpToolsDTO aiMcpToolsDTO = new AiMcpToolsDTO();
			aiMcpToolsDTO.setName(toolSpecification.name());
			aiMcpToolsDTO.setDescription(toolSpecification.description());
			return aiMcpToolsDTO;
		}).toList();
	}

}
