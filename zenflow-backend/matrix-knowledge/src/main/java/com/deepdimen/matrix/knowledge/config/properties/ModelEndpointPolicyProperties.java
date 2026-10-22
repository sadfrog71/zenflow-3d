package com.deepdimen.matrix.knowledge.config.properties;

import java.util.ArrayList;
import java.util.List;

import lombok.Data;

/**
 * 模型服务出站地址安全策略。
 */
@Data
public class ModelEndpointPolicyProperties {

	/**
	 * 允许访问的内网主机名或 IP，仅用于明确部署的 Ollama/私有模型服务。
	 * 默认为空，避免租户配置被用于访问平台内网或云元数据地址。
	 */
	private List<String> allowedPrivateHosts = new ArrayList<>();

}
