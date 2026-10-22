package com.deepdimen.matrix.knowledge.support.flow.model.nodes;

import lombok.Data;

/**
 * AI 数据库节点 用于在工作流中执行数据库查询操作，支持连接不同的数据源
 *
 * @author caoyuan
 * @date 2025/03/03
 */
@Data
public class AiDbNode {

	/**
	 * 数据库连接ID 对应系统中配置的数据源标识
	 */
	private String dbId;

	/**
	 * SQL查询语句 支持SELECT、INSERT、UPDATE、DELETE等标准SQL操作
	 */
	private String sql;

}
