package com.deepdimen.matrix.discover.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.Data;

import java.util.List;

/**
 * 智能体灰度配置请求DTO
 *
 * @author matrix
 * @date 2025-03-11
 */
@Data
@Schema(description = "智能体灰度配置请求")
public class AiAgentVersionGrayDTO {

	/**
	 * 灰度比例（0-100），按用户ID哈希取模命中
	 */
	@Min(value = 0, message = "灰度比例不能小于0")
	@Max(value = 100, message = "灰度比例不能大于100")
	@Schema(description = "灰度比例（0-100），按用户ID哈希取模命中")
	private Integer percent;

	/**
	 * 指定用户ID列表，精确匹配优先于percent
	 */
	@Schema(description = "指定用户ID列表，精确匹配优先于percent")
	private List<Long> userIds;

}
