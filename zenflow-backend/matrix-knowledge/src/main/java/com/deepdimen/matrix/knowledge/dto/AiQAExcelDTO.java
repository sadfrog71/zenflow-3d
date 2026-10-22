package com.deepdimen.matrix.knowledge.dto;

import cn.idev.excel.annotation.ExcelProperty;
import lombok.Data;
import lombok.experimental.FieldNameConstants;

/**
 * AI问答Excel数据传输对象
 * <p>
 * 用于Excel文件导入导出的问答对数据结构，支持批量处理问答知识库数据
 *
 * @author caoyuan
 * @date 2024/7/2
 */
@Data
@FieldNameConstants
public class AiQAExcelDTO {

	/**
	 * 问题内容
	 * <p>
	 * Excel中"问题"列的内容，表示用户可能提出的问题
	 */
	@ExcelProperty("问题")
	private String question;

	/**
	 * 答案内容
	 * <p>
	 * Excel中"答案"列的内容，表示对应问题的标准答案或回复
	 */
	@ExcelProperty("答案")
	private String answer;

}
