package com.deepdimen.matrix.knowledge.support.util;

import com.deepdimen.matrix.knowledge.dto.FunctionFieldDTO;
import com.deepdimen.matrix.knowledge.support.annotation.FieldPrompt;
import com.deepdimen.matrix.knowledge.support.constant.FunctionTypeEnum;
import dev.langchain4j.internal.JsonSchemaElementUtils;
import dev.langchain4j.model.chat.request.json.JsonObjectSchema;
import dev.langchain4j.model.chat.request.json.JsonSchemaElement;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 工具规范生成工具类
 * <p>
 * 用于根据函数字段定义或Java反射字段生成JSON Schema规范， 主要用于AI工具调用时的参数定义和验证
 * </p>
 *
 * @author caoyuan
 * @date 2024/10/14
 */
public class ToolSpecificationsUtils {

	/**
	 * 私有构造函数，防止实例化
	 */
	private ToolSpecificationsUtils() {
	}

	/**
	 * 使用函数字段DTO列表构建JSON Schema
	 * <p>
	 * 根据提供的字段定义列表，生成符合JSON Schema规范的对象结构， 用于描述AI工具函数的参数结构
	 * </p>
	 * @param functionFieldDTOS 函数字段定义列表，包含字段名称、类型、是否必填等信息
	 * @return 构建好的JSON Schema对象，如果没有属性则返回null
	 */
	public static JsonObjectSchema parametersFrom(List<FunctionFieldDTO> functionFieldDTOS) {

		Map<String, JsonSchemaElement> properties = new LinkedHashMap<>();
		List<String> required = new ArrayList<>();

		// 用于跟踪已访问的类，防止循环引用
		Map<Class<?>, JsonSchemaElementUtils.VisitedClassMetadata> visited = new LinkedHashMap<>();

		for (FunctionFieldDTO field : functionFieldDTOS) {
			// 将每个字段转换为JSON Schema元素
			properties.put(field.getAttrName(), jsonSchemaElementFrom(field, visited));
			// 收集必填字段
			if (field.isFormRequired()) {
				required.add(field.getAttrName());
			}
		}

		// 处理递归定义
		Map<String, JsonSchemaElement> definitions = new LinkedHashMap<>();
		visited.forEach((clazz, visitedClassMetadata) -> {
			if (visitedClassMetadata.recursionDetected) {
				definitions.put(visitedClassMetadata.reference, visitedClassMetadata.jsonSchemaElement);
			}
		});

		if (properties.isEmpty()) {
			return null;
		}

		// 构建最终的JSON Schema对象
		return JsonObjectSchema.builder()
			.addProperties(properties)
			.required(required)
			.definitions(definitions.isEmpty() ? null : definitions)
			.build();
	}

	/**
	 * 使用Java反射字段数组构建JSON Schema
	 * <p>
	 * 通过反射获取的字段信息，结合@FieldPrompt注解，生成JSON Schema。 只有标注了@FieldPrompt注解的字段才会被处理
	 * </p>
	 * @param fields Java反射获取的字段数组
	 * @return 构建好的JSON Schema对象，如果没有属性则返回null
	 */
	public static JsonObjectSchema parametersFrom(Field[] fields) {

		Map<String, JsonSchemaElement> properties = new LinkedHashMap<>();
		List<String> required = new ArrayList<>();

		// 用于跟踪已访问的类，防止循环引用
		Map<Class<?>, JsonSchemaElementUtils.VisitedClassMetadata> visited = new LinkedHashMap<>();

		for (Field field : fields) {
			// 获取字段的@FieldPrompt注解
			FieldPrompt fieldPrompt = field.getAnnotation(FieldPrompt.class);

			// 跳过没有@FieldPrompt注解的字段
			if (fieldPrompt == null) {
				continue;
			}

			boolean isRequired = fieldPrompt.required();

			// 将字段转换为JSON Schema元素
			properties.put(field.getName(), jsonSchemaElementFrom(field, fieldPrompt.value(), visited));
			if (isRequired) {
				required.add(field.getName());
			}
		}

		// 处理递归定义
		Map<String, JsonSchemaElement> definitions = new LinkedHashMap<>();
		visited.forEach((clazz, visitedClassMetadata) -> {
			if (visitedClassMetadata.recursionDetected) {
				definitions.put(visitedClassMetadata.reference, visitedClassMetadata.jsonSchemaElement);
			}
		});

		if (properties.isEmpty()) {
			return null;
		}

		// 构建最终的JSON Schema对象
		return JsonObjectSchema.builder()
			.addProperties(properties)
			.required(required)
			.definitions(definitions.isEmpty() ? null : definitions)
			.build();
	}

	/**
	 * 将函数字段DTO转换为JSON Schema元素
	 * @param field 函数字段定义
	 * @param visited 已访问类的元数据映射，用于处理循环引用
	 * @return JSON Schema元素
	 */
	private static JsonSchemaElement jsonSchemaElementFrom(FunctionFieldDTO field,
			Map<Class<?>, JsonSchemaElementUtils.VisitedClassMetadata> visited) {
		// 根据表单类型获取对应的低代码类型
		FunctionTypeEnum fieldTypeEnum = FunctionTypeEnum.fromInput(field.getFormType());
		return JsonSchemaElementUtils.jsonSchemaElementFrom(fieldTypeEnum.getLowCodeType(), String.class,
				field.getFieldComment(), false, visited);
	}

	/**
	 * 将Java反射字段转换为JSON Schema元素
	 * @param field 反射字段对象
	 * @param description 字段描述（来自@FieldPrompt注解）
	 * @param visited 已访问类的元数据映射，用于处理循环引用
	 * @return JSON Schema元素
	 */
	private static JsonSchemaElement jsonSchemaElementFrom(Field field, String description,
			Map<Class<?>, JsonSchemaElementUtils.VisitedClassMetadata> visited) {
		// 使用字段的类型和泛型信息创建JSON Schema元素
		return JsonSchemaElementUtils.jsonSchemaElementFrom(field.getType(), field.getGenericType(), description, false,
				visited);
	}

}
