package com.deepdimen.matrix.knowledge.support.constant;

/**
 * 文本分割器类型枚举
 * <p>
 * 定义了系统支持的各种文本分割策略，用于将长文本切分成适合处理的小块
 *
 * @author caoyuan
 * @date 2025/05/09
 */
public enum SplitterTypeEnums {

	/**
	 * 段落分割器 - 按照段落进行文本分割
	 */
	PARAGRAPH("paragraph", "段落分割器"),

	/**
	 * 行分割器 - 按照行进行文本分割
	 */
	LINE("line", "行分割器"),

	/**
	 * 句子分割器 - 按照句子进行文本分割
	 */
	SENTENCE("sentence", "句子分割器"),

	/**
	 * 单词分割器 - 按照单词进行文本分割
	 */
	WORD("word", "单词分割器"),

	/**
	 * 字符分割器 - 按照固定字符数进行文本分割
	 */
	CHARACTER("character", "字符分割器"),

	/**
	 * 正则表达式分割器 - 使用自定义正则表达式进行文本分割
	 */
	REGEX("regex", "正则表达式分割器"),

	/**
	 * 递归智能分割器 - 使用递归算法智能分割，保持语义完整性
	 */
	RECURSIVE("recursive", "递归智能分割器");

	/**
	 * 分割器类型代码
	 */
	private final String type;

	/**
	 * 分割器类型描述
	 */
	private final String desc;

	SplitterTypeEnums(String type, String desc) {
		this.type = type;
		this.desc = desc;
	}

	public String getType() {
		return type;
	}

}
