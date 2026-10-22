package com.deepdimen.matrix.knowledge.support.handler.source;

import cn.hutool.extra.spring.SpringUtil;
import com.deepdimen.matrix.knowledge.dto.AiDocumentDTO;
import com.deepdimen.matrix.knowledge.entity.AiDocumentEntity;
import com.deepdimen.matrix.knowledge.service.AiDocumentService;
import com.deepdimen.matrix.knowledge.service.AiSliceService;
import com.deepdimen.matrix.knowledge.support.constant.SliceStatusEnums;
import lombok.extern.slf4j.Slf4j;

/**
 * 文件源类型处理器抽象类
 * <p>
 * 提供文档处理的通用流程实现 包括文档保存、切片、摘要生成和向量化等步骤 子类需要实现具体的文档保存和切片逻辑
 *
 * @author caoyuan
 * @date 2024/10/3
 */
@Slf4j
public abstract class AbstractFileSourceTypeHandler implements FileSourceTypeHandler {

	/**
	 * 生成文档摘要
	 * <p>
	 * 调用文档服务生成文档的摘要内容
	 * @param documentEntity 文档实体
	 * @param documentDTO 文档数据传输对象
	 */
	@Override
	public void summaryDocument(AiDocumentEntity documentEntity, AiDocumentDTO documentDTO) {
		SpringUtil.getBean(AiDocumentService.class).summaryDocument(documentEntity);
	}

	/**
	 * 为文档实体打标签
	 * @param documentEntity 文档实体对象
	 * @param documentDTO 文档数据传输对象
	 */
	@Override
	public void tagsDocument(AiDocumentEntity documentEntity, AiDocumentDTO documentDTO) {
		SpringUtil.getBean(AiDocumentService.class).tagsDocument(documentEntity);
	}

	/**
	 * 向量化文档切片
	 * <p>
	 * 将文档切片转换为向量并存储到向量数据库
	 * @param documentEntity 文档实体
	 */
	@Override
	public void embedSlice(AiDocumentEntity documentEntity) {
		SpringUtil.getBean(AiSliceService.class).embedSlice(documentEntity, SliceStatusEnums.UNSLICED);
	}

	/**
	 * 处理文档
	 * <p>
	 * 执行完整的文档处理流程： 1. 保存文档到数据库 2. 创建文档切片 3. 生成文档摘要 4. 向量化切片数据
	 * @param aiDocumentDTO AI文档数据传输对象
	 */
	@Override
	public void handle(AiDocumentDTO aiDocumentDTO) {
		log.info("======= AI 正在处理文档：{} =======", aiDocumentDTO.getName());
		// 数据库保存文档
		AiDocumentEntity documentEntity = aiDocumentDTO.getPreparedDocument() == null ? saveDocument(aiDocumentDTO)
				: aiDocumentDTO.getPreparedDocument();

		// 创建文档切片
		try {
			saveSlice(documentEntity, aiDocumentDTO);
		}
		catch (DocumentProcessingStoppedException e) {
			return;
		}
		catch (Exception e) {
			// 切片失败，更新状态和失败原因
			documentEntity.setSliceStatus(SliceStatusEnums.FAILED.getStatus());
			documentEntity.setSliceFailReason(e.getMessage());
			SpringUtil.getBean(AiDocumentService.class).updateById(documentEntity);
			return;
		}

		// 生成文档摘要
		try {
			summaryDocument(documentEntity, aiDocumentDTO);
		}
		catch (Exception e) {
			// 摘要生成失败，更新状态和失败原因
			documentEntity.setSummaryStatus(SliceStatusEnums.FAILED.getStatus());
			documentEntity.setSummaryFailReason(e.getMessage());
			SpringUtil.getBean(AiDocumentService.class).updateById(documentEntity);
			return;
		}

		// 生成文档标签
		tagsDocument(documentEntity, aiDocumentDTO);

		// 向量化切片
		embedSlice(documentEntity);
		log.info("======= AI 文档：{} 处理结束 =======", aiDocumentDTO.getName());
	}

}
