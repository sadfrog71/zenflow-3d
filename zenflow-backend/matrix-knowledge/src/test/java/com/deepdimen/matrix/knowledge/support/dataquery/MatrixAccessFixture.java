package com.deepdimen.matrix.knowledge.support.dataquery;

import org.springframework.jdbc.core.JdbcTemplate;

final class MatrixAccessFixture {

	static void organization(JdbcTemplate jdbc) {
		jdbc.execute(
				"CREATE TABLE sys_dept (dept_id BIGINT PRIMARY KEY,tenant_id BIGINT,name VARCHAR(80),parent_id BIGINT,del_flag CHAR(1))");
		jdbc.execute(
				"CREATE TABLE sys_role (role_id BIGINT PRIMARY KEY,tenant_id BIGINT,role_name VARCHAR(80),del_flag CHAR(1))");
		jdbc.execute("CREATE TABLE sys_user_dept (user_id BIGINT,dept_id BIGINT,tenant_id BIGINT)");
		jdbc.execute("CREATE TABLE sys_user_role (user_id BIGINT,role_id BIGINT,tenant_id BIGINT)");
		jdbc.update(
				"INSERT INTO sys_dept VALUES (100,1,'MATRIX Operations',0,'0'),(101,1,'MATRIX Child',100,'0'),(200,2,'Other tenant',0,'0'),(102,1,'Deleted',0,'1')");
		jdbc.update(
				"INSERT INTO sys_role VALUES (300,1,'MATRIX Reader','0'),(400,2,'Other tenant','0'),(301,1,'Deleted','1')");
		jdbc.update("INSERT INTO sys_user_dept VALUES (2,100,1),(3,101,1)");
		jdbc.update("INSERT INTO sys_user_role VALUES (2,300,1)");
	}

}
