package com.deepdimen.matrix.knowledge.support.util;

import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Markdown文本处理工具类
 * <p>
 * 提供Markdown文本中图片链接的处理功能，主要用于为相对路径的图片添加域名前缀， 支持将本地图片路径转换为可访问的完整URL
 * </p>
 *
 * @author caoyuan
 * @date 2025/06/01
 */
public class MarkdownUtil {

	/**
	 * Markdown图片语法的正则表达式 匹配格式：![alt文本](图片URL)
	 */
	private static final String IMAGE_PATTERN = "!\\[([^\\]]*)\\]\\(([^\\)]+)\\)";

	private static final Pattern pattern = Pattern.compile(IMAGE_PATTERN);

	/**
	 * 为Markdown文本中的图片链接添加域名前缀
	 * <p>
	 * 该方法会扫描Markdown文本中的所有图片链接，对于相对路径的图片URL， 自动添加指定的域名前缀，使其成为可访问的完整URL。
	 * 已经是完整URL的图片链接不会被修改。
	 * </p>
	 * @param markdownText 原始Markdown文本
	 * @param prefix 要添加的域名前缀（如：https://example.com/）
	 * @return 处理后的Markdown文本，所有相对路径的图片都已添加域名前缀
	 */
	public static String addDomainPrefixToImages(String markdownText, String prefix) {
		Matcher matcher = pattern.matcher(markdownText);
		StringBuffer result = new StringBuffer();

		while (matcher.find()) {
			String altText = matcher.group(1); // 图片的alt文本（描述文字）
			String imageSrc = matcher.group(2); // 图片链接地址

			// 检查是否已经是完整URL（包含http://或https://）
			if (!isAbsoluteUrl(imageSrc)) {
				// 清理相对路径：移除开头的./或/，以及images/前缀
				// 避免路径重复拼接
				String cleanSrc = imageSrc.replaceFirst("^\\./|^/", "").replaceFirst("^images/", "");
				// 构建新的图片标签
				String newImageTag = String.format("![%s](%s%s)", altText, prefix, cleanSrc);
				matcher.appendReplacement(result, Matcher.quoteReplacement(newImageTag));
			}
			else {
				// 如果已经是完整URL，保持原样不做修改
				matcher.appendReplacement(result, Matcher.quoteReplacement(matcher.group(0)));
			}
		}

		matcher.appendTail(result);
		return result.toString();
	}

	/**
	 * 检查URL是否为绝对路径
	 * <p>
	 * 判断给定的URL是否为完整的网络地址，包括： - 以http://或https://开头的URL - 以//开头的协议相对URL
	 * </p>
	 * @param url 要检查的URL字符串
	 * @return 如果是绝对路径返回true，否则返回false
	 */
	private static boolean isAbsoluteUrl(String url) {
		return url.matches("^https?://.*") || url.matches("^//.*");
	}

	/**
	 * 替换Markdown文本中embedded协议的图片链接为实际可访问的URL
	 * @param markdownText 原始Markdown文本
	 * @param imageMapping 图片文件名映射：原始文件名 -> 上传后的文件名
	 * @param urlPrefix URL前缀
	 * @return 处理后的Markdown文本
	 */
	public static String replaceEmbeddedImages(String markdownText, Map<String, String> imageMapping,
			String urlPrefix) {
		if (markdownText == null || imageMapping == null || imageMapping.isEmpty()) {
			return markdownText;
		}
		Matcher matcher = pattern.matcher(markdownText);
		StringBuffer result = new StringBuffer();

		while (matcher.find()) {
			String altText = matcher.group(1);
			String imageSrc = matcher.group(2);

			if (imageSrc.startsWith("embedded:")) {
				String originalName = imageSrc.substring("embedded:".length());
				String uploadedName = imageMapping.get(originalName);
				if (uploadedName != null) {
					String newImageTag = String.format("![%s](%s%s)", altText, urlPrefix, uploadedName);
					matcher.appendReplacement(result, Matcher.quoteReplacement(newImageTag));
				}
				else {
					matcher.appendReplacement(result, Matcher.quoteReplacement(matcher.group(0)));
				}
			}
			else {
				matcher.appendReplacement(result, Matcher.quoteReplacement(matcher.group(0)));
			}
		}

		matcher.appendTail(result);
		return result.toString();
	}

}
