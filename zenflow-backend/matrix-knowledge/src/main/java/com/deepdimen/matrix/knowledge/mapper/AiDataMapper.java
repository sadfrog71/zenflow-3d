package com.deepdimen.matrix.knowledge.mapper;

import com.deepdimen.matrix.common.data.datascope.MatrixBaseMapper;
import com.deepdimen.matrix.knowledge.entity.AiDataEntity;
import org.apache.ibatis.annotations.Mapper;

/**
 * AI数据集管理数据访问接口
 * <p>
 * 提供AI数据集管理表的数据库访问操作 用于管理结构化数据的AI学习配置，支持数据库表的智能问答
 *
 * @author matrix
 * @date 2025-03-26
 */
@Mapper
public interface AiDataMapper extends MatrixBaseMapper<AiDataEntity> {

}