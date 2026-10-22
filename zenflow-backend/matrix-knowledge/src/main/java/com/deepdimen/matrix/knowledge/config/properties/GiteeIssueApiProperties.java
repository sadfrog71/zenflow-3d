package com.deepdimen.matrix.knowledge.config.properties;

import lombok.Data;

/**
 * Gitee Issue查询配置
 *
 * @author caoyuan
 * @date 2024/9/21
 */
@Data
public class GiteeIssueApiProperties {

	/**
	 * 仓库所有者（企业、组织或个人）
	 */
	private String owner = "log4j";

	/**
	 * 仓库路径名称
	 */
	private String repo = "matrix";

	/**
	 * Issue状态：open-开放，progressing-进行中，closed-已关闭，rejected-已拒绝，all-所有
	 */
	private String state = "all";

	/**
	 * 当前页码
	 */
	private Integer page = 1;

	/**
	 * 每页数量（最大100）
	 */
	private Integer perPage = 100;

	/**
	 * 排序方向：asc-升序，desc-降序
	 */
	private String direction = "asc";

	/**
	 * 标签过滤（逗号分隔，如：bug,performance）
	 */
	private String labels;

	/**
	 * 排序依据：created-创建时间，updated_at-更新时间
	 */
	private String sort;

	/**
	 * 起始更新时间（ISO 8601格式）
	 */
	private String since;

	/**
	 * 计划开始日期（支持区间、小于、大于格式）
	 */
	private String schedule;

	/**
	 * 计划截止日期（格式同schedule）
	 */
	private String deadline;

	/**
	 * 任务创建时间（格式同schedule）
	 */
	private String createdAt;

	/**
	 * 任务完成时间（格式同schedule）
	 */
	private String finishedAt;

	/**
	 * 里程碑标题（none-无里程碑，*-所有里程碑）
	 */
	private String milestone;

	/**
	 * 负责人用户名（none-无负责人，*-所有负责人）
	 */
	private String assignee;

	/**
	 * 创建者用户名
	 */
	private String creator;

	/**
	 * 所属项目名称（none-无项目，*-所有项目）
	 */
	private String program;

}
