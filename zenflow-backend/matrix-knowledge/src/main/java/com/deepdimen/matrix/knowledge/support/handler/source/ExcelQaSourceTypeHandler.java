package com.deepdimen.matrix.knowledge.support.handler.source;

import cn.hutool.core.util.StrUtil;
import cn.idev.excel.EasyExcel;
import cn.idev.excel.read.listener.PageReadListener;
import com.deepdimen.matrix.admin.api.feign.RemoteFileService;
import com.deepdimen.matrix.common.core.constant.enums.YesNoEnum;
import com.deepdimen.matrix.knowledge.dto.AiDocumentDTO;
import com.deepdimen.matrix.knowledge.dto.AiQAExcelDTO;
import com.deepdimen.matrix.knowledge.entity.AiDocumentEntity;
import com.deepdimen.matrix.knowledge.entity.AiSliceEntity;
import com.deepdimen.matrix.knowledge.mapper.AiDocumentMapper;
import com.deepdimen.matrix.knowledge.mapper.AiSliceMapper;
import com.deepdimen.matrix.knowledge.support.constant.SliceStatusEnums;
import com.deepdimen.matrix.knowledge.support.constant.SourceTypeEnums;
import com.deepdimen.matrix.knowledge.support.util.PromptBuilder;
import feign.Response;
import lombok.RequiredArgsConstructor;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Excel问答文档处理器
 * <p>
 * 处理Excel格式的问答对数据 将Excel中的问题和答案对解析并存储为文档切片 支持批量导入问答知识库
 *
 * @author caoyuan
 * @date 2024/10/3
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ExcelQaSourceTypeHandler extends AbstractFileSourceTypeHandler {

	/**
	 * 远程文件服务
	 */
	private final RemoteFileService remoteFileService;

	/**
	 * 文档数据访问层
	 */
	private final AiDocumentMapper documentMapper;

	/**
	 * 切片数据访问层
	 */
	private final AiSliceMapper sliceMapper;

	/**
	 * 判断是否支持指定的源类型
	 * @param sourceType 源类型
	 * @return 是否支持（仅支持QA类型）
	 */
	@Override
	public boolean supports(String sourceType) {
		return SourceTypeEnums.QA.getType().equals(sourceType);
	}

	/**
	 * 保存文档信息
	 * <p>
	 * 将Excel文件信息保存到数据库
	 * @param documentDTO 文档数据传输对象
	 * @return 保存后的文档实体
	 */
	@Override
	public AiDocumentEntity saveDocument(AiDocumentDTO documentDTO) {
		List<AiDocumentEntity> documentEntityList = documentDTO.getFiles();
		// 创建并保存文档记录
		AiDocumentEntity document = new AiDocumentEntity();
		document.setDatasetId(documentDTO.getDatasetId());
		document.setCategoryId(documentDTO.getCategoryId());
		document.setFileStatus(documentDTO.getFileStatus());
		document.setSourceType(documentDTO.getSourceType());
		// 设置文件信息
		document.setName(documentEntityList.get(0).getName());
		document.setFileUrl(documentEntityList.get(0).getFileUrl());
		document.setFileType("xlsx");
		// 标记为已切片状态
		document.setSliceStatus(SliceStatusEnums.SLICED.getStatus());
		documentMapper.insert(document);
		return document;
	}

	/**
	 * 保存问答切片
	 * <p>
	 * 读取Excel文件中的问答对，将每个问答对保存为独立的切片 支持问题拆分（按换行符分割多个问题）
	 * @param documentEntity 文档实体
	 * @param documentDTO 文档数据传输对象
	 */
	@Override
	@SneakyThrows
	public void saveSlice(AiDocumentEntity documentEntity, AiDocumentDTO documentDTO) {
		// 从远程服务获取Excel文件
		Response response = remoteFileService.getFile(documentEntity.getFileUrl());

		InputStream inputStream = response.body().asInputStream();

		List<AiQAExcelDTO> qaExcelDTOList = new ArrayList<>();
		// 使用EasyExcel读取问答数据
		EasyExcel.read(inputStream, AiQAExcelDTO.class, new PageReadListener<AiQAExcelDTO>(qaExcelDTOList::addAll))
			.sheet()
			.doRead();

		List<String> resultList = new ArrayList<>();
		// 解析并格式化问答对
		for (AiQAExcelDTO aiQAExcelDTO : qaExcelDTOList) {
			// 支持一个答案对应多个问题（换行符分割）
			StrUtil.split(aiQAExcelDTO.getQuestion(), StrUtil.LF).forEach(question -> {
				// 使用模板格式化问答对
				resultList.add(PromptBuilder.render("knowledge-rag-qa.st", Map.of(AiQAExcelDTO.Fields.answer,
						aiQAExcelDTO.getAnswer(), AiQAExcelDTO.Fields.question, aiQAExcelDTO.getQuestion())));
			});
		}

		// 批量保存切片
		for (String result : resultList) {
			AiSliceEntity slice = new AiSliceEntity();
			slice.setDocumentId(documentEntity.getId());
			slice.setContent(result);
			slice.setName(documentEntity.getName());
			slice.setSliceStatus(YesNoEnum.NO.getCode());
			slice.setCharCount((long) result.length());
			sliceMapper.insert(slice);
		}
	}

}
