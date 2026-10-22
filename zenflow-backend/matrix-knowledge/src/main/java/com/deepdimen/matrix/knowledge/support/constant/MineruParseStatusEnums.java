package com.deepdimen.matrix.knowledge.support.constant;

import lombok.Getter;

/**
 * Mineru 解析状态枚举
 * <p>
 * 定义 Mineru 文档解析服务返回的各种状态值，用于跟踪解析任务的处理进度
 *
 * @author caoyuan
 * @date 2025/10/20
 */
@Getter
public enum MineruParseStatusEnums {

	/**
	 * 处理中 - 解析任务正在进行中
	 */
	PROCESSING("processing", "处理中"),

	/**
	 * 解析完成 - 文档已成功完成解析
	 */
	DONE("done", "完成"),

	/**
	 * 解析失败 - 解析过程中发生错误
	 */
	FAILED("failed", "失败");

	/**
	 * 状态值（与外部系统返回的状态值保持一致）
	 */
	private final String status;

	/**
	 * 状态描述
	 */
	private final String desc;

	MineruParseStatusEnums(String status, String desc) {
		this.status = status;
		this.desc = desc;
	}

	/**
	 * 根据状态值获取对应的枚举实例
	 * @param status 状态值
	 * @return 对应的枚举实例，未找到时返回 null
	 */
	public static MineruParseStatusEnums getByStatus(String status) {
		for (MineruParseStatusEnums value : MineruParseStatusEnums.values()) {
			if (value.status.equals(status)) {
				return value;
			}
		}
		return null;
	}

}
