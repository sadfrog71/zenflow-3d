package com.deepdimen.matrix.knowledge.support.handler.parse;

import dev.langchain4j.data.document.Document;
import dev.langchain4j.data.document.DocumentSplitter;
import dev.langchain4j.data.document.splitter.DocumentSplitters;
import dev.langchain4j.data.segment.TextSegment;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * HTML表格感知切片器
 * <p>
 * 解析器(TorchV)将docx转为内容时，表格输出为HTML的&lt;table&gt;结构。普通递归切片器不感知表格语义，
 * 会把长表格从&lt;tr&gt;之间按字符切断，后半段只剩裸&lt;td&gt;片段、丢失表名与表头，导致向量召回不完整。
 *
 * 处理逻辑： 1. 将输入按&lt;table&gt;...&lt;/table&gt;块与非表格块拆分 2. 非表格块沿用langchain4j递归切片器，行为与原实现一致
 * 3. 表格块作为完整语义单元处理：整表(含前置表名标题)不超过表格上限时整张保留在一个切片，保证一次召回拿到全部行；
 * 仅当超过表格上限(病态超大表)时才按&lt;tr&gt;分段，每段补回表名标题、开标签与表头行并补全闭合标签，避免embedding输入被截断。
 *
 * @author claude
 * @date 2026/6/4
 */
public class HtmlTableAwareSplitter implements DocumentSplitter {

	private static final Pattern TABLE_PATTERN = Pattern.compile("<table[\\s>].*?</table>",
			Pattern.DOTALL | Pattern.CASE_INSENSITIVE);

	private static final Pattern TR_PATTERN = Pattern.compile("<tr[\\s>].*?</tr>",
			Pattern.DOTALL | Pattern.CASE_INSENSITIVE);

	/**
	 * 表格整体保留的字符上限。表格是完整语义单元，应尽量整张放入一个切片以保证一次召回拿到全部行；
	 * 仅当超过此上限(病态超大表)时才按行分段，以免embedding输入被模型截断。 取值贴近主流embedding模型的安全输入长度。
	 */
	private static final int MAX_TABLE_SIZE_IN_CHARS = 8000;

	private final int maxSegmentSizeInChars;

	private final int maxTableSizeInChars;

	private final DocumentSplitter recursiveSplitter;

	public HtmlTableAwareSplitter(int maxSegmentSizeInChars, int maxOverlapSizeInChars) {
		this.maxSegmentSizeInChars = maxSegmentSizeInChars;
		// 表格阈值不小于普通片段阈值，且至少达到表格保留上限
		this.maxTableSizeInChars = Math.max(maxSegmentSizeInChars, MAX_TABLE_SIZE_IN_CHARS);
		this.recursiveSplitter = DocumentSplitters.recursive(maxSegmentSizeInChars, maxOverlapSizeInChars);
	}

	@Override
	public List<TextSegment> split(Document document) {
		String text = document.text();
		List<TextSegment> result = new ArrayList<>();
		if (text == null || text.isEmpty()) {
			return result;
		}

		Matcher matcher = TABLE_PATTERN.matcher(text);
		int cursor = 0;
		while (matcher.find()) {
			// 表格之前的普通文本(剥离归属于本表的前置表名标题后)交给递归切片器
			String before = text.substring(cursor, matcher.start());
			String caption = takeCaption(before);
			String beforeWithoutCaption = caption == null ? before
					: before.substring(0, before.length() - caption.length());
			flushText(beforeWithoutCaption, result);

			splitTable(matcher.group(), caption, result);
			cursor = matcher.end();
		}
		// 处理最后一个表格之后的剩余普通文本
		flushText(text.substring(cursor), result);
		return result;
	}

	/**
	 * 从表格前置文本的尾部取出紧邻表格的最后一行非空文本(通常为表名标题，如"表4 ...对照表：")。
	 * <p>
	 * 该行在原文本就紧贴表格，吸附进表格切片可使表名与表格数据同处片段，提升按表名检索的召回；末行为空或不存在时返回null。
	 * @param before 表格之前的文本
	 * @return 紧邻表格的最后一行文本(含其前导换行)，无可吸附时返回null
	 */
	private String takeCaption(String before) {
		if (before == null || before.isEmpty()) {
			return null;
		}
		int trimEnd = before.length();
		while (trimEnd > 0 && (before.charAt(trimEnd - 1) == '\n' || before.charAt(trimEnd - 1) == '\r')) {
			trimEnd--;
		}
		if (trimEnd == 0) {
			return null;
		}
		int lastBreak = before.lastIndexOf('\n', trimEnd - 1);
		String lastLine = before.substring(lastBreak + 1, trimEnd);
		if (lastLine.trim().isEmpty()) {
			return null;
		}
		// 返回从该行起点到before末尾的子串(含尾部换行)，便于调用方据长度从before剥离
		return before.substring(lastBreak + 1);
	}

	/**
	 * 将一段非表格文本用递归切片器切分后追加到结果，空白内容忽略。
	 */
	private void flushText(String text, List<TextSegment> result) {
		if (text == null || text.trim().isEmpty()) {
			return;
		}
		for (TextSegment segment : recursiveSplitter.split(Document.from(text.trim()))) {
			result.add(segment);
		}
	}

	/**
	 * 切分单个HTML表格块，保证表格语义完整。
	 * <p>
	 * 整表(含表名)不超过片段大小时作为单个切片；否则按&lt;tr&gt;分段，每段补回表名、表格开标签与表头行、
	 * 并补全闭合标签。无法解析出&lt;tr&gt;时整体成片，不强行切分。
	 * @param tableHtml 完整的&lt;table&gt;...&lt;/table&gt;文本
	 * @param caption 表名标题，可为null
	 * @param result 切片结果收集器
	 */
	private void splitTable(String tableHtml, String caption, List<TextSegment> result) {
		String captionPrefix = caption == null ? "" : caption.trim() + "\n";
		String whole = captionPrefix + tableHtml;
		// 提取所有<tr>行
		List<String> rows = new ArrayList<>();
		Matcher trMatcher = TR_PATTERN.matcher(tableHtml);
		while (trMatcher.find()) {
			rows.add(trMatcher.group());
		}
		// 整表不超表格上限、或无法解析出多行数据，直接整体成片(保证一次召回拿到全表)
		if (whole.length() <= maxTableSizeInChars || rows.size() <= 1) {
			result.add(TextSegment.from(whole));
			return;
		}

		// 表格开标签(首个<tr>之前的部分)与表头行(首个<tr>)
		int firstTrStart = tableHtml.indexOf(rows.get(0));
		String openTags = tableHtml.substring(0, firstTrStart);
		String headerRow = rows.get(0);
		// 闭合标签按开标签实际结构生成，避免无<tbody>时凭空多出</tbody>导致标签不对称
		String closeTags = (openTags.toLowerCase().contains("<tbody") ? "\n</tbody>\n</table>" : "\n</table>");
		String prefix = captionPrefix + openTags + headerRow + "\n";

		StringBuilder current = new StringBuilder(prefix);
		boolean hasData = false;
		for (int idx = 1; idx < rows.size(); idx++) {
			String row = rows.get(idx);
			// 单行数据自身就超过表格上限：先冲掉已累积分段，再将该长行内容兜底切分后逐片补表头
			if (prefix.length() + row.length() + closeTags.length() > maxTableSizeInChars) {
				if (hasData) {
					emit(current, closeTags, result);
					current.setLength(0);
					current.append(prefix);
					hasData = false;
				}
				emitOversizedRow(row, prefix, closeTags, result);
				continue;
			}
			// 追加该行会超过表格上限，则先输出当前分段并重新起一段
			if (hasData && current.length() + row.length() + closeTags.length() > maxTableSizeInChars) {
				emit(current, closeTags, result);
				current.setLength(0);
				current.append(prefix);
				hasData = false;
			}
			current.append(row).append("\n");
			hasData = true;
		}
		if (hasData) {
			emit(current, closeTags, result);
		}
	}

	/**
	 * 输出一个表格片段：当前累积内容补上闭合标签后成片。
	 */
	private void emit(StringBuilder current, String closeTags, List<TextSegment> result) {
		result.add(TextSegment.from(current.toString() + closeTags));
	}

	/**
	 * 处理自身就超过片段上限的单个&lt;tr&gt;：用递归切片器将其切分为多段，每段补回表格前缀与闭合标签。
	 */
	private void emitOversizedRow(String row, String prefix, String closeTags, List<TextSegment> result) {
		for (TextSegment part : recursiveSplitter.split(Document.from(row))) {
			result.add(TextSegment.from(prefix + part.text() + "\n" + closeTags));
		}
	}

}
