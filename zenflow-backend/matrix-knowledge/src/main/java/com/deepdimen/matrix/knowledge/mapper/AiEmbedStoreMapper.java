package com.deepdimen.matrix.knowledge.mapper;

import com.deepdimen.matrix.common.data.datascope.MatrixBaseMapper;
import com.deepdimen.matrix.knowledge.entity.AiEmbedStoreEntity;
import org.apache.ibatis.annotations.Mapper;

/**
 * 向量库配置数据访问接口
 * <p>
 * 提供向量库配置表的数据库访问操作 用于管理Milvus等向量数据库的连接配置信息
 *
 */
@Mapper
public interface AiEmbedStoreMapper extends MatrixBaseMapper<AiEmbedStoreEntity> {

}