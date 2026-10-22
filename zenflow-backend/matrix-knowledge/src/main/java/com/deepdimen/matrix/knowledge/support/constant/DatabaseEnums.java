package com.deepdimen.matrix.knowledge.support.constant;

import lombok.AllArgsConstructor;

/**
 * 数据库枚举类
 *
 * @author caoyuan
 * @date 2025/09/22
 */
@AllArgsConstructor
public enum DatabaseEnums {

	/**
	 * MySQL数据库类型标识及驱动类名
	 */
	MYSQL("mysql", "com.mysql.cj.jdbc.Driver"),

	/**
	 * PostgreSQL数据库类型常量，包含标识符和驱动类名
	 */
	POSTGRESQL("postgresql", "org.postgresql.Driver"),
	/**
	 * Oracle数据库类型标识及驱动类名
	 */
	ORACLE("oracle", "oracle.jdbc.OracleDriver"),
	/**
	 * SQL Server数据库类型标识及对应驱动类名
	 */
	SQLSERVER("sqlserver", "com.microsoft.sqlserver.jdbc.SQLServerDriver"), DB2("db2", "com.ibm.db2.jcc.DB2Driver"),
	/**
	 * SQLITE数据库类型标识及驱动类名
	 */
	SQLITE("sqlite", "org.sqlite.JDBC"),
	/**
	 * MariaDB数据库类型标识及驱动类名
	 */
	MARIADB("mariadb", "org.mariadb.jdbc.Driver"),
	/**
	 * DM数据库类型标识，对应驱动类为dm.jdbc.driver.DmDriver
	 */
	DM("dm", "dm.jdbc.driver.DmDriver"),

	/**
	 * TDENGINE 数据库类型标识及对应的 RestfulDriver 驱动类名
	 */
	TDENGINE("tdengine", "com.taosdata.jdbc.rs.RestfulDriver"),

	/**
	 * ClickHouse数据库类型标识及驱动类名
	 */
	CLICKHOUSE("clickhouse", "com.clickhouse.jdbc.Driver"),

	/**
	 * 数据库类型：kingbase，驱动类：com.kingbase8.Driver
	 */
	KINGBASE("kingbase", "com.kingbase8.Driver");

	private String dbType;

	private String driverClassName;

	/**
	 * 根据数据库类型获取驱动类名
	 * @param dbType 数据库类型，不区分大小写
	 * @return 对应的驱动类名，未找到时返回null
	 */
	public static String getDriverClassNameByDbType(String dbType) {
		for (DatabaseEnums databaseEnum : DatabaseEnums.values()) {
			if (databaseEnum.dbType.equalsIgnoreCase(dbType)) {
				return databaseEnum.driverClassName;
			}
		}
		throw new IllegalArgumentException(dbType);
	}

}
