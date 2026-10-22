package com.deepdimen.matrix.knowledge.config.properties;

import com.deepdimen.matrix.knowledge.support.constant.ModelSupportEnums;
import lombok.Data;

/**
 * 默认模型配置属性。
 *
 * @author caoyuan
 * @date 2024/12/11
 */
@Data
public class DefaultModelProperties {

	/**
	 * 默认使用的AI模型。
	 */
	private ModelSupportEnums model = ModelSupportEnums.GLM_4V_FLASH;

	/**
	 * 嵌入模型。
	 */
	private ModelSupportEnums embedModel = ModelSupportEnums.GITEE_QWEN3_EMBEDDING_8B;

	/**
	 * 模型API基础地址。
	 */
	private String baseUrl = "https://open.bigmodel.cn/api/paas/v4";

	/**
	 * 嵌入服务基础URL。
	 */
	private String embedBaseUrl = "https://ai.gitee.com/v1";

	/**
	 * API密钥。生产环境必须通过外部配置注入，不提供代码内回退密钥。
	 */
	private String apiKey;

	/**
	 * 嵌入模型API密钥。生产环境必须通过外部配置注入。
	 */
	private String embedApiKey;

}
