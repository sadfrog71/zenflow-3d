package com.deepdimen.matrix.knowledge.support.constant;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * 文档总结状态枚举
 * <p>
 * 定义了文档在AI总结处理过程中的各种状态，用于跟踪总结任务的执行情况
 *
 * @author caoyuan
 * @date 2024/5/21
 */
@Getter
@RequiredArgsConstructor
public enum SummaryStatusEnums {

	/**
	 * 未总结 - 文档尚未进行总结处理
	 */
	UNSUMMARY("0", "未总结"),

	/**
	 * 已总结 - 文档已成功完成AI总结
	 */
	SUMMARYED("1", "已总结"),

	/**
	 * 总结中 - 正在使用AI模型生成文档总结
	 */
	SUMMARYING("2", "总结中"),

	/**
	 * 总结失败 - AI总结过程中发生错误
	 */
	FAILED("9", "失败");

	/**
	 * 总结状态代码
	 */
	private final String status;

	/**
	 * 总结状态描述
	 */
	private final String desc;

}
