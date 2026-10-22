package com.deepdimen.matrix.knowledge.support.constant;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

import java.util.Objects;

/**
 * AI图表类型枚举
 * <p>
 * 定义了AI生成图表时支持的图表类型，用于数据可视化展示
 *
 * @author caoyuan
 * @date 2025/3/25
 */
@RequiredArgsConstructor
public enum AiChartTypeEnums {

	/**
	 * 折线图
	 * <p>
	 * 用于展示数据随时间或其他连续变量的变化趋势
	 */
	LINE("1", "line"),

	/**
	 * 饼图
	 * <p>
	 * 用于展示各部分占整体的比例关系，适合展示百分比数据
	 */
	PIE("2", "pie"),

	/**
	 * 柱状图
	 * <p>
	 * 用于比较不同类别之间的数值大小，适合展示分类数据
	 */
	BAR("3", "bar");

	/**
	 * 图表类型代码
	 */
	@Getter
	private final String code;

	/**
	 * 图表类型描述
	 */
	@Getter
	private final String desc;

	/**
	 * 根据代码获取对应的图表类型枚举
	 * @param code 图表类型代码
	 * @return 对应的图表类型枚举，如果未找到返回null
	 */
	public static AiChartTypeEnums getEnumByCode(String code) {
		for (AiChartTypeEnums value : AiChartTypeEnums.values()) {
			if (Objects.equals(value.code, code)) {
				return value;
			}
		}
		return null;
	}

}
