package com.deepdimen.matrix.knowledge.mapper;

import com.deepdimen.matrix.common.data.datascope.MatrixBaseMapper;
import com.deepdimen.matrix.knowledge.entity.AiMaterialLogEntity;
import org.apache.ibatis.annotations.Mapper;

/**
 * AI资源记录数据访问接口
 * <p>
 * 提供AI资源记录表的数据库访问操作 用于管理AI生成的各类资源（图片、音频、视频等）的生成记录和存储信息
 *
 * @author matrix
 * @date 2025-05-08
 */
@Mapper
public interface AiMaterialLogMapper extends MatrixBaseMapper<AiMaterialLogEntity> {

}