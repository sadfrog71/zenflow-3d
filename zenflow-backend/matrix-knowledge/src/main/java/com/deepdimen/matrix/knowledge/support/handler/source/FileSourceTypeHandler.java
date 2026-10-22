package com.deepdimen.matrix.knowledge.support.handler.source;

import com.deepdimen.matrix.knowledge.dto.AiDocumentDTO;
import com.deepdimen.matrix.knowledge.entity.AiDocumentEntity;

/**
 * 文件源类型处理器接口
 * <p>
 * 定义不同来源文档的处理流程 支持多种文档源类型：文件上传、文本输入、Excel问答等 实现类需要处理文档的完整生命周期
 *
 * @author caoyuan
 * @date 2024/10/3
 */
public interface FileSourceTypeHandler {

	/**
	 * 判断是否支持指定的源类型
	 * <p>
	 * 每个处理器实现应该只支持一种源类型
	 * @param sourceType 源类型标识
	 * @return 是否支持该类型
	 */
	boolean supports(String sourceType);

	/**
	 * 保存文档元数据
	 * <p>
	 * 将文档基本信息保存到数据库 包括文件名、路径、类型等信息
	 * @param documentDTO 文档数据传输对象
	 * @return 保存后的文档实体
	 */
	AiDocumentEntity saveDocument(AiDocumentDTO documentDTO);

	/**
	 * 创建并保存文档切片
	 * <p>
	 * 将文档内容分割成多个切片 便于后续的向量化和检索
	 * @param documentEntity 文档实体
	 * @param documentDTO 文档数据传输对象
	 */
	void saveSlice(AiDocumentEntity documentEntity, AiDocumentDTO documentDTO);

	/**
	 * 生成文档摘要
	 * <p>
	 * 使用AI模型生成文档的简要描述
	 * @param documentEntity 文档实体
	 * @param documentDTO 文档数据传输对象
	 */
	void summaryDocument(AiDocumentEntity documentEntity, AiDocumentDTO documentDTO);

	/**
	 * 为文档实体打标签
	 * @param documentEntity 文档实体对象
	 * @param documentDTO 文档数据传输对象
	 */
	void tagsDocument(AiDocumentEntity documentEntity, AiDocumentDTO documentDTO);

	/**
	 * 向量化文档切片
	 * <p>
	 * 将切片内容转换为向量并存储 用于后续的语义搜索和相似度计算
	 * @param documentEntity 文档实体
	 */
	void embedSlice(AiDocumentEntity documentEntity);

	/**
	 * 处理文档
	 * <p>
	 * 执行完整的文档处理流程 包括保存、切片、摘要和向量化
	 * @param aiDocumentDTO AI文档数据传输对象
	 */
	void handle(AiDocumentDTO aiDocumentDTO);

}
