package com.deepdimen.matrix.common.neo4j.embedding;

import dev.langchain4j.Internal;
import org.neo4j.cypherdsl.core.FunctionInvocation;

/**
 * Neo4j工具类
 * <p>
 * 内部工具类，提供Neo4j相关的辅助功能和工具方法。 主要用于创建Neo4j Cypher查询中的函数定义等操作。
 * </p>
 *
 */
@Internal
class Neo4jUtils {

	/**
	 * 私有构造函数，防止实例化工具类
	 * @throws InstantiationException 当试图实例化时抛出异常
	 */
	private Neo4jUtils() throws InstantiationException {
		throw new InstantiationException("Cannot instantiate utility class.");
	}

	/**
	 * 创建函数定义对象
	 * <p>
	 * 根据给定的函数名称创建一个FunctionDefinition对象， 用于在Cypher查询中定义自定义函数。
	 * </p>
	 * @param functionName 函数名称，不能为null或空字符串
	 * @return 函数定义对象，包含指定的函数名称
	 */
	static FunctionInvocation.FunctionDefinition functionDef(String functionName) {
		return new FunctionInvocation.FunctionDefinition() {

			/**
			 * 获取函数的实现名称
			 * @return 函数的实际名称
			 */
			@Override
			public String getImplementationName() {
				return functionName;
			}

			/**
			 * 判断该函数是否为聚合函数
			 * @return 始终返回false，表示这不是聚合函数
			 */
			@Override
			public boolean isAggregate() {
				return false;
			}
		};
	}

}
