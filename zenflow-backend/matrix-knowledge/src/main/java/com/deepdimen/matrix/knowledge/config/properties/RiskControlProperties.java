package com.deepdimen.matrix.knowledge.config.properties;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;

/**
 * 风控配置
 *
 * @author caoyuan
 * @date 2024/3/26
 */
@Data
public class RiskControlProperties {

	/**
	 * 是否开启风控
	 */
	private boolean enabled = true;

	/**
	 * 允许调用的次数
	 */
	private int times = 20;

	/**
	 * 不限制的用户
	 */
	private List<String> noLimitUsernames = new ArrayList<>();

	/**
	 * 允许发起调用的IP
	 */
	private List<String> allowIps = new ArrayList<>();

	/**
	 * 拒绝发起调用的IP
	 */
	private List<String> denyIps = new ArrayList<>();

	/**
	 * 错误提示
	 */
	private String errorMsg = "请求次数超限";

	/**
	 * 是否开启敏感词过滤
	 */
	private boolean sensitiveWord = true;

	/**
	 * 敏感词提示
	 */
	private String sensitiveMsg = "您的输入包含敏感词汇，请重新输入";

}
