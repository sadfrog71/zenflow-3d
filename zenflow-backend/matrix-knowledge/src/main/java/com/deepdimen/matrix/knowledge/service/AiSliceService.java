package com.deepdimen.matrix.knowledge.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.deepdimen.matrix.knowledge.dto.AiSliceReCallDTO;
import com.deepdimen.matrix.knowledge.dto.AiSliceReCallRequestDTO;
import com.deepdimen.matrix.knowledge.entity.AiDocumentEntity;
import com.deepdimen.matrix.knowledge.entity.AiSliceEntity;
import com.deepdimen.matrix.knowledge.support.constant.SliceStatusEnums;

import java.util.List;

/**
 * AI文档切片服务接口 提供文档切片管理、向量嵌入和语义检索功能
 *
 * @author matrix
 */
public interface AiSliceService extends IService<AiSliceEntity> {

	/**
	 * 删除切片及其对应的向量嵌入 同时删除数据库中的切片记录和向量数据库中的嵌入向量
	 * @param sliceIdList 待删除的切片ID列表
	 * @return 删除操作是否成功
	 */
	Boolean removeSliceAndEmbeddingById(List<Long> sliceIdList);

	/**
	 * 异步更新切片和文档的命中次数 用于统计切片的使用频率，优化检索排序
	 * @param collect 被命中的切片ID列表
	 */
	void updateHitCount(List<String> collect);

	/**
	 * 对文档进行切片并生成向量嵌入 将文档内容分割成合适大小的片段，并生成对应的向量表示
	 * @param documentEntity 待处理的文档实体
	 * @param sliceStatusEnums 切片处理状态枚举
	 */
	void embedSlice(AiDocumentEntity documentEntity, SliceStatusEnums sliceStatusEnums);

	/**
	 * 更新切片信息 修改切片内容后需要重新生成向量嵌入
	 * @param aiSlice 待更新的切片实体
	 * @return 更新操作是否成功
	 */
	Boolean updateSlice(AiSliceEntity aiSlice);

	/**
	 * 语义召回检索 基于向量相似度进行文档切片的语义检索
	 * @param requestDTO 召回请求参数，包含查询文本、数量限制等
	 * @return 返回相关性最高的切片列表
	 */
	List<AiSliceReCallDTO> recall(AiSliceReCallRequestDTO requestDTO);

	/**
	 * 根据文档ID删除所有相关切片和向量 当删除文档时，同步删除该文档的所有切片数据
	 * @param id 文档ID
	 */
	void removeSliceAndEmbeddingByDocumentId(Long id);

}
