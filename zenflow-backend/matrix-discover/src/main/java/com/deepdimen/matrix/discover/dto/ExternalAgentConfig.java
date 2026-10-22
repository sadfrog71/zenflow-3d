package com.deepdimen.matrix.discover.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 外部智能体接入配置，承载在 ai_agent.config JSON 的 external 子节点。 仅 source=external 的智能体使用，本地智能体不写入此节点。
 *
 * <p>
 * authValue 写入数据库前由 Service 层 AES 加密；查询返回 VO 时脱敏为 ****。
 *
 * @author matrix
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "外部智能体接入配置（存于 ai_agent.config JSON 的 external 子节点）")
public class ExternalAgentConfig {

	@Schema(description = "接入端点 URL")
	private String endpointUrl;

	@Schema(description = "静态应用密钥 token（选填，数据库中 AES 加密存储；留空即公开嵌入）")
	private String authValue;

}
