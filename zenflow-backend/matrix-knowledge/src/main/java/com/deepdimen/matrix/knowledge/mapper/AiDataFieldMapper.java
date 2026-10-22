package com.deepdimen.matrix.knowledge.mapper;

import com.deepdimen.matrix.common.data.datascope.MatrixBaseMapper;
import com.deepdimen.matrix.knowledge.entity.AiDataFieldEntity;
import org.apache.ibatis.annotations.Mapper;

/**
 * AI字段管理数据访问接口
 * <p>
 * 提供AI字段管理表的数据库访问操作 用于管理数据表字段的元数据信息，支持字段注释的虚拟化和修正
 *
 * @author matrix
 * @date 2025-03-26
 */
@Mapper
public interface AiDataFieldMapper extends MatrixBaseMapper<AiDataFieldEntity> {

}