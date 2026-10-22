package com.deepdimen.matrix.knowledge.dto;

import lombok.Data;

import java.util.List;

/**
 * AI数据表数据传输对象
 * <p>
 * 用于传输数据库表结构信息，包含表名、表注释以及字段列表
 *
 * @author caoyuan
 * @date 2025/3/27
 */
@Data
public class AiDataTableDTO {

	/** 数据库类型 */
	private String dbType;

	/**
	 * 数据表名称
	 */
	private String tableName;

	/**
	 * 数据表注释
	 */
	private String tableComment;

	/**
	 * 表字段列表
	 */
	private List<AiTableField> fields;

	/**
	 * AI表字段信息
	 * <p>
	 * 封装数据表字段的详细信息
	 */
	@Data
	public static class AiTableField {

		/**
		 * 字段名称
		 */
		private String fieldName;

		/**
		 * 字段注释
		 */
		private String fieldComment;

		/**
		 * 数据库类型 如：VARCHAR、INT、DATETIME等
		 */
		private String dbType;

		/**
		 * 字段类型 映射的Java类型，如：String、Integer、Date等
		 */
		private String fieldType;

	}

}
