package com.deepdimen.matrix.knowledge.support.handler.parse;

import cn.hutool.core.lang.Pair;
import cn.hutool.extra.spring.SpringUtil;
import com.deepdimen.matrix.knowledge.config.properties.AiKnowledgeProperties;
import com.deepdimen.matrix.knowledge.dto.AiDocumentDTO;
import com.deepdimen.matrix.knowledge.entity.AiDatasetEntity;
import com.deepdimen.matrix.knowledge.entity.AiDocumentEntity;
import com.deepdimen.matrix.knowledge.support.constant.FileParserStatusEnums;
import com.deepdimen.matrix.knowledge.support.constant.SplitterTypeEnums;
import dev.langchain4j.data.document.Document;
import dev.langchain4j.data.document.DocumentSplitter;
import dev.langchain4j.data.document.splitter.DocumentByCharacterSplitter;
import dev.langchain4j.data.document.splitter.DocumentByParagraphSplitter;
import dev.langchain4j.data.document.splitter.DocumentBySentenceSplitter;
import dev.langchain4j.data.segment.TextSegment;
import org.springframework.core.Ordered;

import java.io.InputStream;
import java.util.List;
import java.util.Objects;

/**
 * 文件解析处理器接口
 * <p>
 * 该接口定义了文件解析处理器的基本契约，所有文件解析器都必须实现此接口。 系统通过策略模式根据文件类型和配置选择合适的解析器。 处理器支持以下功能： 1.
 * 判断是否支持特定类型的文件 2. 将文件内容转换为文本 3. 清洗和预处理文本数据 4. 根据配置对文本进行分片处理
 *
 * @author caoyuan
 * @date 2024/3/15
 */
public interface UploadFileParseHandler extends Ordered {

	/**
	 * 判断是否支持处理指定的文档
	 * <p>
	 * 实现类应根据文件类型、数据集配置等因素判断是否能处理该文档。 系统会按优先级顺序询问每个处理器，直到找到支持的处理器。
	 * @param aiDataset 数据集实体，包含解析相关配置
	 * @param document 文档实体，包含文件信息
	 * @return 如果支持处理返回true，否则返回false
	 */
	boolean supports(AiDatasetEntity aiDataset, AiDocumentEntity document);

	/**
	 * 判断是否支持处理指定真实文件类型的文档。
	 * <p>
	 * 默认兼容原有按文件名后缀判断的逻辑，支持伪后缀文件的解析器可重写该方法。
	 * @param aiDataset 数据集实体，包含解析相关配置
	 * @param document 文档实体，包含文件信息
	 * @param extName 识别后的真实文件扩展名
	 * @return 如果支持处理返回true，否则返回false
	 */
	default boolean supports(AiDatasetEntity aiDataset, AiDocumentEntity document, String extName) {
		return supports(aiDataset, document);
	}

	/**
	 * 将文件内容转换为文本字符串
	 * <p>
	 * 该方法是文件解析的核心，负责将各种格式的文件转换为纯文本。 返回的Pair对象包含解析状态和结果： - 成功时：状态为PARSE_SUCCESS，结果为提取的文本 -
	 * 异步处理时：状态为AI_PARSING/OCR_PARSING，结果为任务ID - 失败时：状态为PARSE_FAIL，结果为错误信息
	 * @param documentEntity 文档实体，包含文档信息
	 * @param inputStream 文件输入流
	 * @param extName 文件扩展名
	 * @return 包含解析状态和结果的Pair对象
	 */
	Pair<FileParserStatusEnums, String> file2String(AiDocumentEntity documentEntity, InputStream inputStream,
			String extName);

	/**
	 * 清洗和预处理文本数据
	 * <p>
	 * 子类可以重写此方法来实现特定的数据清洗逻辑， 如去除特殊字符、规范化格式等。默认实现直接返回原文本。
	 * @param input 原始文本
	 * @return 清洗后的文本
	 */
	default String cleanData(String input) {
		return input;
	}

	/**
	 * 将文本拆分成多个片段
	 * <p>
	 * 根据配置的拆分策略将长文本分割成多个小片段，以便于向量化存储和检索。 支持的拆分策略包括： - 段落拆分（PARAGRAPH）：按段落边界拆分 -
	 * 字符拆分（CHARACTER）：按固定字符数拆分 - 句子拆分（SENTENCE）：按句子边界拆分 - 递归拆分（默认）：智能递归拆分，保持语义完整性
	 *
	 * 拆分时会考虑最大片段大小和重叠大小，确保片段之间有适当的上下文重叠。
	 * @param documentEntity 文档实体对象
	 * @param aiDatasetEntity 数据集实体对象，包含默认的片段大小配置
	 * @param input 需要拆分的输入字符串
	 * @param documentDTO 文档数据传输对象，包含拆分策略配置
	 * @return 拆分后的字符串片段列表
	 */
	default List<String> splitData(AiDocumentEntity documentEntity, AiDatasetEntity aiDatasetEntity, String input,
			AiDocumentDTO documentDTO) {
		AiKnowledgeProperties knowledgeProperties = SpringUtil.getBean(AiKnowledgeProperties.class);
		DocumentSplitter documentSplitter;

		// 根据文档DTO的切片类型选择不同的拆分器
		if (Objects.isNull(documentDTO)) {
			documentSplitter = new HtmlTableAwareSplitter(aiDatasetEntity.getFragmentSize(),
					knowledgeProperties.getInMemorySearch().getMaxOverlapSizeInChars());
		}
		else {
			if (SplitterTypeEnums.PARAGRAPH.getType().equals(documentDTO.getSliceType())) {
				documentSplitter = new DocumentByParagraphSplitter(documentDTO.getMaxSegmentSizeInTokens(),
						documentDTO.getMaxOverlapSizeInTokens());
			}
			else if (SplitterTypeEnums.CHARACTER.getType().equals(documentDTO.getSliceType())) {
				documentSplitter = new DocumentByCharacterSplitter(documentDTO.getMaxSegmentSizeInTokens(),
						documentDTO.getMaxOverlapSizeInTokens());
			}
			else if (SplitterTypeEnums.SENTENCE.getType().equals(documentDTO.getSliceType())) {
				documentSplitter = new DocumentBySentenceSplitter(documentDTO.getMaxSegmentSizeInTokens(),
						documentDTO.getMaxOverlapSizeInTokens());
			}
			else {
				documentSplitter = new HtmlTableAwareSplitter(aiDatasetEntity.getFragmentSize(),
						knowledgeProperties.getInMemorySearch().getMaxOverlapSizeInChars());
			}
		}

		List<TextSegment> textSegmentList = documentSplitter.split(Document.from(input));
		return textSegmentList.stream().map(TextSegment::text).toList();
	}

	/**
	 * 获取处理器的优先级顺序
	 * <p>
	 * 数值越大，优先级越高。当多个处理器都支持同一文件类型时， 系统会优先使用优先级高的处理器。默认优先级为0。 实现类可以重写此方法来设置自定义优先级。
	 * @return 优先级值，默认返回0
	 */
	@Override
	default int getOrder() {
		return 0;
	}

}
