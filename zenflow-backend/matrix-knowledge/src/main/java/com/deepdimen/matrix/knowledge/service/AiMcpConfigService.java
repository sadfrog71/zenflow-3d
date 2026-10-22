package com.deepdimen.matrix.knowledge.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.deepdimen.matrix.knowledge.dto.AiMcpToolsDTO;
import com.deepdimen.matrix.knowledge.entity.AiMcpConfigEntity;

import java.util.List;

/**
 * AI MCP（Model Context Protocol）配置服务接口
 * <p>
 * 管理MCP协议的配置信息，支持动态加载和管理AI模型的工具集， 实现AI能力的灵活扩展和工具调用。
 *
 * @author caoyuan
 * @date 2025/06/01
 */
public interface AiMcpConfigService extends IService<AiMcpConfigEntity> {

	/**
	 * 保存或更新MCP配置
	 * <p>
	 * 保存新的MCP配置或更新已存在的配置信息， 包括MCP服务地址、认证信息、工具集配置等。
	 * @param aiMcpConfig MCP配置实体，包含服务端点、认证、工具集等信息
	 * @return 操作是否成功
	 */
	boolean saveOrUpdateMcp(AiMcpConfigEntity aiMcpConfig);

	/**
	 * 获取MCP工具列表
	 * <p>
	 * 根据MCP配置动态获取可用的工具集列表， 包括工具名称、描述、参数定义等信息，供AI模型调用。
	 * @param aiMcpConfig MCP配置实体，用于连接对应的MCP服务
	 * @return 可用工具的列表，每个工具包含名称、描述、参数schema等信息
	 */
	List<AiMcpToolsDTO> listTools(AiMcpConfigEntity aiMcpConfig);

}
