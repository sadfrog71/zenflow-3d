package com.deepdimen.matrix.knowledge.support.constant;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * 小米IoT服务枚举类 定义了小米IoT设备控制相关的服务和操作
 *
 * @author caoyuan
 * @date 2024/12/22
 * <p>
 * 0 switch turn_off 1 switch turn_on
 */
@RequiredArgsConstructor
public enum MiIotServiceEnums {

	/**
	 * 开关关闭 - 控制设备关闭
	 */
	SWITCH_TURN_OFF(0, "switch", "turn_off"),

	/**
	 * 开关开启 - 控制设备开启
	 */
	SWITCH_TURN_ON(1, "switch", "turn_on");

	/**
	 * 操作代码
	 */
	private final int code;

	/**
	 * 服务域
	 */
	@Getter
	private final String domain;

	/**
	 * 服务名称
	 */
	@Getter
	private final String service;

	/**
	 * 根据操作代码获取对应的枚举
	 * @param code 操作代码
	 * @return 对应的枚举，如果未找到则返回null
	 */
	public static MiIotServiceEnums getEnumByCode(int code) {
		for (MiIotServiceEnums value : MiIotServiceEnums.values()) {
			if (value.code == code) {
				return value;
			}
		}
		return null;
	}

}
