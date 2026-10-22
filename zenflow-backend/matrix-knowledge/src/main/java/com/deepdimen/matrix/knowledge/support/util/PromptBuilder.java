package com.deepdimen.matrix.knowledge.support.util;

import cn.hutool.core.date.DateUtil;
import org.anyline.entity.DataRow;
import org.anyline.entity.DataSet;
import org.springframework.ai.chat.prompt.PromptTemplate;
import org.springframework.core.io.ClassPathResource;

import java.util.List;
import java.util.Map;

import static com.deepdimen.matrix.knowledge.support.constant.AiPromptField.systemTime;

/**
 * 提示词构建器工具类
 * <p>
 * 提供提示词模板的渲染功能，支持从classpath加载模板文件并填充变量， 同时提供将数据转换为Markdown表格的功能
 * </p>
 *
 * @author caoyuan
 * @date 2024/9/27
 */
public class PromptBuilder {

	/**
	 * 渲染指定的提示词模板
	 * <p>
	 * 从classpath的prompts目录下加载模板文件，并使用提供的模型数据进行渲染
	 * </p>
	 * @param promptName 提示词模板文件名（不含路径）
	 * @param model 包含变量值的Map，用于替换模板中的占位符
	 * @return 渲染后的提示词字符串
	 */
	public static String render(String promptName, Map<String, Object> model) {
		// 从classpath加载提示词模板文件
		ClassPathResource classPathResource = new ClassPathResource("prompts/" + promptName);
		PromptTemplate promptTemplate = new PromptTemplate(classPathResource);
		return promptTemplate.render(model);
	}

	/**
	 * 使用默认参数渲染提示词模板
	 * <p>
	 * 自动添加系统时间作为默认变量，适用于不需要额外参数的简单模板
	 * </p>
	 * @param promptName 提示词模板文件名（不含路径）
	 * @return 渲染后的提示词字符串，已包含当前系统时间
	 */
	public static String render(String promptName) {
		// 使用当前系统时间作为默认参数
		return render(promptName, Map.of(systemTime, DateUtil.now()));
	}

	/**
	 * 将数据列表转换为Markdown表格格式
	 * <p>
	 * 根据第一行数据的键作为表头，将所有数据行格式化为Markdown表格。支持动态列数，自动处理空值
	 * </p>
	 * @param dataSet 包含表格数据的DataSet对象
	 * @return Markdown格式的表格字符串，如果列表为空则返回空字符串
	 */
	public static String toMarkdownTable(DataSet dataSet) {
		if (dataSet == null || dataSet.isEmpty() || dataSet.getRows().isEmpty())
			return "";

		// 获取表头（使用第一行数据的键集合）
		List<String> headers = dataSet.getRows().get(0).keys();

		// 构建Markdown表格
		StringBuilder sb = new StringBuilder();

		// 构建表头行
		sb.append("| ").append(String.join(" | ", headers)).append(" |\n");

		// 构建分隔行（每列都是---）
		sb.append("|").append(" --- |".repeat(headers.size())).append("\n");

		// 构建数据行
		for (DataRow row : dataSet.getRows()) {
			sb.append("| ");
			for (String header : headers) {
				// 使用getOrDefault处理可能的空值
				sb.append(row.getOrDefault(header, "")).append(" | ");
			}
			sb.append("\n");
		}

		return sb.toString();
	}

}
