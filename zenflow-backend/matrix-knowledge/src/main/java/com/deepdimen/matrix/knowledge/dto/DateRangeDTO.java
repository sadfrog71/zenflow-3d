package com.deepdimen.matrix.knowledge.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

/**
 * 日期范围查询参数数据传输对象
 * <p>
 * 用于Dashboard统计分析接口的时间范围查询条件
 *
 * @author matrix
 * @date 2025-01-17
 */
@Data
@Schema(description = "日期范围查询参数")
public class DateRangeDTO {

	/**
	 * 开始日期
	 * <p>
	 * 格式：yyyy-MM-dd，如：2025-01-01
	 */
	@Schema(description = "开始日期", example = "2025-01-01")
	private String startDate;

	/**
	 * 结束日期
	 * <p>
	 * 格式：yyyy-MM-dd，如：2025-01-31
	 */
	@Schema(description = "结束日期", example = "2025-01-31")
	private String endDate;

	/**
	 * 时间粒度
	 * <p>
	 * 支持按天(day)或按小时(hour)聚合，默认为day
	 */
	@Schema(description = "时间粒度", example = "day")
	private String interval = "day";

}
