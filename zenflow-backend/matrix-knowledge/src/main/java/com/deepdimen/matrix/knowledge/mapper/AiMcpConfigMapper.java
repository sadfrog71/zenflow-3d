package com.deepdimen.matrix.knowledge.mapper;

import com.deepdimen.matrix.common.data.datascope.MatrixBaseMapper;
import com.deepdimen.matrix.knowledge.entity.AiMcpConfigEntity;
import org.apache.ibatis.annotations.Mapper;

/**
 * MCP配置数据访问接口
 * <p>
 * 提供模型上下文协议(MCP)配置的数据库访问操作 管理MCP服务器的连接配置和交互参数
 *
 */
@Mapper
public interface AiMcpConfigMapper extends MatrixBaseMapper<AiMcpConfigEntity> {

}