package com.deepdimen.matrix.knowledge.service.impl;

import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.map.MapUtil;
import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.deepdimen.matrix.common.core.constant.enums.YesNoEnum;
import com.deepdimen.matrix.common.core.util.R;
import com.deepdimen.matrix.common.data.tenant.TenantBroker;
import com.deepdimen.matrix.common.data.tenant.TenantContextHolder;
import com.deepdimen.matrix.common.security.util.SecurityUtils;
import com.deepdimen.matrix.knowledge.dto.*;
import com.deepdimen.matrix.knowledge.entity.AiChatRecordEntity;
import com.deepdimen.matrix.knowledge.entity.AiDatasetEntity;
import com.deepdimen.matrix.knowledge.mapper.AiChatRecordMapper;
import com.deepdimen.matrix.knowledge.mapper.AiDatasetMapper;
import com.deepdimen.matrix.knowledge.service.AiChatRecordService;
import com.deepdimen.matrix.knowledge.service.EmbeddingStoreService;
import com.deepdimen.matrix.knowledge.support.constant.DocumentTypeEnums;
import com.deepdimen.matrix.knowledge.support.constant.LLMUseStatusEnums;
import com.deepdimen.matrix.knowledge.support.provider.ModelProvider;
import dev.langchain4j.data.document.Metadata;
import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.embedding.DimensionAwareEmbeddingModel;
import dev.langchain4j.model.output.Response;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.stream.Collectors;

/**
 * AI聊天记录服务实现类
 * <p>
 * 管理用户与AI的对话记录 支持记录存储、更新和数据标注功能 可将优质问答对标注到向量数据库用于后续检索
 *
 * @author matrix
 * @date 2024-07-03 12:39:32
 */
@Service
@RequiredArgsConstructor
public class AiChatRecordServiceImpl extends ServiceImpl<AiChatRecordMapper, AiChatRecordEntity>
		implements AiChatRecordService {

	private static final int MAX_USER_RANK_PAGE_SIZE = 100;

	private static final int MAX_USER_RANK_PAGE_NUM = Integer.MAX_VALUE / MAX_USER_RANK_PAGE_SIZE;

	private static final long MAX_CHAT_DETAIL_EXPORT_ROWS = 100_000L;

	private static final Set<String> CHAT_DETAIL_STATUS_CODES = Set.of("0", "1", "2", "3", "9");

	private static final Set<String> CHAT_DETAIL_EVALUATE_CODES = Set.of("like", "dislike", "none");

	/**
	 * 向量存储服务 用于存储和检索标注的问答对
	 */
	private final EmbeddingStoreService embeddingStoreService;

	/**
	 * 数据集数据访问层
	 */
	private final AiDatasetMapper datasetMapper;

	/**
	 * 模型提供者 用于获取向量化模型
	 */
	private final ModelProvider modelProvider;

	private final org.springframework.beans.factory.ObjectProvider<com.deepdimen.matrix.knowledge.support.dataquery.DataQueryCorrectionService> dataQueryCorrections;

	@org.springframework.beans.factory.annotation.Autowired
	private org.springframework.beans.factory.ObjectProvider<com.deepdimen.matrix.knowledge.support.dataquery.DataQueryDatasetGuidance> datasetGuidance;

	@org.springframework.beans.factory.annotation.Autowired
	private org.springframework.beans.factory.ObjectProvider<com.deepdimen.matrix.knowledge.support.dataquery.DataQueryLegacyAnswerStore> legacyAnswers;

	/**
	 * 保存聊天记录
	 * <p>
	 * 更新已存在的聊天记录，添加AI响应内容 记录用户信息和LLM使用状态
	 * @param map 包含记录ID、IP、用户代理等上下文信息
	 * @param text AI生成的响应文本
	 * @param llmFlag 是否成功使用LLM生成响应
	 */
	@Override
	public void saveRecord(Map<Object, Object> map, String text, boolean llmFlag) {
		Long messageKey = MapUtil.getLong(map, AiChatRecordEntity.Fields.recordId);
		AiChatRecordEntity recordEntity = baseMapper.selectById(messageKey);
		if (Objects.isNull(recordEntity)) {
			return;
		}
		recordEntity.setAnswerText(text);
		recordEntity.setLlmFlag(llmFlag ? LLMUseStatusEnums.USED.getStatus() : LLMUseStatusEnums.FAILED.getStatus());

		String ip = MapUtil.getStr(map, AiChatRecordEntity.Fields.ip);
		String userAgent = MapUtil.getStr(map, AiChatRecordEntity.Fields.userAgent);
		String username = MapUtil.getStr(map, AiChatRecordEntity.Fields.username);
		Long useTime = MapUtil.getLong(map, AiChatRecordEntity.Fields.useTime);

		recordEntity.setIp(ip);
		recordEntity.setUserAgent(userAgent);
		recordEntity.setUsername(username);
		recordEntity.setUseTime(useTime);
		baseMapper.updateById(recordEntity);
	}

	/**
	 * 更新并标注聊天记录
	 * <p>
	 * 支持将优质问答对标注到向量数据库 用于后续的相似问题检索和答案推荐 如果取消标注，会从向量库中删除对应记录
	 * @param aiChatRecord 包含更新内容和标注状态的聊天记录
	 * @return 操作结果
	 */
	@Override
	@Transactional(rollbackFor = Exception.class)
	public R updateAndStandardRecord(AiChatRecordEntity aiChatRecord) {
		AiDatasetEntity aiDatasetEntity = datasetMapper.selectById(aiChatRecord.getDatasetId());
		// 如果存在向量ID，先删除旧的向量记录
		if (StrUtil.isNotBlank(aiChatRecord.getQdrantId())) {
			embeddingStoreService.delete(aiDatasetEntity.getCollectionName(), List.of(aiChatRecord.getQdrantId()));
		}

		DimensionAwareEmbeddingModel embeddingModel = modelProvider
			.getEmbeddingModel(aiDatasetEntity.getEmbeddingModel());
		// 如果开启标注，将问答对向量化后存入向量库
		if (YesNoEnum.YES.getCode().equals(aiChatRecord.getStandardFlag())) {
			Response<Embedding> embeddingResponse = embeddingModel.embed(aiChatRecord.getQuestionText());
			String qdrantId = embeddingStoreService.embeddingStore(aiDatasetEntity.getCollectionName())
				.add(embeddingResponse.content(),
						TextSegment.textSegment(aiChatRecord.getQuestionText(),
								new Metadata(Map.of(DocumentTypeEnums.Fields.type, DocumentTypeEnums.QUESTION.getType(),
										AiChatRecordEntity.Fields.datasetId, aiChatRecord.getDatasetId().toString(),
										AiChatRecordEntity.Fields.answerText, aiChatRecord.getAnswerText(),
										AiChatRecordEntity.Fields.recordId, aiChatRecord.getRecordId().toString()))));
			aiChatRecord.setQdrantId(qdrantId);
		}

		// 更新数据
		baseMapper.updateById(aiChatRecord);
		return R.ok();
	}

	/**
	 * 批量删除聊天记录和标注
	 * <p>
	 * 删除指定的聊天记录 同时从向量库中删除对应的标注数据 支持事务，确保数据一致性
	 * @param ids 要删除的记录ID数组
	 * @return 操作结果
	 */
	@Override
	@Transactional(rollbackFor = Exception.class)
	public R removeRecordAndStandardByIds(Long[] ids) {
		baseMapper.selectByIds(CollUtil.toList(ids)).forEach(aiChatRecordEntity -> {
			AiDatasetEntity aiDatasetEntity = datasetMapper.selectById(aiChatRecordEntity.getDatasetId());
			if (StrUtil.isNotBlank(aiChatRecordEntity.getQdrantId())) {
				embeddingStoreService.delete(aiDatasetEntity.getCollectionName(),
						List.of(aiChatRecordEntity.getQdrantId()));
			}
		});
		baseMapper.deleteByIds(CollUtil.toList(ids));
		return R.ok();
	}

	// ==================== 前台对话增强接口 ====================

	/**
	 * 消息评价（点赞/反对/取消）
	 */
	@Override
	public R evaluate(ChatEvaluateDTO chatEvaluateDTO) {
		AiChatRecordEntity record = baseMapper.selectById(chatEvaluateDTO.getMessageId());
		if (record == null) {
			return R.failed("消息记录不存在");
		}
		// 校验当前用户是否有权评价该消息
		if (SecurityUtils.getUser() == null) {
			return R.failed("未登录，无法评价");
		}
		String currentUsername = SecurityUtils.getUser().getUsername();
		if (!currentUsername.equals(record.getUsername())) {
			return R.failed("无权评价他人的消息");
		}
		// 校验评价类型合法性
		String type = chatEvaluateDTO.getType();
		if (type != null && !"like".equals(type) && !"dislike".equals(type)) {
			return R.failed("评价类型不合法，仅支持 like/dislike/null");
		}
		String feedbackId = null;
		if (chatEvaluateDTO.getQueryId() != null) {
			var corrections = dataQueryCorrections.getIfAvailable();
			if (corrections == null) {
				return R.failed("CONTROLLED_SERVICE_UNAVAILABLE");
			}
			try {
				feedbackId = corrections.feedback(
						com.deepdimen.matrix.knowledge.support.dataquery.DataQuerySecurity.identity(),
						chatEvaluateDTO.getQueryId(), chatEvaluateDTO.getCategory(), chatEvaluateDTO.getDescription(),
						chatEvaluateDTO.getMessageId(), chatEvaluateDTO.getRequestId());
			}
			catch (com.deepdimen.matrix.knowledge.support.dataquery.DataQueryException e) {
				return R.failed(e.getMessage());
			}
		}
		record.setEvaluate(type);
		baseMapper.updateById(record);
		return feedbackId == null ? R.ok() : R.ok(Map.of("feedbackId", feedbackId));
	}

	/**
	 * 前台历史消息查询（按conversationId + 当前用户过滤）
	 */
	@Override
	public List<AiChatRecordEntity> listByConversation(String conversationId, String username) {
		var records = list(com.baomidou.mybatisplus.core.toolkit.Wrappers.<AiChatRecordEntity>lambdaQuery()
			.eq(AiChatRecordEntity::getConversationId, conversationId)
			.eq(AiChatRecordEntity::getUsername, username)
			.orderByAsc(AiChatRecordEntity::getCreateTime));
		var corrections = dataQueryCorrections.getIfAvailable();
		if (corrections != null && !records.isEmpty()) {
			try {
				var identity = com.deepdimen.matrix.knowledge.support.dataquery.DataQuerySecurity.identity();
				if (!username.equals(SecurityUtils.getUser().getUsername()))
					throw new IllegalArgumentException("owner");
				for (int offset = 0; offset < records.size(); offset += 200) {
					var batch = records.subList(offset, Math.min(offset + 200, records.size()));
					var references = corrections.history(identity, username,
							batch.stream().map(AiChatRecordEntity::getRecordId).toList());
					for (var record : batch) {
						var reference = references.get(record.getRecordId());
						record.setDataQueryReferenceState(reference == null ? "NONE" : reference.state());
						record.setDataQuery(reference == null ? null : reference.dataQuery());
						record.setDataQueryFeedbackId(reference == null ? null : reference.feedbackId());
					}
				}
			}
			catch (RuntimeException unavailable) {
				// History remains readable, but missing provenance must not be replaced
				// with guessed/local IDs.
				for (var record : records) {
					record.setDataQueryReferenceState("UNAVAILABLE");
					record.setDataQuery(null);
					record.setDataQueryFeedbackId(null);
				}
			}
		}
		var legacy = legacyAnswers == null ? null : legacyAnswers.getIfAvailable();
		if (legacy != null) {
			for (var record : records) {
				if (Long.valueOf(-2).equals(record.getDatasetId()))
					record.setDatabaseChatState(legacy.state(record));
			}
		}
		var guidance = datasetGuidance == null ? null : datasetGuidance.getIfAvailable();
		if (guidance != null)
			for (var record : records)
				record.setMetadataGuidance(guidance.restore(record));
		return records;
	}

	@Override
	public boolean removeConversation(String conversationId, String username) {
		return remove(com.baomidou.mybatisplus.core.toolkit.Wrappers.<AiChatRecordEntity>lambdaQuery()
			.eq(AiChatRecordEntity::getConversationId, conversationId)
			.eq(AiChatRecordEntity::getUsername, username));
	}

	// ==================== Dashboard统计分析实现 ====================

	/**
	 * 获取Dashboard核心指标概览
	 */
	@Override
	public DashboardOverviewVO getDashboardOverview(DateRangeDTO params) {
		Map<String, Object> queryParams = buildQueryParams(params);

		// 查询当前周期数据
		Map<String, Object> currentStats = baseMapper.selectOverviewStats(queryParams);

		// 计算上一周期日期范围
		LocalDate startDate = LocalDate.parse(params.getStartDate());
		LocalDate endDate = LocalDate.parse(params.getEndDate());
		long daysBetween = ChronoUnit.DAYS.between(startDate, endDate) + 1;

		LocalDate prevEndDate = startDate.minusDays(1);
		LocalDate prevStartDate = prevEndDate.minusDays(daysBetween - 1);

		Map<String, Object> prevQueryParams = new HashMap<>();
		prevQueryParams.put("prevStartDate", prevStartDate.toString());
		prevQueryParams.put("prevEndDate", prevEndDate.toString());
		prevQueryParams.put("tenantId", requiredTenantId());

		// 查询上一周期数据
		Map<String, Object> prevStats = baseMapper.selectPreviousOverviewStats(prevQueryParams);

		// 构建响应对象
		DashboardOverviewVO overview = new DashboardOverviewVO();
		overview.setTotalChats(MapUtil.getLong(currentStats, "totalChats", 0L));
		overview.setTotalTokens(MapUtil.getLong(currentStats, "totalTokens", 0L));
		overview.setActiveUsers(MapUtil.getLong(currentStats, "activeUsers", 0L));
		overview.setAvgResponseTime(MapUtil.getLong(currentStats, "avgResponseTime", 0L));

		// 计算增长率
		DashboardOverviewVO.GrowthRate growthRate = new DashboardOverviewVO.GrowthRate();
		growthRate.setChats(calculateGrowthRate(MapUtil.getLong(prevStats, "totalChats", 0L),
				MapUtil.getLong(currentStats, "totalChats", 0L)));
		growthRate.setTokens(calculateGrowthRate(MapUtil.getLong(prevStats, "totalTokens", 0L),
				MapUtil.getLong(currentStats, "totalTokens", 0L)));
		growthRate.setUsers(calculateGrowthRate(MapUtil.getLong(prevStats, "activeUsers", 0L),
				MapUtil.getLong(currentStats, "activeUsers", 0L)));

		overview.setGrowthRate(growthRate);
		return overview;
	}

	/**
	 * 获取Token使用趋势
	 */
	@Override
	public TokenTrendVO getTokenTrend(DateRangeDTO params) {
		Map<String, Object> queryParams = buildQueryParams(params);
		List<Map<String, Object>> trendData = baseMapper.selectTokenTrend(queryParams);

		TokenTrendVO tokenTrend = new TokenTrendVO();
		tokenTrend.setDates(trendData.stream().map(m -> MapUtil.getStr(m, "date")).collect(Collectors.toList()));
		tokenTrend.setPromptTokens(
				trendData.stream().map(m -> MapUtil.getLong(m, "promptTokens", 0L)).collect(Collectors.toList()));
		tokenTrend.setCompletionTokens(
				trendData.stream().map(m -> MapUtil.getLong(m, "completionTokens", 0L)).collect(Collectors.toList()));
		tokenTrend.setTotalTokens(
				trendData.stream().map(m -> MapUtil.getLong(m, "totalTokens", 0L)).collect(Collectors.toList()));

		return tokenTrend;
	}

	@Override
	public ConversationSourceUsageVO getConversationSourceUsage(DateRangeDTO params) {
		ConversationSourceUsageVO result = new ConversationSourceUsageVO();
		result.setItems(baseMapper.selectConversationSourceUsage(buildQueryParams(params)));
		return result;
	}

	/**
	 * 获取用户活跃度排行榜
	 */
	@Override
	public UserRankingVO getUserRanking(DateRangeDTO params, Integer limit, String orderBy) {
		Map<String, Object> queryParams = buildQueryParams(params);
		queryParams.put("limit", limit != null ? limit : 10);
		queryParams.put("orderBy", orderBy != null ? orderBy : "chats");

		List<UserRankingVO.UserRankingItem> users = baseMapper.selectUserRanking(queryParams);

		UserRankingVO userRanking = new UserRankingVO();
		userRanking.setUsers(users);
		return userRanking;
	}

	/**
	 * 获取AI模型使用分布统计
	 */
	@Override
	public ModelDistributionVO getModelDistribution(DateRangeDTO params) {
		Map<String, Object> queryParams = buildQueryParams(params);
		List<ModelDistributionVO.ModelDistributionItem> models = baseMapper.selectModelDistribution(queryParams);

		ModelDistributionVO modelDistribution = new ModelDistributionVO();
		modelDistribution.setModels(models);
		return modelDistribution;
	}

	/**
	 * 获取LLM调用状态分布统计
	 */
	@Override
	public LLMStatusVO getLLMStatus(DateRangeDTO params) {
		Map<String, Object> queryParams = buildQueryParams(params);
		List<LLMStatusVO.LLMStatusItem> statuses = baseMapper.selectLLMStatus(queryParams);

		// 计算成功率（状态码为'1'的占比）
		Double successRate = statuses.stream()
			.filter(s -> "1".equals(s.getStatus()))
			.findFirst()
			.map(LLMStatusVO.LLMStatusItem::getPercentage)
			.orElse(0.0);

		LLMStatusVO llmStatus = new LLMStatusVO();
		llmStatus.setStatuses(statuses);
		llmStatus.setSuccessRate(successRate);
		return llmStatus;
	}

	/**
	 * 获取知识库使用统计
	 */
	@Override
	public DatasetUsageVO getDatasetUsage(DateRangeDTO params) {
		Map<String, Object> queryParams = buildQueryParams(params);
		List<DatasetUsageVO.DatasetUsageItem> datasets = baseMapper.selectDatasetUsage(queryParams);

		DatasetUsageVO datasetUsage = new DatasetUsageVO();
		datasetUsage.setDatasets(datasets);
		return datasetUsage;
	}

	/**
	 * 获取时段活跃度热力图数据
	 */
	@Override
	public HeatmapVO getHeatmapData(DateRangeDTO params) {
		Map<String, Object> queryParams = buildQueryParams(params);
		List<HeatmapVO.HeatmapItem> data = baseMapper.selectHeatmapData(queryParams);

		HeatmapVO heatmap = new HeatmapVO();
		heatmap.setData(data);
		return heatmap;
	}

	/**
	 * 获取响应时间分布统计（箱线图）
	 */
	@Override
	public ResponseTimeVO getResponseTime(DateRangeDTO params) {
		Map<String, Object> queryParams = buildQueryParams(params);
		List<Map<String, Object>> rawData = baseMapper.selectResponseTimeStats(queryParams);

		// 按模型分组
		Map<String, List<Long>> modelTimesMap = rawData.stream()
			.collect(Collectors.groupingBy(m -> MapUtil.getStr(m, "model"),
					Collectors.mapping(m -> MapUtil.getLong(m, "use_time"), Collectors.toList())));

		// 计算每个模型的箱线图数据
		List<ResponseTimeVO.ModelResponseTime> models = modelTimesMap.entrySet().stream().map(entry -> {
			String model = entry.getKey();
			List<Long> times = entry.getValue();
			Collections.sort(times);

			ResponseTimeVO.ModelResponseTime modelStats = new ResponseTimeVO.ModelResponseTime();
			modelStats.setModel(model);

			if (!times.isEmpty()) {
				int size = times.size();
				modelStats.setMin(times.get(0));
				modelStats.setMax(times.get(size - 1));
				modelStats.setMedian(times.get(size / 2));
				modelStats.setQ1(times.get(size / 4));
				modelStats.setQ3(times.get(size * 3 / 4));

				// 计算异常值（IQR方法）
				long iqr = modelStats.getQ3() - modelStats.getQ1();
				long lowerBound = modelStats.getQ1() - (long) (1.5 * iqr);
				long upperBound = modelStats.getQ3() + (long) (1.5 * iqr);

				List<Long> outliers = times.stream()
					.filter(t -> t < lowerBound || t > upperBound)
					.collect(Collectors.toList());
				modelStats.setOutliers(outliers);
			}

			return modelStats;
		}).collect(Collectors.toList());

		ResponseTimeVO responseTime = new ResponseTimeVO();
		responseTime.setModels(models);
		return responseTime;
	}

	/**
	 * 获取Token配额类型分布趋势
	 */
	@Override
	public TokenTypeVO getTokenTypeDistribution(DateRangeDTO params) {
		Map<String, Object> queryParams = buildQueryParams(params);
		List<TokenTypeVO.TokenTypeItem> data = baseMapper.selectTokenTypeDistribution(queryParams);

		TokenTypeVO tokenType = new TokenTypeVO();
		tokenType.setData(data);
		return tokenType;
	}

	/**
	 * 获取网络搜索使用情况统计
	 */
	@Override
	public WebSearchUsageVO getWebSearchUsage(DateRangeDTO params) {
		Map<String, Object> queryParams = buildQueryParams(params);
		Map<String, Object> usageData = baseMapper.selectWebSearchUsage(queryParams);

		Long withSearch = MapUtil.getLong(usageData, "withSearch", 0L);
		Long total = MapUtil.getLong(usageData, "total", 0L);

		WebSearchUsageVO webSearch = new WebSearchUsageVO();
		webSearch.setWithSearch(withSearch);
		webSearch.setTotal(total);

		// 计算使用率
		if (total > 0) {
			double rate = (withSearch * 100.0) / total;
			webSearch.setUsageRate(Math.round(rate * 10.0) / 10.0);
		}
		else {
			webSearch.setUsageRate(0.0);
		}

		return webSearch;
	}

	// ==================== 私有辅助方法 ====================

	/**
	 * 构建查询参数Map
	 */
	private Map<String, Object> buildQueryParams(DateRangeDTO params) {
		Map<String, Object> map = new HashMap<>();
		map.put("tenantId", requiredTenantId());
		map.put("startDate", params.getStartDate());
		map.put("endDate", params.getEndDate());
		if (StrUtil.isNotBlank(params.getInterval())) {
			map.put("interval", params.getInterval());
		}
		return map;
	}

	private String requiredTenantId() {
		Long tenantId = TenantContextHolder.getTenantId();
		if (tenantId == null) {
			throw new IllegalStateException("缺少租户上下文");
		}
		return tenantId.toString();
	}

	/**
	 * 计算增长率（百分比）
	 */
	private Double calculateGrowthRate(Long previous, Long current) {
		if (previous == null || previous == 0) {
			return current > 0 ? 100.0 : 0.0;
		}
		double rate = ((current - previous) * 100.0) / previous;
		return Math.round(rate * 10.0) / 10.0;
	}

	// ==================== 新增统计接口实现 ====================

	/**
	 * 获取用户使用统计
	 */
	@Override
	public UserStatsVO getUserStats() {
		String username = SecurityUtils.getUser().getUsername();

		LocalDate today = LocalDate.now();
		LocalDate currentMonthStart = today.withDayOfMonth(1);
		LocalDate lastMonthStart = currentMonthStart.minusMonths(1);
		LocalDate lastMonthEnd = currentMonthStart.minusDays(1);

		// 查询当月数据
		Map<String, Object> currentParams = new HashMap<>();
		currentParams.put("tenantId", requiredTenantId());
		currentParams.put("userId", username);
		currentParams.put("startDate", currentMonthStart.toString());
		currentParams.put("endDate", today.toString());
		Map<String, Object> currentData = baseMapper.selectUserStats(currentParams);

		// 查询上月数据
		Map<String, Object> lastParams = new HashMap<>();
		lastParams.put("tenantId", requiredTenantId());
		lastParams.put("userId", username);
		lastParams.put("startDate", lastMonthStart.toString());
		lastParams.put("endDate", lastMonthEnd.toString());
		Map<String, Object> lastData = baseMapper.selectUserStats(lastParams);

		long chatCount = MapUtil.getLong(currentData, "chatCount", 0L);
		long totalTokens = MapUtil.getLong(currentData, "totalTokens", 0L);
		long promptTokens = MapUtil.getLong(currentData, "promptTokens", 0L);
		long completionTokens = MapUtil.getLong(currentData, "completionTokens", 0L);
		long activeDays = MapUtil.getLong(currentData, "activeDays", 0L);
		long lastChatCount = MapUtil.getLong(lastData, "chatCount", 0L);
		long lastTotalTokens = MapUtil.getLong(lastData, "totalTokens", 0L);

		UserStatsVO vo = new UserStatsVO();
		vo.setChatCount(chatCount);
		vo.setTotalTokens(totalTokens);
		vo.setPromptTokens(promptTokens);
		vo.setCompletionTokens(completionTokens);
		vo.setTokenQuota(-1L);
		vo.setActiveDays(activeDays);

		if (lastChatCount > 0) {
			vo.setChatCountGrowth(Math.round((chatCount - lastChatCount) * 1000.0 / lastChatCount) / 10.0);
		}
		if (lastTotalTokens > 0) {
			vo.setTokenGrowth(Math.round((totalTokens - lastTotalTokens) * 1000.0 / lastTotalTokens) / 10.0);
		}

		int daysPassed = today.getDayOfMonth();
		vo.setActiveRate(Math.round(activeDays * 1000.0 / daysPassed) / 10.0);

		return vo;
	}

	/**
	 * 获取用户活跃度趋势
	 */
	@Override
	public List<UserTrendVO> getUserTrend(UserStatsQueryDTO query) {
		Map<String, Object> params = new HashMap<>();
		params.put("tenantId", requiredTenantId());
		params.put("userId", SecurityUtils.getUser().getUsername());
		int days = query.getDays() != null ? query.getDays() : 30;
		if (days < 1 || days > 365) {
			days = 30;
		}
		LocalDate endDate = LocalDate.now();
		LocalDate startDate = endDate.minusDays(days - 1);
		String startStr = startDate.toString();
		String endStr = endDate.toString();
		if (StrUtil.isNotBlank(query.getStartDate())) {
			try {
				startStr = LocalDate.parse(query.getStartDate()).toString();
			}
			catch (Exception ignored) {
			}
		}
		if (StrUtil.isNotBlank(query.getEndDate())) {
			try {
				endStr = LocalDate.parse(query.getEndDate()).toString();
			}
			catch (Exception ignored) {
			}
		}
		params.put("startDate", startStr);
		params.put("endDate", endStr);
		List<Map<String, Object>> trendData = baseMapper.selectUserTrend(params);
		Map<String, Long> dataMap = new HashMap<>();
		for (Map<String, Object> row : trendData) {
			dataMap.put(MapUtil.getStr(row, "date"), MapUtil.getLong(row, "chatCount", 0L));
		}
		List<UserTrendVO> result = new ArrayList<>();
		LocalDate start = LocalDate.parse(startStr);
		LocalDate end = LocalDate.parse(endStr);
		for (LocalDate d = start; !d.isAfter(end); d = d.plusDays(1)) {
			String dateStr = d.toString();
			result.add(new UserTrendVO(dateStr, dataMap.getOrDefault(dateStr, 0L)));
		}
		return result;
	}

	/**
	 * 获取当前用户最近对话列表
	 */
	@Override
	public List<RecentConversationVO> getRecentConversations(int limit, Long datasetId) {
		String username = SecurityUtils.getUser().getUsername();
		Map<String, Object> params = new HashMap<>();
		params.put("tenantId", requiredTenantId());
		params.put("userId", username);
		params.put("datasetId", datasetId);
		params.put("limit", Math.min(limit, 20));
		return baseMapper.selectRecentConversations(params);
	}

	/**
	 * 获取按部门统计
	 */
	@Override
	public DeptStatsVO getDeptStats(DateRangeDTO params) {
		Map<String, Object> queryParams = buildQueryParams(params);
		List<DeptStatsVO.DeptStatsItem> items = baseMapper.selectDeptStats(queryParams);
		DeptStatsVO vo = new DeptStatsVO();
		vo.setItems(items);
		return vo;
	}

	/**
	 * 获取按公司统计
	 */
	@Override
	public CompanyStatsVO getCompanyStats(DateRangeDTO params) {
		Map<String, Object> queryParams = buildQueryParams(params);
		List<CompanyStatsVO.CompanyStatsItem> items = baseMapper.selectCompanyStats(queryParams);
		CompanyStatsVO vo = new CompanyStatsVO();
		vo.setItems(items);
		return vo;
	}

	/**
	 * 获取智能体费用明细
	 */
	@Override
	public AgentCostVO getAgentCost(DateRangeDTO params) {
		Map<String, Object> queryParams = buildQueryParams(params);
		List<AgentCostVO.AgentCostItem> items = baseMapper.selectAgentCost(queryParams);
		AgentCostVO vo = new AgentCostVO();
		vo.setItems(items);
		return vo;
	}

	/**
	 * 获取按模型维度Token用量统计
	 */
	@Override
	public ModelStatsVO getModelStats(DateRangeDTO params, String modelName) {
		Map<String, Object> queryParams = buildQueryParams(params);
		queryParams.put("modelName", modelName);
		List<ModelStatsVO.ModelStatsItem> items = baseMapper.selectModelStats(queryParams);
		ModelStatsVO vo = new ModelStatsVO();
		vo.setItems(items);
		return vo;
	}

	/**
	 * 获取人员使用排行（含姓名和部门信息）
	 */
	@Override
	public UserRankVO getUserRank(DateRangeDTO params, Integer pageNum, Integer pageSize, String keyword,
			String sortBy) {
		int normalizedPageNum = pageNum == null || pageNum < 1 ? 1 : Math.min(pageNum, MAX_USER_RANK_PAGE_NUM);
		int normalizedPageSize = pageSize == null || pageSize < 1 ? 20 : Math.min(pageSize, MAX_USER_RANK_PAGE_SIZE);
		String normalizedSortBy = "tokens".equals(sortBy) ? "tokens" : "chatCount";
		String normalizedKeyword = StrUtil.trim(keyword);

		Map<String, Object> queryParams = buildQueryParams(params);
		long offset = (long) (normalizedPageNum - 1) * normalizedPageSize;
		queryParams.put("offset", offset);
		queryParams.put("pageSize", normalizedPageSize);
		queryParams.put("keyword", normalizedKeyword);
		queryParams.put("sortBy", normalizedSortBy);
		queryParams.put("tenantId", TenantContextHolder.getTenantId());

		List<UserRankVO.UserRankItem> items = TenantBroker.noneAs(() -> baseMapper.selectUserRank(queryParams));
		Long total = TenantBroker.noneAs(() -> baseMapper.countUserRank(queryParams));
		for (int i = 0; i < items.size(); i++) {
			items.get(i).setRank(Math.toIntExact(offset + i + 1));
		}

		UserRankVO vo = new UserRankVO();
		vo.setItems(items);
		vo.setTotal(total == null ? 0L : total);
		vo.setPageNum(normalizedPageNum);
		vo.setPageSize(normalizedPageSize);
		return vo;
	}

	@Override
	public Page<AiChatDetailVO> getChatDetailPage(Page<AiChatDetailVO> page, AiChatDetailQueryDTO query) {
		validateChatDetailQuery(query);
		List<AiChatDetailVO> records = baseMapper.selectChatDetailPage(page, query);
		records.forEach(this::convertChatDetailDisplayValues);
		page.setRecords(records);
		return page;
	}

	@Override
	public List<AiChatDetailVO> getChatDetailExportData(AiChatDetailQueryDTO query) {
		validateChatDetailQuery(query);
		long total = baseMapper.countChatDetails(query);
		if (total > MAX_CHAT_DETAIL_EXPORT_ROWS) {
			throw new IllegalArgumentException("当前筛选结果超过 100,000 条，请缩小筛选范围后重试");
		}
		List<AiChatDetailVO> records = total == 0 ? List.of() : baseMapper.selectChatDetailList(query);
		records.forEach(this::convertChatDetailDisplayValues);
		return records;
	}

	private void validateChatDetailQuery(AiChatDetailQueryDTO query) {
		query.setTenantId(requiredTenantId());
		if (query.getStartTime() != null && query.getEndTime() != null
				&& query.getStartTime().isAfter(query.getEndTime())) {
			throw new IllegalArgumentException("开始时间不能晚于结束时间");
		}
		if (StrUtil.isNotBlank(query.getResponseStatus())
				&& !CHAT_DETAIL_STATUS_CODES.contains(query.getResponseStatus())) {
			throw new IllegalArgumentException("响应状态不合法");
		}
		if (StrUtil.isNotBlank(query.getEvaluate()) && !CHAT_DETAIL_EVALUATE_CODES.contains(query.getEvaluate())) {
			throw new IllegalArgumentException("用户反馈不合法");
		}
	}

	private void convertChatDetailDisplayValues(AiChatDetailVO item) {
		item.setResponseStatus(switch (StrUtil.blankToDefault(item.getResponseStatus(), "unknown")) {
			case "0" -> "未使用";
			case "1" -> "已完成";
			case "2" -> "敏感词拦截";
			case "3" -> "用量受限";
			case "9" -> "处理失败";
			default -> "未知";
		});
		item.setEvaluate(switch (StrUtil.blankToDefault(item.getEvaluate(), "none")) {
			case "like" -> "正向";
			case "dislike" -> "负向";
			default -> "未评价";
		});
	}

}
