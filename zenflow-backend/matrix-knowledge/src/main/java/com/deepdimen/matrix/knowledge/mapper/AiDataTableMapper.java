package com.deepdimen.matrix.knowledge.mapper;

import com.deepdimen.matrix.common.data.datascope.MatrixBaseMapper;
import com.deepdimen.matrix.knowledge.entity.AiDataTableEntity;
import org.apache.ibatis.annotations.Mapper;

/**
 * AI数据表管理数据访问接口
 * <p>
 * 提供AI数据表管理表的数据库访问操作 用于管理数据库表的元数据信息，支持表注释的虚拟化管理
 *
 * @author matrix
 */
@Mapper
public interface AiDataTableMapper extends MatrixBaseMapper<AiDataTableEntity> {

}