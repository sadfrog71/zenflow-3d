package com.deepdimen.matrix.common.neo4j.embedding;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.json.JsonMapper;
import dev.langchain4j.Internal;
import dev.langchain4j.store.embedding.filter.Filter;
import dev.langchain4j.store.embedding.filter.comparison.*;
import dev.langchain4j.store.embedding.filter.logical.And;
import dev.langchain4j.store.embedding.filter.logical.Not;
import dev.langchain4j.store.embedding.filter.logical.Or;
import org.neo4j.cypherdsl.core.*;
import org.neo4j.driver.internal.value.ListValue;
import org.neo4j.driver.internal.value.PointValue;

import java.time.OffsetDateTime;
import java.util.Map;

import static org.neo4j.cypherdsl.core.Cypher.*;

/**
 * Neo4j过滤器映射器
 * <p>
 * 内部工具类，用于将LangChain4j的Filter对象转换为Neo4j Cypher查询条件。 支持各种数据类型的转换和复杂逻辑条件的映射。
 * </p>
 * <p>
 * 主要功能：
 * <ul>
 * <li>Filter对象到Cypher条件的转换</li>
 * <li>支持比较运算符（等于、不等于、大于、小于等）</li>
 * <li>支持逻辑运算符（AND、OR、NOT）</li>
 * <li>特殊数据类型的处理（时间、地理位置、集合等）</li>
 * </ul>
 */
@Internal
class Neo4jFilterMapper {

	/** JSON对象映射器，用于数据类型转换 */
	static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

	/** 不支持的过滤器类型错误消息 */
	static final String UNSUPPORTED_FILTER_TYPE_ERROR = "Unsupported filter type: ";

	/**
	 * 将PointValue转换为Cypher Point函数调用
	 * @param value1 要转换的PointValue
	 * @return Cypher Point函数调用表达式
	 * @throws RuntimeException 当转换失败时抛出异常
	 */
	private static FunctionInvocation convertToPoint(PointValue value1) {
		try {
			String s = OBJECT_MAPPER.writeValueAsString(value1.asObject());
			Map<String, Object> map = new JsonMapper().readValue(s, Map.class);
			return Cypher.point(asExpression(map));
		}
		catch (Exception e) {
			throw new RuntimeException(e);
		}
	}

	/**
	 * 将Java对象转换为Cypher字面值表达式
	 * <p>
	 * Cypher.literalOf()方法不能处理所有数据类型，因此使用此方法来转换未处理的数据类型。
	 * 支持OffsetDateTime、PointValue、Map、List等特殊类型的转换。
	 * </p>
	 * @param value 要转换的值
	 * @return Cypher表达式
	 */
	static Expression toCypherLiteral(Object value) {
		if (value instanceof OffsetDateTime) {
			return Cypher.datetime(literalOf(value.toString()));
		}
		if (value instanceof PointValue pointValue) {
			return convertToPoint(pointValue);
		}
		if (value instanceof Map) {
			return mapOf(value);
		}
		if (value instanceof ListValue listValue) {
			return literalOf(listValue.asList());
		}

		// Other data types
		return literalOf(value);
	}

	/**
	 * Neo4j节点对象，用于构建Cypher查询条件中的节点引用 在生成过滤条件时，将基于此节点的属性进行比较操作
	 */
	private final Node node;

	/**
	 * 构造Neo4j过滤器映射器
	 * @param node Neo4j节点对象，不能为null
	 */
	Neo4jFilterMapper(Node node) {
		this.node = node;
	}

	/**
	 * 根据Filter对象生成对应的Cypher查询条件
	 * <p>
	 * 支持以下类型的过滤器：
	 * <ul>
	 * <li>比较操作：等于、不等于、大于、小于、大于等于、小于等于</li>
	 * <li>集合操作：包含于、不包含于</li>
	 * <li>逻辑操作：与、或、非</li>
	 * </ul>
	 * @param filter LangChain4j的Filter对象，包含过滤条件
	 * @return 转换后的Neo4j Cypher查询条件
	 * @throws UnsupportedOperationException 当遇到不支持的过滤器类型时抛出异常
	 */
	Condition getCondition(Filter filter) {
		if (filter instanceof IsEqualTo item) {
			Expression cypherLiteral = toCypherLiteral(item.key());
			Expression cypherLiteral1 = toCypherLiteral(item.comparisonValue());
			return node.property(cypherLiteral).eq(cypherLiteral1);
		}
		else if (filter instanceof IsNotEqualTo item) {
			Expression cypherLiteral = toCypherLiteral(item.key());
			Expression cypherLiteral1 = toCypherLiteral(item.comparisonValue());
			return node.property(cypherLiteral).isNotEqualTo(cypherLiteral1);
		}
		else if (filter instanceof IsGreaterThan item) {
			Expression cypherLiteral = toCypherLiteral(item.key());
			Expression cypherLiteral1 = toCypherLiteral(item.comparisonValue());
			return node.property(cypherLiteral).gt(cypherLiteral1);
		}
		else if (filter instanceof IsGreaterThanOrEqualTo item) {
			Expression cypherLiteral = toCypherLiteral(item.key());
			Expression cypherLiteral1 = toCypherLiteral(item.comparisonValue());
			return node.property(cypherLiteral).gte(cypherLiteral1);
		}
		else if (filter instanceof IsLessThan item) {
			Expression cypherLiteral = toCypherLiteral(item.key());
			Expression cypherLiteral1 = toCypherLiteral(item.comparisonValue());
			return node.property(cypherLiteral).lt(cypherLiteral1);
		}
		else if (filter instanceof IsLessThanOrEqualTo item) {
			Expression cypherLiteral = toCypherLiteral(item.key());
			Expression cypherLiteral1 = toCypherLiteral(item.comparisonValue());
			return node.property(cypherLiteral).lte(cypherLiteral1);
		}
		else if (filter instanceof IsIn item) {
			return mapIn(item);
		}
		else if (filter instanceof IsNotIn item) {
			return mapNotIn(item);
		}
		else if (filter instanceof And item) {
			return mapAnd(item);
		}
		else if (filter instanceof Not item) {
			return mapNot(item);
		}
		else if (filter instanceof Or item) {
			return mapOr(item);
		}
		else {
			throw new UnsupportedOperationException(UNSUPPORTED_FILTER_TYPE_ERROR + filter.getClass().getName());
		}
	}

	/**
	 * 处理"包含于"过滤器，将IsIn过滤器转换为Cypher查询条件
	 * <p>
	 * 检查节点属性值是否包含在指定的值列表中。 使用Neo4j的includesAny函数来实现包含判断。
	 * </p>
	 * @param filter IsIn过滤器对象，包含属性键和比较值列表
	 * @return 生成的Cypher条件表达式
	 */
	Condition mapIn(IsIn filter) {
		Expression cypherLiteral = toCypherLiteral(filter.key());
		Expression cypherLiteral1 = toCypherLiteral(filter.comparisonValues());
		return Cypher.includesAny(node.property(cypherLiteral), cypherLiteral1);
	}

	/**
	 * 处理"不包含于"过滤器，将IsNotIn过滤器转换为Cypher查询条件
	 * <p>
	 * 检查节点属性值是否不包含在指定的值列表中。 通过对包含条件进行逻辑非操作来实现不包含判断。
	 * </p>
	 * @param filter IsNotIn过滤器对象，包含属性键和比较值列表
	 * @return 生成的Cypher条件表达式（对包含条件的否定）
	 */
	Condition mapNotIn(IsNotIn filter) {
		Expression cypherLiteral = toCypherLiteral(filter.key());
		Expression cypherLiteral1 = toCypherLiteral(filter.comparisonValues());
		Condition condition1 = Cypher.includesAny(node.property(cypherLiteral), cypherLiteral1);
		return not(condition1);
	}

	/**
	 * 处理逻辑"与"操作，将And过滤器转换为Cypher查询条件
	 * <p>
	 * 递归处理左右两个子条件，然后使用and操作符连接它们。 只有当两个子条件都为真时，整个条件才为真。
	 * </p>
	 * @param filter And过滤器对象，包含左右两个子过滤器
	 * @return 生成的Cypher与条件表达式
	 */
	private Condition mapAnd(And filter) {
		Condition left = getCondition(filter.left());
		Condition right = getCondition(filter.right());
		return left.and(right);
	}

	/**
	 * 处理逻辑"或"操作，将Or过滤器转换为Cypher查询条件
	 * <p>
	 * 递归处理左右两个子条件，然后使用or操作符连接它们。 只要有一个子条件为真，整个条件就为真。
	 * </p>
	 * @param filter Or过滤器对象，包含左右两个子过滤器
	 * @return 生成的Cypher或条件表达式
	 */
	private Condition mapOr(Or filter) {
		Condition left = getCondition(filter.left());
		Condition right = getCondition(filter.right());
		return left.or(right);
	}

	/**
	 * 处理逻辑"非"操作，将Not过滤器转换为Cypher查询条件
	 * <p>
	 * 递归处理内部表达式，然后对其结果进行逻辑非操作。 当内部条件为假时，整个条件为真；反之亦然。
	 * </p>
	 * @param filter Not过滤器对象，包含要否定的子过滤器
	 * @return 生成的Cypher非条件表达式
	 */
	private Condition mapNot(Not filter) {
		Condition expression = getCondition(filter.expression());
		return not(expression);
	}

}
