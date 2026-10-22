package com.deepdimen.matrix.knowledge.support.constant;

import lombok.Getter;

/**
 * 文件状态枚举
 *
 * @author caoyuan
 * @date 2025/10/16
 */
@Getter
public enum FileStatusEnums {

	PENDING("0", "待处理"), PROCESSING("1", "处理中"), COMPLETED("2", "已完成"), FAILED("9", "失败");

	private final String code;

	private final String desc;

	FileStatusEnums(String code, String desc) {
		this.code = code;
		this.desc = desc;
	}

	/**
	 * 根据状态码获取对应的枚举实例
	 * @param code 状态码
	 * @return 对应的枚举实例，未找到时返回null
	 */
	public static FileStatusEnums getEnumByCode(String code) {
		for (FileStatusEnums value : FileStatusEnums.values()) {
			if (value.code.equals(code)) {
				return value;
			}
		}
		return null;
	}

}
