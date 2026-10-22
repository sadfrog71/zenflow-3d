package com.deepdimen.matrix.knowledge.service.impl;

import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.date.DateUtil;
import cn.hutool.core.util.ArrayUtil;
import cn.hutool.core.util.ObjectUtil;
import cn.hutool.core.util.StrUtil;
import com.aspose.words.*;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.deepdimen.matrix.common.core.util.R;
import com.deepdimen.matrix.knowledge.dto.AiWriterReponseDto;
import com.deepdimen.matrix.knowledge.dto.AiWriterRequestDto;
import com.deepdimen.matrix.knowledge.dto.Html2DocxReqDto;
import com.deepdimen.matrix.knowledge.entity.AiDocumentTemplateEntity;
import com.deepdimen.matrix.knowledge.mapper.AiDocumentTemplateMapper;
import com.deepdimen.matrix.knowledge.service.AiNoMemoryStreamAssistantService;
import com.deepdimen.matrix.knowledge.service.AiWriterService;
import com.deepdimen.matrix.knowledge.support.constant.ActionPrompts;
import com.deepdimen.matrix.knowledge.support.provider.ModelProvider;
import com.deepdimen.matrix.knowledge.support.util.PromptBuilder;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import reactor.core.publisher.Flux;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

import static com.deepdimen.matrix.knowledge.support.constant.AiPromptField.systemTime;

/**
 * 智能写作服务实现类
 * 
 * 整合了AI写作、文档模板管理和HTML转DOCX功能
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AiWriterServiceImpl extends ServiceImpl<AiDocumentTemplateMapper, AiDocumentTemplateEntity>
		implements AiWriterService {

	private final ModelProvider modelProvider;

	/**
	 * 静态初始化块：设置字体目录 解决中文和特殊字体在转换后不显示的问题 支持Linux、macOS、Windows等不同操作系统的字体路径
	 */
	static {
		FontSettings.getDefaultInstance()
			.setFontsFolders(
					new String[] { "/usr/share/fonts", "/System/Library/Fonts", "/Library/Fonts", "/opt/fonts" }, true);
	}

	// ==================== AI写作相关方法 ====================

	@Override
	public Flux<AiWriterReponseDto> generateDocument(AiWriterRequestDto requestDto) {
		try {
			AiNoMemoryStreamAssistantService streamAssistantService = modelProvider
				.getAiNoMemoryStreamAssistant(requestDto.getModelName())
				.getValue();

			AiDocumentTemplateEntity templateEntity = baseMapper.selectById(requestDto.getDocTemplateId());
			if (ObjectUtil.isEmpty(templateEntity)) {
				throw new RuntimeException("模板不存在");
			}

			Flux<String> chatResponseFlux = streamAssistantService.chat(PromptBuilder.render("ai-writter-document.st",
					Map.of(systemTime, DateUtil.now(), "title", requestDto.getTitle(), "doc_template",
							templateEntity.getContent(), "abstractContent", requestDto.getAbstractContent())));

			return chatResponseFlux.map(AiWriterReponseDto::new);
		}
		catch (Exception e) {
			log.error("生成AI文档失败", e);
			return Flux.error(e);
		}
	}

	@Override
	public Flux<AiWriterReponseDto> processText(AiWriterRequestDto requestDto) {
		try {
			AiNoMemoryStreamAssistantService streamAssistantService = modelProvider
				.getAiNoMemoryStreamAssistant(requestDto.getModelName())
				.getValue();

			Flux<String> chatResponseFlux = streamAssistantService.chat(PromptBuilder.render("ai-writer-text-ops.st",
					Map.of("action", requestDto.getAction(), systemTime, DateUtil.now(), "instruction",
							ActionPrompts.ACTION_PROMPTS.get(requestDto.getAction()), "text", requestDto.getText())));

			return chatResponseFlux.map(AiWriterReponseDto::new);
		}
		catch (Exception e) {
			log.error("文本智能处理失败", e);
			return Flux.error(e);
		}
	}

	// ==================== 文档模板管理相关方法 ====================

	@Override
	public R getTemplatePage(Page page, AiDocumentTemplateEntity templateQuery) {
		try {
			return R.ok(baseMapper.selectPage(page,
					Wrappers.<AiDocumentTemplateEntity>lambdaQuery()
						.eq(StrUtil.isNotEmpty(templateQuery.getName()), AiDocumentTemplateEntity::getName,
								templateQuery.getName())
						.orderByDesc(AiDocumentTemplateEntity::getCreateTime)));
		}
		catch (Exception e) {
			log.error("分页查询文档模板失败", e);
			return R.failed("查询失败：" + e.getMessage());
		}
	}

	@Override
	public R searchTemplates(AiDocumentTemplateEntity templateQuery) {
		try {
			return R.ok(baseMapper.selectList(Wrappers.query(templateQuery)));
		}
		catch (Exception e) {
			log.error("查询文档模板失败", e);
			return R.failed("查询失败：" + e.getMessage());
		}
	}

	@Override
	public R createTemplate(AiDocumentTemplateEntity template) {
		baseMapper.insert(template);
		return R.ok();
	}

	@Override
	public R updateTemplate(AiDocumentTemplateEntity template) {
		baseMapper.updateById(template);
		return R.ok();
	}

	@Override
	public R deleteTemplates(Long[] templateIds) {
		baseMapper.deleteByIds(CollUtil.toList(templateIds));
		return R.ok();
	}

	@Override
	public List<AiDocumentTemplateEntity> exportTemplatesToExcel(AiDocumentTemplateEntity templateQuery,
			Long[] exportIds) {
		try {
			return baseMapper.selectList(Wrappers.lambdaQuery(templateQuery)
				.in(ArrayUtil.isNotEmpty(exportIds), AiDocumentTemplateEntity::getId, CollUtil.toList(exportIds)));
		}
		catch (Exception e) {
			log.error("导出模板数据失败", e);
			throw new RuntimeException("导出失败：" + e.getMessage());
		}
	}

	@Override
	public R importTemplatesFromExcel(List<AiDocumentTemplateEntity> templateList) {
		baseMapper.insert(templateList);
		return R.ok();
	}

	// ==================== HTML转DOCX相关方法 ====================

	@Override
	public ResponseEntity<byte[]> convertHtmlToDocx(Html2DocxReqDto conversionRequest) {
		try {
			// 验证HTML内容是否为空
			if (!StringUtils.hasText(conversionRequest.getHtml())) {
				return ResponseEntity.badRequest().body(("HTML内容不能为空").getBytes(StandardCharsets.UTF_8));
			}

			// 调用核心转换方法，将HTML字符串转换为DOCX字节数组
			byte[] docxBytes = convertHtmlToDocxBytes(conversionRequest.getHtml().getBytes(StandardCharsets.UTF_8),
					conversionRequest.getBaseUrl());

			// 返回转换后的文档，文件名包含时间戳
			return createDownloadResponse(docxBytes, StrUtil.format("converted_{}.docx", System.currentTimeMillis()));
		}
		catch (Exception e) {
			log.error("HTML转DOCX失败", e);
			throw new RuntimeException("转换失败：" + e.getMessage());
		}
	}

	/**
	 * 核心转换方法：将HTML字节数组转换为DOCX字节数组
	 *
	 * 这是所有转换方式的核心实现，负责： 1. 配置HTML加载选项 2. 创建Aspose.Words文档对象 3. 设置页面格式（A4纸张、页边距等） 4.
	 * 输出为DOCX格式
	 * @param htmlBytes HTML内容的字节数组
	 * @param baseUrl 基础URL，用于解析相对路径的资源引用
	 * @return 转换后的DOCX文档字节数组
	 * @throws Exception 转换过程中可能出现的异常
	 */
	private byte[] convertHtmlToDocxBytes(byte[] htmlBytes, String baseUrl) throws Exception {
		// 创建HTML加载选项
		HtmlLoadOptions loadOptions = new HtmlLoadOptions();
		loadOptions.setLoadFormat(LoadFormat.HTML); // 指定加载格式为HTML
		loadOptions.setEncoding(StandardCharsets.UTF_8); // 设置编码为UTF-8

		// 如果提供了基础URL，设置到加载选项中
		// 这样相对路径的资源（如img/src、link/href）就能被正确解析
		if (StringUtils.hasText(baseUrl)) {
			loadOptions.setBaseUri(baseUrl);
		}

		try (ByteArrayInputStream inputStream = new ByteArrayInputStream(htmlBytes);
				ByteArrayOutputStream outputStream = new ByteArrayOutputStream()) {

			// 使用HTML内容创建Aspose.Words文档对象
			Document document = new Document(inputStream, loadOptions);

			// 统一设置页面格式：A4纸张大小，设置页边距
			// 遍历所有节（Section），为每个节设置相同的页面属性
			for (Section section : document.getSections()) {
				PageSetup pageSetup = section.getPageSetup();
				pageSetup.setPaperSize(PaperSize.A4); // 设置纸张大小为A4
				pageSetup.setTopMargin(ConvertUtil.millimeterToPoint(20)); // 上边距20mm
				pageSetup.setBottomMargin(ConvertUtil.millimeterToPoint(20)); // 下边距20mm
				pageSetup.setLeftMargin(ConvertUtil.millimeterToPoint(25)); // 左边距25mm
				pageSetup.setRightMargin(ConvertUtil.millimeterToPoint(25)); // 右边距25mm
			}

			// 将文档保存为DOCX格式到输出流
			document.save(outputStream, SaveFormat.DOCX);
			return outputStream.toByteArray();
		}
	}

	/**
	 * 创建文件下载响应
	 *
	 * 设置HTTP响应头，使浏览器将响应内容作为附件下载 设置正确的Content-Type和Content-Disposition头
	 * @param fileBytes 要下载的文件字节数组
	 * @param fileName 下载时的文件名
	 * @return 配置好的HTTP响应实体
	 */
	private ResponseEntity<byte[]> createDownloadResponse(byte[] fileBytes, String fileName) {
		return ResponseEntity.ok()
			.header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + fileName + "\"") // 设置为附件下载
			.contentType(
					MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.wordprocessingml.document")) // DOCX文件的MIME类型
			.body(fileBytes);
	}

}
