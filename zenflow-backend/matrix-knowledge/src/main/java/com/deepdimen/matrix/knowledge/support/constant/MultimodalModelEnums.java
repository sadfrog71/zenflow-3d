package com.deepdimen.matrix.knowledge.support.constant;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * 多模态嵌入模型枚举类
 * <p>
 * 定义了系统支持的多模态嵌入模型配置，包括模型编码、描述和向量维度
 *
 * @author caoyuan
 * @date 2025/10/01
 */
@Getter
@RequiredArgsConstructor
public enum MultimodalModelEnums {

	/**
	 * 通义多模态嵌入Vision Plus - 支持文本和图像的嵌入模型(高维度版本)
	 */
	ALIYUN_TONGYI_EMBEDDING_VISION_PLUS("tongyi-embedding-vision-plus", "Aliyun", "通义多模态嵌入Vision Plus", 1152),

	/**
	 * 通义多模态嵌入Vision Flash - 支持文本和图像的嵌入模型(轻量版本)
	 */
	ALIYUN_TONGYI_EMBEDDING_VISION_FLASH("tongyi-embedding-vision-flash", "Aliyun", "通义多模态嵌入Vision Flash", 768),

	/**
	 * 阿里云多模态嵌入V1 - 支持文本和图像的嵌入模型
	 */
	ALIYUN_MULTIMODAL_V1("multimodal-embedding-v1", "Aliyun", "阿里云多模态嵌入V1", 1024),

	/**
	 * 豆包嵌入视觉模型 - 火山引擎ARK平台多模态向量化模型
	 */
	ARK_DOUBAO_EMBEDDING_VISION("doubao-embedding-vision-250615", "Ark", "豆包嵌入视觉模型", 2048);

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

	/**
	 * 向量维度
	 */
	private final Integer dimensions;

	/**
	 * 根据模型编码获取对应的枚举实例
	 * @param code 模型编码
	 * @return 对应的枚举实例，如果不存在则返回null
	 */
	public static MultimodalModelEnums fromCode(String code) {
		for (MultimodalModelEnums model : values()) {
			if (model.getCode().equals(code)) {
				return model;
			}
		}
		return null;
	}

}
