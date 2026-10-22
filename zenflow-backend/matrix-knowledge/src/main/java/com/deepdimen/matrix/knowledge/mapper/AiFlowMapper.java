package com.deepdimen.matrix.knowledge.mapper;

import com.deepdimen.matrix.common.data.datascope.MatrixBaseMapper;
import com.deepdimen.matrix.knowledge.entity.AiFlowEntity;
import org.apache.ibatis.annotations.Mapper;

/**
 * 大模型流程数据访问接口
 * <p>
 * 提供大模型流程表的数据库访问操作 用于管理基于LiteFlow的AI流程编排配置，支持流程DSL定义和API开放
 *
 * @author matrix
 */
@Mapper
public interface AiFlowMapper extends MatrixBaseMapper<AiFlowEntity> {

}