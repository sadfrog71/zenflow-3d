package com.deepdimen.matrix.knowledge.support.constant;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * 函数调用枚举类 定义了系统中可用的函数调用服务
 *
 * @author caoyuan
 * @date 2024/3/29
 * <p>
 * mockMathScoreService ，获取成绩情况 mockWeatherScoreService ，获取天气情况
 */
@Getter
@RequiredArgsConstructor
public enum FunctionCallEnums {

	/**
	 * 模拟数学成绩服务 - 用于获取数学成绩情况
	 */
	MOCK_MATH_SCORE_SERVICE("mockMathScoreService", "获取成绩情况");

	/**
	 * 函数代码标识
	 */
	private final String code;

	/**
	 * 函数描述信息
	 */
	private final String desc;

}
