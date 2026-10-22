package com.deepdimen.matrix.knowledge.service.impl;

import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.date.DateUtil;
import cn.hutool.core.io.FileUtil;
import cn.hutool.core.io.IoUtil;
import cn.hutool.core.lang.Pair;
import cn.hutool.core.util.StrUtil;
import cn.hutool.http.HttpUtil;
import cn.hutool.json.JSONUtil;
import com.aspose.words.Document;
import com.aspose.words.HtmlSaveOptions;
import com.aspose.words.SaveFormat;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.deepdimen.matrix.admin.api.feign.RemoteFileService;
import com.deepdimen.matrix.common.core.constant.enums.YesNoEnum;
import com.deepdimen.matrix.common.core.util.R;
import com.deepdimen.matrix.common.security.util.SecurityUtils;
import com.deepdimen.matrix.knowledge.dto.AiReportCompletionsDTO;
import com.deepdimen.matrix.knowledge.dto.LlmOCRFieldDTO;
import com.deepdimen.matrix.knowledge.entity.AiReportConfEntity;
import com.deepdimen.matrix.knowledge.mapper.AiReportConfMapper;
import com.deepdimen.matrix.knowledge.service.AiAssistantService;
import com.deepdimen.matrix.knowledge.service.AiReportConfService;
import com.deepdimen.matrix.knowledge.support.constant.ModelProviderFormatEnums;
import com.deepdimen.matrix.knowledge.support.provider.ModelProvider;
import com.deepdimen.matrix.knowledge.support.util.JSONRepairUtil;
import com.deepdimen.matrix.knowledge.support.util.PromptBuilder;
import com.sushengren.easyword.EasyWord;
import com.sushengren.easyword.constants.DefaultAnchors;
import com.sushengren.easyword.model.AnchorContext;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.request.ResponseFormat;
import dev.langchain4j.model.chat.request.json.JsonObjectSchema;
import dev.langchain4j.model.chat.request.json.JsonSchema;
import dev.langchain4j.model.chat.request.json.JsonStringSchema;
import dev.langchain4j.model.chat.response.ChatResponse;
import feign.Response;
import lombok.RequiredArgsConstructor;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.tuple.Triple;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.springframework.stereotype.Service;

import java.io.*;
import java.nio.charset.Charset;
import java.util.*;

import static com.deepdimen.matrix.knowledge.support.constant.AiPromptField.systemTime;
import static dev.langchain4j.data.message.SystemMessage.systemMessage;
import static dev.langchain4j.data.message.UserMessage.userMessage;
import static dev.langchain4j.model.chat.request.ResponseFormatType.JSON;

/**
 * AI报告配置服务实现类
 * <p>
 * 提供AI报告的解析和生成服务 支持Word文档模板解析和书签、占位符提取 结合AI模型实现报告内容的智能填充
 *
 * @author caoyuan
 * @date 2025/05/11
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AiReportConfServiceImpl extends ServiceImpl<AiReportConfMapper, AiReportConfEntity>
		implements AiReportConfService {

	/**
	 * 远程文件服务 用于获取报告模板文件
	 */
	private final RemoteFileService remoteFileService;

	/**
	 * 模型提供者 管理AI模型实例
	 */
	private final ModelProvider modelProvider;

	/**
	 * 解析文档模板
	 * <p>
	 * 从Word文档中提取书签和占位符 支持HTTP链接和文件存储两种资源获取方式
	 * @param aiReportConf 包含报告资源路径的配置实体
	 * @return 包含所有书签和占位符名称的列表
	 */
	@Override
	@SneakyThrows
	public R parseDoc(AiReportConfEntity aiReportConf) {
		String reportResource = aiReportConf.getReportResource();

		// 根据资源类型获取文件字节数据
		byte[] bytes;
		if (StrUtil.startWithAnyIgnoreCase(reportResource, "http")) {
			bytes = HttpUtil.downloadBytes(reportResource);
		}
		else {
			Response response = remoteFileService.getFile(aiReportConf.getReportResource());

			try (ByteArrayOutputStream baos = new ByteArrayOutputStream()) {
				IoUtil.copy(response.body().asInputStream(), baos);
				bytes = baos.toByteArray();
			}
		}

		// 提取文档中的书签和占位符
		List<AnchorContext> bookmarkList = DefaultAnchors.BOOKMARK_ANCHOR
			.locate(new XWPFDocument(new ByteArrayInputStream(bytes)));
		List<AnchorContext> placeholderList = DefaultAnchors.PLACEHOLDER_ANCHOR
			.locate(new XWPFDocument(new ByteArrayInputStream(bytes)));

		List<Map<String, String>> pairs = CollUtil.addAll(bookmarkList, placeholderList)
			.stream()
			.map(anchorContext -> Map.of("name", anchorContext.getKey()))
			.toList();
		return R.ok(pairs);
	}

	/**
	 * 生成并下载AI报告
	 * @param completionsDTO 包含报告ID、模型名和用户输入的请求参数
	 * @return 生成的HTML格式报告内容
	 */
	@Override
	@SneakyThrows
	public String generate(AiReportCompletionsDTO completionsDTO) {
		// 调用文本模型处理用户输入
		Pair<ChatModel, AiAssistantService> aiAssistant = modelProvider.getAiAssistant(completionsDTO.getModel());

		AiReportConfEntity reportConfEntity = baseMapper.selectById(completionsDTO.getReportId());

		// 如果需要AI润色，先对内容进行优化
		if (YesNoEnum.YES.getCode().equals(completionsDTO.getPolish())) {
			String chatted = aiAssistant.getKey()
				.chat(PromptBuilder.render("report-polish.st",
						Map.of(AiReportConfEntity.Fields.reportTitle, reportConfEntity.getReportTitle(),
								AiReportConfEntity.Fields.reportPrompt, reportConfEntity.getReportPrompt()// 系统提示词
								, AiReportCompletionsDTO.Fields.prompt, completionsDTO.getPrompt() // 用户原有输入
								, "username", SecurityUtils.getUser().getName(), systemTime, DateUtil.now())));
			completionsDTO.setPrompt(chatted
					+ StrUtil.format("systemTime：{}，username：{}", DateUtil.now(), SecurityUtils.getUser().getName()));
		}

		// 构建JSON Schema以约束输出格式
		ChatRequest.Builder builder = ChatRequest.builder();
		List<String> fieldNames = new ArrayList<>();
		fieldNames.add("isContain");

		List<LlmOCRFieldDTO> fieldDTOList = JSONUtil.parseObj(reportConfEntity.getReportMarked())
			.getBeanList("markerList", LlmOCRFieldDTO.class);

		JsonSchema jsonSchema = JsonSchema.builder()
			.name("ReportResult")
			.rootElement(JsonObjectSchema.builder().addProperties(new LinkedHashMap<>() {
				{
					put("isContain",
							JsonStringSchema.builder()
								.description(StrUtil.format("用户输入是否包含{}要求的内容", reportConfEntity.getReportTitle()))
								.build());
					for (LlmOCRFieldDTO llmOCRFieldDTO : fieldDTOList) {
						put(llmOCRFieldDTO.getName(),
								JsonStringSchema.builder().description(llmOCRFieldDTO.getDescription()).build());
					}
				}
			}).required(fieldNames).additionalProperties(false).build())
			.build();

		// 根据模型类型选择不同的JSON Schema传递方式
		Triple<ChatModel, AiAssistantService, String> aiJSONAssistant = modelProvider
			.getAiJSONAssistant(completionsDTO.getModel());

		if (Objects.equals(aiJSONAssistant.getRight(), ModelProviderFormatEnums.OPENAI.getFormat())) {
			builder
				.messages(systemMessage(PromptBuilder.render("report-system-json.st")),
						userMessage(PromptBuilder.render("report-user-json.st",
								Map.of("userInput", completionsDTO.getPrompt(), "jsonSchema", StrUtil.EMPTY))))
				.responseFormat(ResponseFormat.builder().type(JSON).jsonSchema(jsonSchema).build());
		}
		else {
			builder.messages(systemMessage(PromptBuilder.render("report-system-json.st")),
					userMessage(PromptBuilder.render("report-user-json.st",
							Map.of("userInput", completionsDTO.getPrompt(), "jsonSchema", jsonSchema))));
		}

		ChatResponse chatted = aiJSONAssistant.getLeft().chat(builder.build());
		log.info("Report JSON: {}", chatted.aiMessage().text());

		String repair = JSONRepairUtil.repair(chatted.aiMessage().text());

		// 将AI生成的结构化数据填充到Word模板
		Map bean = JSONUtil.toBean(repair, Map.class);

		File tempFile = FileUtil.createTempFile();

		try {
			// 先写入临时文件，使用 try-with-resources 确保输出流关闭
			try (FileOutputStream fileOutputStream = new FileOutputStream(tempFile)) {
				if (StrUtil.startWithAnyIgnoreCase(reportConfEntity.getReportResource(), "http")) {
					byte[] bytes = HttpUtil.downloadBytes(reportConfEntity.getReportResource());
					EasyWord.of(new ByteArrayInputStream(bytes)).doWrite(bean).toOutputStream(fileOutputStream);
				}
				else {
					Response fileResponse = remoteFileService.getFile(reportConfEntity.getReportResource());
					EasyWord.of(fileResponse.body().asInputStream()).doWrite(bean).toOutputStream(fileOutputStream);
				}
			}

			// 输出流已关闭，现在可以安全地读取临时文件
			try (BufferedInputStream fileInputStream = FileUtil.getInputStream(tempFile);
					ByteArrayOutputStream htmlStream = new ByteArrayOutputStream()) {

				Document doc = new Document(fileInputStream);
				HtmlSaveOptions options = new HtmlSaveOptions(SaveFormat.HTML);
				options.setExportImagesAsBase64(true);

				doc.save(htmlStream, options);
				return htmlStream.toString(Charset.defaultCharset());
			}
		}
		finally {
			// 确保临时文件被删除
			FileUtil.del(tempFile);
		}
	}

}