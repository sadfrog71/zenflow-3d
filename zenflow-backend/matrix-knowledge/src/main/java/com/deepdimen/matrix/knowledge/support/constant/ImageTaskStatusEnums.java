package com.deepdimen.matrix.knowledge.support.constant;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * 图像任务状态枚举类 定义了图像处理任务的各种状态
 *
 * @author caoyuan
 * @date 2024/10/12
 * <p>
 * SUCCESSED：任务执行成功
 * <p>
 * FAILED：任务执行失败
 * <p>
 * CANCELED：任务被取消
 * <p>
 * PENDING：任务排队中
 * <p>
 * SUSPENDED：任务挂起
 * <p>
 * RUNNING：任务处理中
 */
@Getter
@RequiredArgsConstructor
public enum ImageTaskStatusEnums {

	/**
	 * 任务执行成功
	 */
	SUCCESSED("SUCCESSED", "任务执行成功"),

	/**
	 * 任务执行失败
	 */
	FAILED("FAILED", "任务执行失败");

	/**
	 * 状态代码
	 */
	private final String code;

	/**
	 * 状态描述
	 */
	private final String desc;

}
