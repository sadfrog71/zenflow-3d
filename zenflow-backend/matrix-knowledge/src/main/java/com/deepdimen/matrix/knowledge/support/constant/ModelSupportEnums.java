package com.deepdimen.matrix.knowledge.support.constant;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * 特殊支持的模型枚举类 定义了系统中需要特殊处理的各种AI模型
 *
 * @author caoyuan
 * @date 2024/4/15
 * <p>
 */
@Getter
@RequiredArgsConstructor
public enum ModelSupportEnums {

	/**
	 * 阿里云CosyVoice V1 - 语音合成模型
	 */
	ALIYUN_COSYVOICE_V1("cosyvoice-v1", "Aliyun", "Cosyvoice V1"),

	/**
	 * 阿里云多模态嵌入V1 - 支持文本和图像的嵌入模型
	 */
	ALIYUN_MULTIMODAL_V1("multimodal-embedding-v1", "Aliyun", "MULTIMODAL V1"),

	/**
	 * 智谱GLM-4V-Flash - 多模态视觉语言模型
	 */
	GLM_4V_FLASH("glm-4v-flash", "ChatGLM", "Glm 4v Flash"),

	/**
	 * Gitee提供的免费嵌入模型Qwen3-Embedding-8B
	 */
	GITEE_QWEN3_EMBEDDING_8B("Qwen3-Embedding-8B", "Gitee", "Gitee free embed"),

	/**
	 * SiliconFlow FLUX.1-schnell - 高速图像生成模型
	 */
	SILICONFLOW_KWAI_KOLORS_KOLORS("Kwai-Kolors/Kolors", "Siliconflow", "Black Forest Labs Flux 1 Schnell"),

	/**
	 * SiliconFlow SenseVoice Small - 语音识别模型
	 */
	SILICONFLOW_SENSE_VOICE_SMALL("FunAudioLLM/SenseVoiceSmall", "Siliconflow", "Fun Audio LLM Sense Voice Small"),

	/**
	 * 阿里云百炼 Qwen3 ASR Flash - 语音识别模型
	 */
	ALIYUN_QWEN3_ASR_FLASH("qwen3-asr-flash", "Aliyun", "Qwen3 ASR Flash"),

	/**
	 * 豆包大模型录音文件极速版 - 语音识别模型
	 */
	DOUBAO_BIGMODEL_ASR("bigmodel", "Doubao", "豆包语音识别大模型"),

	/**
	 * SiliconFlow Wan2.1-I2V - 图像转视频模型
	 */
	SILICONFLOW_WAN_2_1_I2V("Wan-AI/Wan2.1-I2V-14B-720P", "Siliconflow", "Wan 2.1 I2V"),

	/**
	 * SiliconFlow GPT-SoVITS - 语音克隆与合成模型
	 */
	SILICONFLOW_GPT_SOVITS("RVC-Boss/GPT-SoVITS", "Siliconflow", "RVC-Boss/GPT-SoVITS:alex"),

	/**
	 * OpenRouter Gemini 2.5 Flash Image Preview的API端点配置
	 * <p>
	 * 包含服务地址、提供方名称和模型描述信息
	 */
	OPEN_ROUTER_GEMINI_2_5_FLASH_IMAGE_PREVIEW("google/gemini-2.5-flash-image-preview:free", "OpenRouter",
			"Gemini 2.5 Flash Image Preview"),

	/**
	 * 豆包Seedream4模型ARK版本标识
	 * <p>
	 * 参数1: 模型唯一标识符 参数2: 平台名称(Ark) 参数3: 模型简称
	 */
	ARK_DOUBAO_SEEDREAM4("doubao-seedream-4-0-250828", "Ark", "doubao-seedream-4"),

	/**
	 * 豆包嵌入视觉模型配置
	 */
	ARK_DOUBAO_EMBEDDING_VISION("doubao-embedding-vision-250615", "Ark", "doubao-embedding-vision"),
	/**
	 * Jina Reader - 网页内容提取模型
	 */
	JINA_READER("jina-ai/jina-reader", "Jina", "Jina Reader"),

	/**
	 * 阿里云GTE-Rerank V2 - 文本重排序模型
	 */
	ALIYUN_GTE_RERANK_V2("gte-rerank-v2", "Aliyun", "gte-rerank-v2"),

	/**
	 * Gitee SenseVoice Small - 语音识别模型
	 */
	GITEE_SENSE_VOICE_SMALL("SenseVoiceSmall", "Gitee", "SenseVoiceSmall");

	/**
	 * 模型编码
	 */
	private final String code;

	/**
	 * 模型提供商
	 */
	private final String provider;

	/**
	 * 模型描述
	 */
	private final String desc;

}
