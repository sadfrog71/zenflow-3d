package com.deepdimen.matrix.knowledge.config;

import com.deepdimen.matrix.knowledge.service.AiTaskService;
import com.deepdimen.matrix.knowledge.support.tenant.KnowledgeTenantExecutor;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;

import java.util.concurrent.TimeUnit;

/**
 * 文档切片和训练任务定时调度配置 负责定期扫描和处理文档，包括生成摘要、文档切片、OCR识别和Mineru解析
 *
 * @author caoyuan
 * @date 2024/3/15
 */
@Slf4j
@EnableScheduling
@RequiredArgsConstructor
@Configuration(proxyBeanMethods = false)
public class SliceTrainTaskConfiguration {

	private final AiTaskService taskService;

	private final KnowledgeTenantExecutor tenantExecutor;

	/**
	 * 定时扫描文档并生成摘要 每60秒执行一次，检查新上传或未处理的文档，自动生成文档摘要
	 */
	@Scheduled(fixedRate = 60, timeUnit = TimeUnit.SECONDS)
	public void scanDocument() {
		tenantExecutor.runForCurrentOrAll("文档摘要重试", tenantId -> taskService.updateDocumentSummary());
	}

	/**
	 * 定时扫描并更新Mineru文档解析结果 每30秒执行一次，仅在启用Mineru功能时运行 用于处理PDF等复杂格式文档的高精度解析
	 * @throws Exception 执行过程中可能抛出的异常
	 */
	@Scheduled(fixedRate = 30, timeUnit = TimeUnit.SECONDS)
	@ConditionalOnProperty(name = "matrix.ai.mineru.enabled", havingValue = "true")
	public void scanMineruDocument() {
		tenantExecutor.runForCurrentOrAll("MinerU解析结果", tenantId -> taskService.updateMineruParseResult());
	}

	/**
	 * 定时扫描文档并进行切片处理 每60秒执行一次，将大文档切分为小片段，便于向量检索和语义搜索
	 */
	@Scheduled(fixedRate = 60, timeUnit = TimeUnit.SECONDS)
	public void scanSlice() {
		tenantExecutor.runForCurrentOrAll("文档向量切片重试", tenantId -> taskService.updateDocumentSlice());
	}

	/**
	 * 定时扫描并更新文档的OCR识别结果 每60秒执行一次，仅在启用CnOCR功能时运行 用于从图片或扫描件中提取文字内容
	 * @throws Exception 执行过程中可能抛出的异常
	 */
	@Scheduled(fixedRate = 60, timeUnit = TimeUnit.SECONDS)
	@ConditionalOnProperty(name = "matrix.ai.cnocr.enabled", havingValue = "true")
	public void scanOCRResult() {
		tenantExecutor.runForCurrentOrAll("OCR解析结果", tenantId -> taskService.updateDocumentOcrResult());
	}

}
