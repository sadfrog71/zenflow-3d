package com.deepdimen.matrix.discover.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.util.List;

/**
 * Dify DSL导入请求DTO
 *
 * @author matrix
 * @date 2026/04/02
 */
@Data
@Schema(description = "Dify DSL导入请求")
public class AiAgentImportDTO {

	@Schema(description = "智能体名称，不填则使用DSL中的应用名称")
	private String name;

	@Schema(description = "描述")
	private String description;

	@Schema(description = "分类: operation/production/equipment/emergency/general")
	private String category;

	@Schema(description = "智能体标签")
	private List<String> tags;

	@Schema(description = "Dify DSL内容(YAML格式)，与 difyAppId 二选一")
	private String dslContent;

	@Schema(description = "已存在的 Dify 应用 ID，与 dslContent 二选一")
	private String difyAppId;

}
