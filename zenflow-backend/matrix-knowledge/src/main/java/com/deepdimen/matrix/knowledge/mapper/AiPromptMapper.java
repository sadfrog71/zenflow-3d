package com.deepdimen.matrix.knowledge.mapper;

import com.deepdimen.matrix.common.data.datascope.MatrixBaseMapper;
import com.deepdimen.matrix.knowledge.entity.AiPromptEntity;
import org.apache.ibatis.annotations.Mapper;

/**
 * AI提示词Mapper接口
 * <p>
 * 该接口提供了对AI提示词数据表的数据访问操作。 继承自MatrixBaseMapper，自动拥有基本的CRUD操作功能。
 * </p>
 *
 * @author matrix code generator
 */
@Mapper
public interface AiPromptMapper extends MatrixBaseMapper<AiPromptEntity> {

}