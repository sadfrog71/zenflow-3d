package com.deepdimen.matrix.knowledge.support.constant;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * 模型类型枚举
 * <p>
 * 定义了系统支持的各种AI模型类型，包括聊天模型、向量模型、图像模型等
 *
 * @author caoyuan
 * @date 2024/4/15
 */
@Getter
@RequiredArgsConstructor
public enum ModelTypeEnums {

	/**
	 * 聊天模型 - 用于对话和文本生成
	 */
	CHAT("Chat", "聊天"),

	/**
	 * 推理
	 */
	REASON("Reason", "推理"),

	/**
	 * 向量嵌入模型 - 用于文本向量化，支持语义搜索和相似度计算
	 */
	EMBEDDING("Embedding", "向量"),

	/**
	 * 重排序模型 - 用于对搜索结果进行语义重排序，提高相关性
	 */
	RERANKER("Reranker", "排序"),

	/**
	 * 图像生成模型 - 用于根据文本描述生成图像
	 */
	IMAGE("Image", "图片"),

	/**
	 * 视觉理解模型 - 用于图像识别、分析和理解
	 */
	VISION("Vision", "视觉理解"),

	/**
	 * 视频处理模型 - 用于视频内容的分析和理解
	 */
	VIDEO("Video", "视频"),

	/**
	 * 搜索操作标识，用于表示网络搜索功能
	 */
	SEARCH("Search", "网络搜索"),

	/**
	 * 音频处理模型 - 用于语音识别、合成和音频分析
	 */
	VOICE("Voice", "音频");

	/**
	 * 模型类型编码
	 */
	private final String type;

	/**
	 * 模型类型描述
	 */
	private final String desc;

}
