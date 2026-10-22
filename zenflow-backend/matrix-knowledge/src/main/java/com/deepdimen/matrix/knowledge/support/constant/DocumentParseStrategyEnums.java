package com.deepdimen.matrix.knowledge.support.constant;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * 文档解析策略枚举
 * <p>
 * 定义了文档解析节点支持的解析策略，包括自动选择、标准解析和AI视觉识别
 *
 * @author caoyuan
 * @date 2025/01/25
 */
@Getter
@RequiredArgsConstructor
public enum DocumentParseStrategyEnums {

	/**
	 * 自动选择策略（推荐）
	 * <p>
	 * 优先使用标准解析器(Apache POI/PDFBox)进行快速解析 当标准解析失败时，自动降级到AI视觉识别模型
	 * 这是推荐的默认策略，可以在速度和准确性之间取得最佳平衡
	 */
	AUTO("auto", "自动选择"),

	/**
	 * 标准解析策略
	 * <p>
	 * 仅使用标准解析器(Apache POI处理Office文档，PDFBox处理PDF) 解析速度快，但对于影印版PDF或复杂格式的文档可能失败 适用于格式规范的电子文档
	 */
	STANDARD("standard", "标准解析"),

	/**
	 * AI视觉识别策略
	 * <p>
	 * 仅使用AI视觉模型进行文档识别，将文档转换为图片后进行OCR识别 解析速度较慢但准确率高，特别适合影印版PDF和扫描文档 当前仅支持PDF格式文档
	 */
	AI("ai", "AI视觉识别");

	/**
	 * 策略编码 用于前后端交互时的策略标识
	 */
	private final String strategy;

	/**
	 * 策略描述 用于UI展示和日志记录
	 */
	private final String desc;

	/**
	 * 根据策略编码获取对应的枚举值
	 * @param strategy 策略编码
	 * @return 对应的枚举值，如果未找到则返回AUTO
	 */
	public static DocumentParseStrategyEnums fromStrategy(String strategy) {
		if (strategy == null) {
			return AUTO;
		}
		for (DocumentParseStrategyEnums value : values()) {
			if (value.strategy.equals(strategy)) {
				return value;
			}
		}
		return AUTO;
	}

	/**
	 * 判断策略是否需要使用AI视觉模型
	 * @return true表示需要AI模型，false表示不需要
	 */
	public boolean requiresAI() {
		return this == AI || this == AUTO;
	}

	/**
	 * 判断策略是否允许标准解析
	 * @return true表示允许标准解析，false表示不允许
	 */
	public boolean allowsStandard() {
		return this == STANDARD || this == AUTO;
	}

}
