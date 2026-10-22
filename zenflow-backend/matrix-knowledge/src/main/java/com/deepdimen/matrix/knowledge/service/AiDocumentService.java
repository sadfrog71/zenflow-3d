package com.deepdimen.matrix.knowledge.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.IService;
import com.deepdimen.matrix.common.core.util.R;
import com.deepdimen.matrix.knowledge.dto.AiCrawleDocumentDTO;
import com.deepdimen.matrix.knowledge.dto.AiDocumentDTO;
import com.deepdimen.matrix.knowledge.entity.AiDocumentEntity;
import com.deepdimen.matrix.knowledge.support.handler.source.FileSourceTypeHandler;

import java.io.IOException;
import java.util.List;

/**
 * AI文档服务接口
 * <p>
 * 提供知识库文档的上传、解析、切片、向量化等核心功能， 支持多种文档格式和数据源（文件、网页、数据库等）
 * </p>
 *
 * @author matrix
 */
public interface AiDocumentService extends IService<AiDocumentEntity> {

	/**
	 * 分页查询文档列表
	 * <p>
	 * 根据知识库ID、文档状态等条件查询文档列表
	 * </p>
	 * @param page 分页参数
	 * @param aiDocument 查询条件（如知识库ID、文档名称等）
	 * @return 文档分页数据
	 */
	Page<AiDocumentEntity> getDocumentPage(Page<AiDocumentEntity> page, AiDocumentEntity aiDocument);

	/**
	 * 保存文档
	 * <p>
	 * 处理不同类型的文档上传，包括文件解析、内容提取、切片处理等
	 * </p>
	 * @param handler 文档类型处理器（处理不同格式的文档）
	 * @param aiDocument 文档信息和配置参数
	 */
	void save(FileSourceTypeHandler handler, AiDocumentDTO aiDocument);

	/**
	 * 批量删除文档及其切片
	 * <p>
	 * 删除文档记录的同时，清理相关的文档切片和向量数据
	 * </p>
	 * @param idList 文档ID列表
	 * @return 删除是否成功
	 */
	Boolean removeDocumentAndSliceBatchByIds(List<Long> idList);

	/**
	 * 重新处理文档
	 * <p>
	 * 当文档处理失败或需要更新切片策略时，重新进行文档解析和切片
	 * </p>
	 * @param aiDocument 文档信息及新的处理参数
	 */
	void retryDocument(AiDocumentDTO aiDocument);

	/**
	 * 生成文档摘要
	 * <p>
	 * 使用AI模型对文档内容生成摘要，便于快速了解文档主要内容
	 * </p>
	 * @param documentEntity 待生成摘要的文档
	 */
	void summaryDocument(AiDocumentEntity documentEntity);

	/**
	 * 为文档实体打标签
	 * @param documentEntity 需要打标签的文档实体
	 */
	void tagsDocument(AiDocumentEntity documentEntity);

	/**
	 * 文档向量化处理
	 * <p>
	 * 将文档切片转换为向量并存储到向量数据库，用于语义检索
	 * </p>
	 * @param name 文档名称或标识
	 * @return 向量化处理结果
	 * @throws IOException IO异常
	 */
	R embedDocument(String name) throws IOException;

	/**
	 * 爬取网页文档
	 * <p>
	 * 从指定URL爬取网页内容，提取有效信息并保存为文档
	 * </p>
	 * @param crawleDocumentDTO 爬取配置（URL、深度、过滤规则等）
	 * @return 爬取任务ID或结果信息
	 */
	String crawleDocument(AiCrawleDocumentDTO crawleDocumentDTO);

	/**
	 * 获取图表数据
	 * @param documentId 文档ID
	 * @return 图表数据
	 */
	R getGraphData(Long documentId);

	/**
	 * 根据数据集ID和节点ID获取图节点数据
	 * @param datasetId 数据集ID
	 * @param nodeId 节点ID
	 * @return 图节点数据
	 */
	R getGraphNodeData(Long datasetId, Long nodeId);

}
