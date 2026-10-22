package com.deepdimen.matrix.knowledge.support.util;

import io.github.haibiiin.json.repair.JSONRepair;
import io.github.haibiiin.json.repair.JSONRepairConfig;
import lombok.experimental.UtilityClass;

/**
 * JSON修复工具类
 *
 * @author caoyuan
 * @date 2025/07/21
 */
@UtilityClass
public class JSONRepairUtil {

	// 初始化JSON修复配置
	private final JSONRepairConfig CONFIG = new JSONRepairConfig();

	static {
		CONFIG.enableExtractJSON();
	}

	private final JSONRepair repair = new JSONRepair(CONFIG);

	/**
	 * 修复JSON字符串
	 * @param inputJson 需要修复的JSON字符串
	 * @return 修复后的JSON字符串
	 */
	public String repair(String inputJson) {
		return repair.handle(inputJson);
	}

}
