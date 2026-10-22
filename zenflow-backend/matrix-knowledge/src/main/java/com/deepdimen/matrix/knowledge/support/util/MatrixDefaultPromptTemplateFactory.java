package com.deepdimen.matrix.knowledge.support.util;

import dev.langchain4j.spi.prompt.PromptTemplateFactory;

import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static dev.langchain4j.internal.Exceptions.illegalArgument;
import static dev.langchain4j.internal.ValidationUtils.ensureNotBlank;

/**
 * 自定义提示词模板工厂
 * <p>
 * 实现PromptTemplateFactory接口，提供自定义的提示词模板处理功能。 支持使用双花括号{{variable}}语法的变量替换
 * </p>
 *
 * @author caoyuan
 * @date 2024/10/14
 */
public class MatrixDefaultPromptTemplateFactory implements PromptTemplateFactory {

	/**
	 * 创建默认模板实例
	 * @param input 包含模板字符串的输入对象
	 * @return 默认模板实例
	 */
	@Override
	public MatrixDefaultPromptTemplateFactory.DefaultTemplate create(PromptTemplateFactory.Input input) {
		return new MatrixDefaultPromptTemplateFactory.DefaultTemplate(input.getTemplate());
	}

	/**
	 * 默认模板实现类
	 * <p>
	 * 支持双花括号变量语法的模板处理，例如：{{userName}}会被替换为实际的用户名
	 * </p>
	 */
	static class DefaultTemplate implements Template {

		/**
		 * 匹配双花括号变量的正则表达式 格式：{{variableName}}
		 */
		@SuppressWarnings("RegExpRedundantEscape")
		private static final Pattern VARIABLE_PATTERN = Pattern.compile("\\{\\{(.+?)\\}\\}");

		/**
		 * 模板字符串
		 */
		private final String template;

		/**
		 * 从模板中提取的所有变量名集合
		 */
		private final Set<String> allVariables;

		/**
		 * 构造函数
		 * @param template 模板字符串，不能为空
		 */
		public DefaultTemplate(String template) {
			this.template = ensureNotBlank(template, "template");
			this.allVariables = extractVariables(template);
		}

		/**
		 * 从模板字符串中提取所有变量名
		 * @param template 模板字符串
		 * @return 包含所有变量名的Set集合
		 */
		private static Set<String> extractVariables(String template) {
			Set<String> variables = new HashSet<>();
			Matcher matcher = VARIABLE_PATTERN.matcher(template);
			while (matcher.find()) {
				// 提取花括号中的变量名
				variables.add(matcher.group(1));
			}
			return variables;
		}

		/**
		 * 渲染模板，将变量替换为实际值
		 * @param variables 变量名到值的映射
		 * @return 渲染后的字符串
		 */
		public String render(Map<String, Object> variables) {
			String result = template;
			// 遍历所有变量进行替换
			for (Map.Entry<String, Object> entry : variables.entrySet()) {
				result = replaceAll(result, entry.getKey(), entry.getValue());
			}

			return result;
		}

		/**
		 * 替换模板中的单个变量
		 * @param template 模板字符串
		 * @param variable 变量名
		 * @param value 变量值
		 * @return 替换后的字符串
		 * @throws IllegalArgumentException 如果变量值为null
		 */
		private static String replaceAll(String template, String variable, Object value) {
			if (value == null || value.toString() == null) {
				throw illegalArgument("Value for the variable '%s' is null", variable);
			}
			// 将{{variableName}}替换为实际值
			return template.replace(inDoubleCurlyBrackets(variable), value.toString());
		}

		/**
		 * 将变量名包装在双花括号中
		 * @param variable 变量名
		 * @return 带双花括号的变量，如：{{variableName}}
		 */
		private static String inDoubleCurlyBrackets(String variable) {
			return "{{" + variable + "}}";
		}

	}

}
