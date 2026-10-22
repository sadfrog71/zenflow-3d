package com.deepdimen.matrix.knowledge.support.handler.source;

import com.deepdimen.matrix.knowledge.dto.AiDocumentDTO;
import com.deepdimen.matrix.knowledge.entity.AiDatasetEntity;
import com.deepdimen.matrix.knowledge.entity.AiDocumentEntity;
import com.deepdimen.matrix.knowledge.entity.AiSliceEntity;
import com.deepdimen.matrix.knowledge.mapper.AiDatasetMapper;
import com.deepdimen.matrix.knowledge.mapper.AiDocumentMapper;
import com.deepdimen.matrix.knowledge.mapper.AiSliceMapper;
import com.deepdimen.matrix.knowledge.support.constant.SliceStatusEnums;
import com.deepdimen.matrix.knowledge.support.constant.SourceTypeEnums;
import com.deepdimen.matrix.knowledge.support.constant.SummaryStatusEnums;
import com.deepdimen.matrix.knowledge.support.handler.parse.TextUploadFileParseHandler;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * 文本输入源处理器
 * <p>
 * 处理直接输入的文本内容和URL链接 支持将用户输入的文本或网页内容保存为知识库文档 自动进行文本分割和切片处理
 *
 * @author caoyuan
 * @date 2024/10/3
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class InputTextSourceTypeHandler extends AbstractFileSourceTypeHandler {

	/**
	 * 文档数据访问层
	 */
	private final AiDocumentMapper documentMapper;

	/**
	 * 切片数据访问层
	 */
	private final AiSliceMapper sliceMapper;

	/**
	 * 数据集数据访问层
	 */
	private final AiDatasetMapper datasetMapper;

	/**
	 * 文本解析处理器
	 */
	private final TextUploadFileParseHandler textUploadFileParseHandler;

	/**
	 * 判断是否支持指定的源类型
	 * <p>
	 * 支持TEXT（文本输入）和URL（网页链接）两种类型
	 * @param sourceType 源类型
	 * @return 是否支持
	 */
	@Override
	public boolean supports(String sourceType) {
		return SourceTypeEnums.TEXT.getType().equals(sourceType) || SourceTypeEnums.URL.getType().equals(sourceType);
	}

	/**
	 * 保存文档信息
	 * <p>
	 * 创建文本类型的文档记录，设置初始状态
	 * @param documentDTO 文档数据传输对象
	 * @return 保存后的文档实体
	 */
	@Override
	public AiDocumentEntity saveDocument(AiDocumentDTO documentDTO) {
		AiDocumentEntity document = new AiDocumentEntity();
		document.setDatasetId(documentDTO.getDatasetId());
		document.setCategoryId(documentDTO.getCategoryId());
		document.setFileStatus(documentDTO.getFileStatus());
		document.setSourceType(documentDTO.getSourceType());
		// 设置文件类型为文本
		document.setFileType("txt");
		// 标记为已切片状态
		document.setSliceStatus(SliceStatusEnums.SLICED.getStatus());
		document.setName(documentDTO.getName());
		// 设置摘要状态为未生成
		document.setSummaryStatus(SummaryStatusEnums.UNSUMMARY.getStatus());
		documentMapper.insert(document);
		return document;
	}

	/**
	 * 保存文档切片
	 * <p>
	 * 根据数据集配置对文本内容进行智能分割 将分割后的文本块保存为独立的切片
	 * @param documentEntity 文档实体对象
	 * @param documentDTO 包含文本内容的文档DTO
	 */
	@Override
	public void saveSlice(AiDocumentEntity documentEntity, AiDocumentDTO documentDTO) {
		// 获取数据集配置
		AiDatasetEntity aiDatasetEntity = datasetMapper.selectById(documentDTO.getDatasetId());
		// 使用文本解析器进行内容分割
		List<String> stringList = textUploadFileParseHandler.splitData(documentEntity, aiDatasetEntity,
				documentDTO.getContent(), documentDTO);

		// 批量保存切片
		for (String content : stringList) {
			AiSliceEntity slice = new AiSliceEntity();
			slice.setDocumentId(documentEntity.getId());
			slice.setContent(content);
			slice.setName(documentDTO.getName());
			// 设置为未向量化状态
			slice.setSliceStatus(SliceStatusEnums.UNSLICED.getStatus());
			slice.setCharCount((long) content.length());
			sliceMapper.insert(slice);
		}
	}

}
