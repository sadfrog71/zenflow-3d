package com.deepdimen.matrix.knowledge.mapper;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.deepdimen.matrix.common.data.datascope.MatrixBaseMapper;
import com.deepdimen.matrix.knowledge.entity.AiDocumentEntity;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

/**
 * 知识文档数据访问接口
 * <p>
 * 提供知识文档表的数据库访问操作 用于管理知识库中的文档信息，包括文档上传、切片状态、命中统计等
 *
 * @author matrix
 */
@Mapper
public interface AiDocumentMapper extends MatrixBaseMapper<AiDocumentEntity> {

	/**
	 * 获取文档页面
	 * @param page 页
	 * @param aiDocument AI 文档
	 * @return {@link Page }<{@link AiDocumentEntity }>
	 */
	Page<AiDocumentEntity> getDocumentPage(Page<AiDocumentEntity> page, @Param("query") AiDocumentEntity aiDocument);

}
