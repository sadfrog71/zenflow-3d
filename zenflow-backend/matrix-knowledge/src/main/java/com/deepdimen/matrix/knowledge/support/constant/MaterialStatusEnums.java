package com.deepdimen.matrix.knowledge.support.constant;

/**
 * 物料状态枚举类 定义了物料处理过程中可能的状态
 *
 * @author caoyuan
 * @date 2025/05/08
 */
public enum MaterialStatusEnums {

	/**
	 * 成功 - 物料处理成功
	 */
	SUCCEED("Succeed", "成功"),

	/**
	 * 排队中 - 物料在处理队列中等待
	 */
	IN_QUEUE("InQueue", "排队中"),

	/**
	 * 处理中 - 物料正在被处理
	 */
	IN_PROGRESS("InProgress", "处理中"),

	/**
	 * 失败 - 物料处理失败
	 */
	FAILED("Failed", "失败");

	/**
	 * 状态标识
	 */
	private final String status;

	/**
	 * 状态描述
	 */
	private final String desc;

	MaterialStatusEnums(String status, String desc) {
		this.status = status;
		this.desc = desc;
	}

	public String getStatus() {
		return status;
	}

	public String getDesc() {
		return desc;
	}

}
