package com.deepdimen.matrix.discover.dto;

import jakarta.validation.constraints.NotBlank;
import com.deepdimen.matrix.discover.dto.validation.CreateGroup;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.util.List;

/**
 * 智能体创建/更新请求DTO
 *
 * @author matrix
 * @date 2025-03-11
 */
@Data
@Schema(description = "智能体创建/更新请求")
public class AiAgentDTO {

	@Schema(description = "主键ID，更新时必填")
	private Long id;

	@NotBlank(message = "智能体名称不能为空", groups = CreateGroup.class)
	@Schema(description = "智能体名称")
	private String name;

	@Schema(description = "描述")
	private String description;

	@Schema(description = "智能体标签")
	private List<String> tags;

	@Schema(description = "标签精确过滤条件")
	private String tag;

	@Schema(description = "使用场景JSON数组")
	private String useScenes;

	@Schema(description = "操作说明富文本HTML")
	private String operationGuide;

	@Schema(description = "头像URL")
	private String avatar;

	/**
	 * 状态字段在更新接口中无效，状态变更请使用 /publish、/disable、/enable 专用接口
	 */
	@Schema(description = "状态: draft/published/disabled（更新时忽略，请使用专用状态接口）")
	private String status;

	@Schema(description = "类型: knowledge/comprehensive/dify/external，不传默认dify；source=external 时强制为 external")
	private String agentType;

	@Schema(description = "Dify应用模式: agent-chat/advanced-chat/chat/workflow/completion，不传默认agent-chat")
	private String difyMode;

	@Schema(description = "分类: operation/production/equipment/emergency/general")
	private String category;

	@Schema(description = "智能体配置JSON")
	private String config;

	@Schema(description = "是否在市场展示")
	private Integer marketVisible;

	@Schema(description = "市场展示配置JSON")
	private String marketConfig;

	@Schema(description = "排序权重")
	private Integer sort;

	@Schema(description = "最大并发数")
	private Integer maxConcurrency;

	@Schema(description = "每分钟限流数")
	private Integer rateLimitPerMin;

	@Schema(description = "权限: public/restricted")
	private String permission;

	@Schema(description = "来源: local/external")
	private String source;

	@Schema(description = "外部智能体接入端点 URL（仅 source=external 必填，存于 config JSON）")
	private String endpointUrl;

	@Schema(description = "外部智能体静态应用密钥 token（仅 source=external 选填，存于 config JSON，AES 加密存储；查询返回时脱敏为 ****；留空即公开嵌入）")
	private String authValue;

}
