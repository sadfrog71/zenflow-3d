package com.deepdimen.matrix.knowledge.controller;

import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.util.ArrayUtil;
import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.deepdimen.matrix.knowledge.dto.DateRangeDTO;
import com.deepdimen.matrix.knowledge.dto.AiChatDetailQueryDTO;
import com.deepdimen.matrix.knowledge.dto.AiChatDetailVO;
import com.deepdimen.matrix.knowledge.dto.AiChatDetailExcelVO;
import com.deepdimen.matrix.knowledge.excel.AiChatDetailExcelStyleHandler;
import cn.idev.excel.EasyExcel;
import com.deepdimen.matrix.knowledge.dto.UserStatsQueryDTO;
import com.github.yulichang.wrapper.MPJLambdaWrapper;
import com.deepdimen.matrix.common.core.util.R;
import com.deepdimen.matrix.common.excel.annotation.ResponseExcel;
import com.deepdimen.matrix.common.log.annotation.SysLog;
import com.deepdimen.matrix.common.security.annotation.HasPermission;
import com.deepdimen.matrix.common.security.annotation.Inner;
import com.deepdimen.matrix.knowledge.entity.AiChatRecordEntity;
import com.deepdimen.matrix.knowledge.entity.AiDatasetEntity;
import com.deepdimen.matrix.knowledge.service.AiChatRecordService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.http.HttpHeaders;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Objects;
import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/**
 * AI聊天记录控制器 管理AI聊天对话记录，包括用户提问、AI回答、数据集关联等信息的维护
 *
 * @author matrix
 * @date 2024-07-03 12:39:32
 */
@RestController
@RequiredArgsConstructor
@RequestMapping("/aiChatRecord")
@Tag(description = "aiChatRecord", name = "聊天记录管理")
@SecurityRequirement(name = HttpHeaders.AUTHORIZATION)
public class AiChatRecordController {

	private final AiChatRecordService aiChatRecordService;

	/**
	 * 分页查询聊天记录 支持按用户名、问题文本、标准问题标识、大模型标识、数据集等条件筛选 查询结果包含关联的数据集名称
	 * @param page 分页对象
	 * @param aiChatRecord 查询条件
	 * @return 分页聊天记录列表
	 */
	@Operation(summary = "分页查询", description = "分页查询")
	@GetMapping("/page")
	@HasPermission("knowledge_aiChatRecord_view")
	public R getAiChatRecordPage(@ParameterObject Page page, @ParameterObject AiChatRecordEntity aiChatRecord) {
		MPJLambdaWrapper<AiChatRecordEntity> wrapper = new MPJLambdaWrapper<AiChatRecordEntity>()
			.selectAll(AiChatRecordEntity.class)
			.selectAs(AiDatasetEntity::getName, AiChatRecordEntity.Fields.datasetName)
			.leftJoin(AiDatasetEntity.class, AiDatasetEntity::getId, AiChatRecordEntity::getDatasetId);
		wrapper.like(StrUtil.isNotBlank(aiChatRecord.getUsername()), AiChatRecordEntity::getUsername,
				aiChatRecord.getUsername());
		wrapper.like(StrUtil.isNotBlank(aiChatRecord.getQuestionText()), AiChatRecordEntity::getQuestionText,
				aiChatRecord.getQuestionText());
		wrapper.eq(StrUtil.isNotBlank(aiChatRecord.getStandardFlag()), AiChatRecordEntity::getStandardFlag,
				aiChatRecord.getStandardFlag());
		wrapper.eq(StrUtil.isNotBlank(aiChatRecord.getLlmFlag()), AiChatRecordEntity::getLlmFlag,
				aiChatRecord.getLlmFlag());
		wrapper.eq(Objects.nonNull(aiChatRecord.getDatasetId()), AiChatRecordEntity::getDatasetId,
				aiChatRecord.getDatasetId());
		wrapper.isNotNull(AiDatasetEntity::getName);
		return R.ok(aiChatRecordService.page(page, wrapper));
	}

	/**
	 * 查询聊天记录详情 根据记录ID获取单条聊天记录的详细信息
	 * @param id 记录ID
	 * @return 聊天记录详情
	 */
	@Operation(summary = "通过id查询", description = "通过id查询")
	@GetMapping("/{id}")
	@HasPermission("knowledge_aiChatRecord_view")
	public R getById(@PathVariable("id") Long id) {
		return R.ok(aiChatRecordService.getById(id));
	}

	/**
	 * 新增聊天记录 保存新的AI聊天对话记录
	 * @param aiChatRecord 聊天记录实体
	 * @return 保存结果
	 */
	@Operation(summary = "新增聊天记录", description = "新增聊天记录")
	@SysLog("新增聊天记录")
	@PostMapping
	@HasPermission("knowledge_aiChatRecord_add")
	public R save(@RequestBody AiChatRecordEntity aiChatRecord) {
		return R.ok(aiChatRecordService.save(aiChatRecord));
	}

	/**
	 * 修改聊天记录 更新聊天记录信息，包括标准问题等关联数据
	 * @param aiChatRecord 聊天记录实体
	 * @return 更新结果
	 */
	@Operation(summary = "修改聊天记录", description = "修改聊天记录")
	@SysLog("修改聊天记录")
	@PutMapping
	@HasPermission("knowledge_aiChatRecord_edit")
	public R updateById(@Valid @RequestBody AiChatRecordEntity aiChatRecord) {
		return aiChatRecordService.updateAndStandardRecord(aiChatRecord);
	}

	/**
	 * 批量删除聊天记录 根据ID列表删除聊天记录及其关联的标准问题数据
	 * @param ids 记录ID数组
	 * @return 删除结果
	 */
	@Operation(summary = "通过id删除聊天记录", description = "通过id删除聊天记录")
	@SysLog("通过id删除聊天记录")
	@DeleteMapping
	@HasPermission("knowledge_aiChatRecord_del")
	public R removeById(@RequestBody Long[] ids) {
		return aiChatRecordService.removeRecordAndStandardByIds(ids);
	}

	/**
	 * 导出聊天记录Excel 根据查询条件或指定ID导出聊天记录到Excel文件
	 * @param aiChatRecord 查询条件
	 * @param ids 指定导出的记录ID数组（可选）
	 * @return 聊天记录列表
	 */
	@ResponseExcel
	@GetMapping("/export")
	@HasPermission("knowledge_aiChatRecord_export")
	public List<AiChatRecordEntity> export(AiChatRecordEntity aiChatRecord, Long[] ids) {
		return aiChatRecordService.list(
					Wrappers.lambdaQuery(aiChatRecord)
						.in(ArrayUtil.isNotEmpty(ids), AiChatRecordEntity::getRecordId, CollUtil.toList(ids)));
	}

	// ==================== Dashboard统计分析接口 ====================

	/**
	 * 获取Dashboard核心指标概览 包含总对话数、总Token消耗、活跃用户数、平均响应时间及其增长率
	 * @param params 日期范围查询参数
	 * @return 核心指标概览数据
	 */
	@Operation(summary = "Dashboard核心指标概览", description = "获取Dashboard核心指标概览")
	@GetMapping("/dashboard/overview")
	@HasPermission("knowledge_aiChatRecord_view")
	public R getDashboardOverview(@ParameterObject DateRangeDTO params) {
		return R.ok(aiChatRecordService.getDashboardOverview(params));
	}

	/**
	 * 获取Token使用趋势 展示promptTokens、completionTokens、totalTokens的时间变化
	 * @param params 日期范围查询参数
	 * @return Token趋势数据
	 */
	@Operation(summary = "Token使用趋势", description = "获取Token使用趋势")
	@GetMapping("/dashboard/token-trend")
	@HasPermission("knowledge_aiChatRecord_view")
	public R getTokenTrend(@ParameterObject DateRangeDTO params) {
		return R.ok(aiChatRecordService.getTokenTrend(params));
	}

	/**
	 * 获取用户活跃度排行榜 Top N用户按对话次数或Token消耗排序
	 * @param params 日期范围查询参数
	 * @param limit 返回记录数量限制（默认10）
	 * @param orderBy 排序字段（chats-按对话数，tokens-按Token数，默认chats）
	 * @return 用户排名数据
	 */
	@Operation(summary = "用户活跃度排行榜", description = "获取用户活跃度排行榜")
	@GetMapping("/dashboard/user-ranking")
	@HasPermission("knowledge_aiChatRecord_view")
	public R getUserRanking(@ParameterObject DateRangeDTO params,
			@RequestParam(required = false, defaultValue = "10") Integer limit,
			@RequestParam(required = false, defaultValue = "chats") String orderBy) {
		return R.ok(aiChatRecordService.getUserRanking(params, limit, orderBy));
	}

	@Operation(summary = "Token 使用来源分布", description = "区分旧版对话、统一助手与工作简报")
	@GetMapping("/dashboard/source-usage")
	@HasPermission("knowledge_aiChatRecord_view")
	public R getConversationSourceUsage(@ParameterObject DateRangeDTO params) {
		return R.ok(aiChatRecordService.getConversationSourceUsage(params));
	}

	/**
	 * 获取AI模型使用分布统计 统计各模型的调用次数、Token消耗和使用占比
	 * @param params 日期范围查询参数
	 * @return 模型分布数据
	 */
	@Operation(summary = "AI模型使用分布", description = "获取AI模型使用分布统计")
	@GetMapping("/dashboard/model-distribution")
	@HasPermission("knowledge_aiChatRecord_view")
	public R getModelDistribution(@ParameterObject DateRangeDTO params) {
		return R.ok(aiChatRecordService.getModelDistribution(params));
	}

	/**
	 * 获取LLM调用状态分布统计 展示成功、失败、敏感词、受限等各种状态的统计信息
	 * @param params 日期范围查询参数
	 * @return LLM状态分布数据
	 */
	@Operation(summary = "LLM调用状态分布", description = "获取LLM调用状态分布统计")
	@GetMapping("/dashboard/llm-status")
	@HasPermission("knowledge_aiChatRecord_view")
	public R getLLMStatus(@ParameterObject DateRangeDTO params) {
		return R.ok(aiChatRecordService.getLLMStatus(params));
	}

	/**
	 * 获取知识库使用统计 统计各知识库的调用次数和平均匹配度
	 * @param params 日期范围查询参数
	 * @return 知识库使用统计数据
	 */
	@Operation(summary = "知识库使用统计", description = "获取知识库使用统计")
	@GetMapping("/dashboard/dataset-usage")
	@HasPermission("knowledge_aiChatRecord_view")
	public R getDatasetUsage(@ParameterObject DateRangeDTO params) {
		return R.ok(aiChatRecordService.getDatasetUsage(params));
	}

	/**
	 * 获取时段活跃度热力图数据 展示24小时x7天的对话分布
	 * @param params 日期范围查询参数
	 * @return 热力图数据
	 */
	@Operation(summary = "时段活跃度热力图", description = "获取时段活跃度热力图数据")
	@GetMapping("/dashboard/heatmap")
	@HasPermission("knowledge_aiChatRecord_view")
	public R getHeatmapData(@ParameterObject DateRangeDTO params) {
		return R.ok(aiChatRecordService.getHeatmapData(params));
	}

	/**
	 * 获取响应时间分布统计 按模型展示响应时间的统计分布（箱线图）
	 * @param params 日期范围查询参数
	 * @return 响应时间分布数据
	 */
	@Operation(summary = "响应时间分布", description = "获取响应时间分布统计")
	@GetMapping("/dashboard/response-time")
	@HasPermission("knowledge_aiChatRecord_view")
	public R getResponseTime(@ParameterObject DateRangeDTO params) {
		return R.ok(aiChatRecordService.getResponseTime(params));
	}

	/**
	 * 获取Token配额类型分布趋势 展示系统配额Token和用户配额Token的时间分布
	 * @param params 日期范围查询参数
	 * @return Token类型分布数据
	 */
	@Operation(summary = "Token配额类型分布", description = "获取Token配额类型分布趋势")
	@GetMapping("/dashboard/token-type-distribution")
	@HasPermission("knowledge_aiChatRecord_view")
	public R getTokenTypeDistribution(@ParameterObject DateRangeDTO params) {
		return R.ok(aiChatRecordService.getTokenTypeDistribution(params));
	}

	/**
	 * 获取网络搜索使用情况统计 统计使用了网络搜索功能的对话数量及使用率
	 * @param params 日期范围查询参数
	 * @return 网络搜索使用数据
	 */
	@Operation(summary = "网络搜索使用情况", description = "获取网络搜索使用情况统计")
	@GetMapping("/dashboard/web-search-usage")
	@HasPermission("knowledge_aiChatRecord_view")
	public R getWebSearchUsage(@ParameterObject DateRangeDTO params) {
		return R.ok(aiChatRecordService.getWebSearchUsage(params));
	}

	// ==================== 新增统计接口 ====================

	/**
	 * 获取用户使用统计（对话次数、Token用量、活跃天数）
	 * @param query 用户统计查询参数
	 * @return 用户使用统计数据
	 */
	@Operation(summary = "用户使用统计", description = "获取当前用户当月使用统计，含环比变化率和活跃率")
	@GetMapping("/dashboard/userStats")
	@Inner(value = false)
	public R getUserStats() {
		return R.ok(aiChatRecordService.getUserStats());
	}

	/**
	 * 获取用户活跃度趋势
	 * @param query 用户统计查询参数
	 * @return 用户活跃度趋势数据
	 */
	@Operation(summary = "用户活跃度趋势", description = "获取用户活跃度趋势，支持7/30/365天切换")
	@GetMapping("/dashboard/userTrend")
	@Inner(value = false)
	public R getUserTrend(@ParameterObject UserStatsQueryDTO query) {
		return R.ok(aiChatRecordService.getUserTrend(query));
	}

	/**
	 * 获取当前用户最近对话列表（按conversationId去重，每个会话只取最新一条）
	 * @param limit 返回记录数量（默认3）
	 * @param datasetId 知识库ID（可选）
	 * @return 最近对话列表
	 */
	@Operation(summary = "最近对话列表", description = "获取当前用户最近N条去重对话记录，按conversationId聚合取最新")
	@GetMapping("/recent")
	@Inner(value = false)
	public R getRecentConversations(@RequestParam(required = false, defaultValue = "3") int limit,
			@RequestParam(required = false) Long datasetId) {
		return R.ok(aiChatRecordService.getRecentConversations(limit, datasetId));
	}

	/**
	 * 获取按部门统计
	 * @param params 日期范围查询参数
	 * @return 部门统计数据
	 */
	@Operation(summary = "按部门统计", description = "获取按部门维度的对话次数和Token用量统计")
	@GetMapping("/dashboard/deptStats")
	@HasPermission("knowledge_aiChatRecord_view")
	public R getDeptStats(@ParameterObject DateRangeDTO params) {
		return R.ok(aiChatRecordService.getDeptStats(params));
	}

	/**
	 * 获取按公司统计
	 * @param params 日期范围查询参数
	 * @return 公司统计数据
	 */
	@Operation(summary = "按公司统计", description = "获取按公司（租户）维度的对话次数和Token用量统计")
	@GetMapping("/dashboard/companyStats")
	@HasPermission("knowledge_aiChatRecord_view")
	public R getCompanyStats(@ParameterObject DateRangeDTO params) {
		return R.ok(aiChatRecordService.getCompanyStats(params));
	}

	/**
	 * 获取智能体费用明细
	 * @param params 日期范围查询参数
	 * @return 智能体费用明细数据
	 */
	@Operation(summary = "智能体费用明细", description = "获取按智能体和模型维度的Token消耗量统计")
	@GetMapping("/dashboard/agentCost")
	@HasPermission("knowledge_aiChatRecord_view")
	public R getAgentCost(@ParameterObject DateRangeDTO params) {
		return R.ok(aiChatRecordService.getAgentCost(params));
	}

	/**
	 * 获取按模型维度Token用量统计
	 * @param params 日期范围查询参数
	 * @param modelName 模型名称过滤（可选）
	 * @return 模型统计数据
	 */
	@Operation(summary = "按模型维度Token用量统计", description = "获取按模型维度的Token用量统计，支持modelName过滤")
	@GetMapping("/dashboard/modelStats")
	@HasPermission("knowledge_aiChatRecord_view")
	public R getModelStats(@ParameterObject DateRangeDTO params, @RequestParam(required = false) String modelName) {
		return R.ok(aiChatRecordService.getModelStats(params, modelName));
	}

	/**
	 * 获取人员使用排行（含姓名和部门信息）
	 * @param params 日期范围查询参数
	 * @param pageNum 当前页码（默认1）
	 * @param pageSize 每页数量（默认20）
	 * @param keyword 用户名或姓名过滤（可选）
	 * @param sortBy 排序字段（chatCount-按调用量，tokens-按Token消耗）
	 * @return 人员排行数据
	 */
	@Operation(summary = "人员使用排行", description = "分页获取人员的调用量和Token消耗排行，支持用户名或姓名过滤，含姓名和部门信息")
	@GetMapping("/dashboard/userRank")
	@Inner(value = false)
	public R getUserRank(@ParameterObject DateRangeDTO params,
			@RequestParam(required = false, defaultValue = "1") Integer pageNum,
			@RequestParam(required = false, defaultValue = "20") Integer pageSize,
			@RequestParam(required = false) String keyword,
			@RequestParam(required = false, defaultValue = "chatCount") String sortBy) {
		return R.ok(aiChatRecordService.getUserRank(params, pageNum, pageSize, keyword, sortBy));
	}

	@Operation(summary = "AI问答明细分页查询")
	@GetMapping("/detail/page")
	@HasPermission("knowledge_aiChatDetail_view")
	public R getChatDetailPage(@ParameterObject Page<AiChatDetailVO> page,
			@ParameterObject AiChatDetailQueryDTO query) {
		return R.ok(aiChatRecordService.getChatDetailPage(page, query));
	}

	@Operation(summary = "导出AI问答明细")
	@GetMapping("/detail/export")
	@HasPermission("knowledge_aiChatDetail_export")
	public void exportChatDetails(@ParameterObject AiChatDetailQueryDTO query, HttpServletResponse response)
			throws IOException {
		List<AiChatDetailExcelVO> rows = aiChatRecordService.getChatDetailExportData(query)
			.stream()
			.map(AiChatDetailExcelVO::from)
			.toList();
		String timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMddHHmmss"));
		String fileName = URLEncoder.encode("AI问答明细_" + timestamp, StandardCharsets.UTF_8).replace("+", "%20");
		response.setContentType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");
		response.setCharacterEncoding(StandardCharsets.UTF_8.name());
		response.setHeader(HttpHeaders.CONTENT_DISPOSITION, "attachment;filename*=UTF-8''" + fileName + ".xlsx");
		EasyExcel.write(response.getOutputStream(), AiChatDetailExcelVO.class)
			.registerWriteHandler(new AiChatDetailExcelStyleHandler())
			.sheet("AI问答明细")
			.doWrite(rows);
	}

}
