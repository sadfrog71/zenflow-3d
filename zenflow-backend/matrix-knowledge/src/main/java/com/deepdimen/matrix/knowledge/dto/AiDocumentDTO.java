package com.deepdimen.matrix.knowledge.dto;

import com.deepdimen.matrix.knowledge.entity.AiDocumentEntity;
import lombok.Data;
import lombok.experimental.FieldNameConstants;

import java.util.List;

/**
 * AI文档数据传输对象
 * <p>
 * 用于文档上传、更新和管理的数据传输，支持多种文档来源和切片配置
 *
 * @author caoyuan
 * @date 2024/3/14
 */
@Data
@FieldNameConstants
public class AiDocumentDTO {

	/**
	 * 文档ID 文档的唯一标识符
	 */
	private Long id;

	/**
	 * 所属知识库ID 文档所属的知识库唯一标识
	 */
	private Long datasetId;

	/** 上传时指定分类，NULL 为未分类。 */
	private Long categoryId;

	/** 仅HTTP线程同步创建后传递给解析线程，不接受请求输入。 */
	@com.fasterxml.jackson.annotation.JsonIgnore
	private AiDocumentEntity preparedDocument;

	/**
	 * 文件列表 批量上传时的文件实体列表
	 */
	private List<AiDocumentEntity> files;

	/**
	 * 文件内容 直接输入的文本内容
	 */
	private String content;

	/**
	 * 文件来源类型 如：file（文件上传）、url（网页）、text（文本输入）
	 */
	private String sourceType;

	/**
	 * 文件处理状态 如：pending（待处理）、processing（处理中）、completed（已完成）、failed（失败）
	 */
	private String fileStatus;

	/**
	 * 文件名称 文档的显示名称
	 */
	private String name;

	/**
	 * 文档URL 当sourceType为url时，存储网页地址
	 */
	private String url;

	/**
	 * 文档切片类型 如：auto（自动）、custom（自定义）
	 */
	private String sliceType;

	/**
	 * 最大分段令牌数 每个文档切片的最大token数量
	 */
	private int maxSegmentSizeInTokens;

	/**
	 * 最大重叠令牌数 相邻切片之间重叠的token数量，用于保持上下文连续性
	 */
	private int maxOverlapSizeInTokens;

}
