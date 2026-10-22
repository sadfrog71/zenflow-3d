package com.deepdimen.matrix.knowledge.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 用户活跃度趋势单条数据
 *
 * @author matrix
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "用户活跃度趋势项")
public class UserTrendVO {

	@Schema(description = "日期，格式 yyyy-MM-dd")
	private String date;

	@Schema(description = "当日对话次数")
	private Long count;

}
