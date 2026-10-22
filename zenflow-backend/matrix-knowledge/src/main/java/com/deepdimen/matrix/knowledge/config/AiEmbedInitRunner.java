package com.deepdimen.matrix.knowledge.config;

import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.util.ArrayUtil;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.deepdimen.matrix.common.core.constant.enums.YesNoEnum;
import com.deepdimen.matrix.knowledge.entity.AiDataEntity;
import com.deepdimen.matrix.knowledge.entity.AiMcpConfigEntity;
import com.deepdimen.matrix.knowledge.mapper.AiDataMapper;
import com.deepdimen.matrix.knowledge.mapper.AiDatasetMapper;
import com.deepdimen.matrix.knowledge.mapper.AiMcpConfigMapper;
import com.deepdimen.matrix.knowledge.support.constant.EmbedBizTypeEnums;
import com.deepdimen.matrix.knowledge.support.function.FunctionCalling;
import com.deepdimen.matrix.knowledge.support.provider.EmbeddingProvider;
import com.deepdimen.matrix.knowledge.support.provider.TagClassifierProvider;
import com.deepdimen.matrix.knowledge.support.tenant.KnowledgeTenantExecutor;
import dev.langchain4j.data.document.Metadata;
import dev.langchain4j.data.segment.TextSegment;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.web.context.WebServerInitializedEvent;
import org.springframework.context.ApplicationEvent;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.event.EventListener;
import org.springframework.core.annotation.Order;
import org.springframework.scheduling.annotation.Async;

import java.util.List;
import java.util.Map;

import static com.deepdimen.matrix.knowledge.support.provider.EmbeddingProvider.TEMP_ID;

/**
 * AI向量数据初始化运行器 在应用启动后自动初始化函数调用、MCP配置和数据源的向量数据 用于支持AI功能的语义搜索和智能路由
 *
 * @author caoyuan
 * @date 2025/03/21
 */
@Slf4j
@Configuration
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "matrix.knowledge.embed-init", name = "enabled", havingValue = "true", matchIfMissing = true)
public class AiEmbedInitRunner {

	/**
	 * 系统中所有可用的函数调用实现列表
	 */
	private final List<FunctionCalling> functionCallingList;

	/**
	 * MCP（Model Context Protocol）配置数据访问层
	 */
	private final AiMcpConfigMapper aiMcpConfigMapper;

	/**
	 * AI数据源配置数据访问层
	 */
	private final AiDataMapper aiDataMapper;

	/**
	 * AI知识库数据访问层
	 */
	private final AiDatasetMapper aiDatasetMapper;

	/**
	 * 标签分类器提供者
	 */
	private final TagClassifierProvider classifierProvider;

	private final KnowledgeTenantExecutor tenantExecutor;

	/**
	 * Web服务器初始化事件监听器 在服务器启动完成后异步执行向量数据初始化 将函数调用、MCP配置、数据源信息转换为向量并存储到内存中
	 */
	@Async(DocumentParseAsyncConfiguration.DOCUMENT_PARSE_EXECUTOR)
	@Order
	@EventListener({ WebServerInitializedEvent.class, EmbedInitEvent.class })
	public void WebServerInit() {
		tenantExecutor.runForCurrentOrAll("业务向量初始化", tenantId -> initializeCurrentTenant());
	}

	private void initializeCurrentTenant() {
		// 初始化函数调用向量数据
		EmbeddingProvider.removeAll();

		// 将每个函数的名称和描述组合成文本，创建向量并存储
		for (FunctionCalling functionCalling : functionCallingList) {
			TextSegment textSegment = TextSegment.textSegment(
					functionCalling.functionName() + functionCalling.functionDesc(),
					Metadata.from(Map.of(EmbedBizTypeEnums.Fields.type, EmbedBizTypeEnums.FUNCTION.getType(), TEMP_ID,
							functionCalling.functionName())));
			EmbeddingProvider.add(textSegment);
		}

		log.info("AI 函数调用的向量初始化完毕");

		// 初始化MCP配置向量数据
		// 查询所有启用的MCP配置，将配置名称和描述转换为向量
		List<AiMcpConfigEntity> mcpConfigEntityList = aiMcpConfigMapper
			.selectList(Wrappers.<AiMcpConfigEntity>lambdaQuery()
				.eq(AiMcpConfigEntity::getMcpEnabled, YesNoEnum.YES.getCode()));

		for (AiMcpConfigEntity mcpConfigEntity : mcpConfigEntityList) {
			TextSegment textSegment = TextSegment.textSegment(
					mcpConfigEntity.getName() + mcpConfigEntity.getDescription(),
					Metadata.from(Map.of(EmbedBizTypeEnums.Fields.type, EmbedBizTypeEnums.CHAT2MCP.getType(), TEMP_ID,
							mcpConfigEntity.getMcpId())));
			EmbeddingProvider.add(textSegment);
		}

		log.info("AI MCP 调用的向量初始化完毕");

		// 初始化数据源向量数据
		// 查询所有数据源配置，将数据源名称和描述转换为向量，用于Chat2SQL功能
		List<AiDataEntity> aiDataEntityList = aiDataMapper.selectList(Wrappers.lambdaQuery());

		for (AiDataEntity aiDataEntity : aiDataEntityList) {
			TextSegment textSegment = TextSegment.textSegment(aiDataEntity.getDsName() + aiDataEntity.getDescription(),
					Metadata.from(Map.of(EmbedBizTypeEnums.Fields.type, EmbedBizTypeEnums.CHAT2SQL.getType(), TEMP_ID,
							aiDataEntity.getDataId())));
			EmbeddingProvider.add(textSegment);
		}

		log.info("AI 数据调用的向量初始化完毕");

		// 初始化知识库向量数据
		aiDatasetMapper.selectList(Wrappers.lambdaQuery()).forEach(dataset -> {
			TextSegment textSegment = TextSegment.textSegment(dataset.getName() + dataset.getDescription(),
					Metadata.from(Map.of(EmbedBizTypeEnums.Fields.type, EmbedBizTypeEnums.DATASET.getType(), TEMP_ID,
							dataset.getId())));
			EmbeddingProvider.add(textSegment);
		});

		log.info("AI 知识库的向量初始化完毕");

		// 知识库的标签
		aiDatasetMapper.selectList(Wrappers.lambdaQuery()).forEach(dataset -> {
			if (ArrayUtil.isEmpty(dataset.getTags())) {
				return;
			}

			classifierProvider.remove(dataset.getId().toString());
			classifierProvider.getClassify(dataset.getId().toString(), CollUtil.toList(dataset.getTags()));
		});
		log.info("AI 标签的向量初始化完毕");
	}

	/**
	 * 向量刷新事件
	 */
	public static class EmbedInitEvent extends ApplicationEvent {

		public EmbedInitEvent(Object source) {
			super(source);
		}

	}

}
