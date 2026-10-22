package com.deepdimen.matrix.knowledge.dto;

import dev.langchain4j.model.output.structured.Description;
import lombok.Data;

import java.util.List;

/**
 * 大模型OCR解析结果对象
 * <p>
 * 封装使用大语言模型进行OCR文本提取后的结果数据
 *
 * @author caoyuan
 * @date 2024/9/17
 */
@Data
@Description("解析POJO结果对象")
public class LllAiOcrResult {

	@Description("解析是否成功标志，指示是否包含输入文本要求的字段信息，true: 包含，false: 不包含")
	private boolean isSuccessful;

	@Description("从OCR识别的文本中提取出的多个结果字段列表")
	private List<OcrField> ocrFieldList;

}

@Data
@Description("OCR识别结果字段，封装单个字段的名称和值")
class OcrField {

	@Description("字段名称，用于标识该字段的含义")
	String fieldName;

	@Description("字段值，从OCR文本中提取出的实际内容")
	String fieldValue;

}
