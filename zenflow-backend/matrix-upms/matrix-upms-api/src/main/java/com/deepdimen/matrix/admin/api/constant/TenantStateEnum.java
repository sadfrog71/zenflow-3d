package com.deepdimen.matrix.admin.api.constant;

/**
 * 租户状态
 *
 * @author caoyuan
 * @date 2023/11/15
 * <p>
 * "0" 正常 "9" 冻结
 */
public enum TenantStateEnum {

	/**
	 * 正常
	 */
	NORMAL("0", "正常"),

	/**
	 * 冻结
	 */
	FREEZE("9", "冻结");

	private final String code;

	private final String desc;

	TenantStateEnum(String code, String desc) {
		this.code = code;
		this.desc = desc;
	}

	public String getCode() {
		return code;
	}

	public String getDesc() {
		return desc;
	}

}
