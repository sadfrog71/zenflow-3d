package com.deepdimen.matrix.knowledge.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.deepdimen.matrix.common.core.util.R;
import com.deepdimen.matrix.knowledge.dto.DashboardOverviewVO;
import com.deepdimen.matrix.knowledge.dto.DatasetUsageVO;
import com.deepdimen.matrix.knowledge.dto.DateRangeDTO;
import com.deepdimen.matrix.knowledge.dto.HeatmapVO;
import com.deepdimen.matrix.knowledge.dto.LLMStatusVO;
import com.deepdimen.matrix.knowledge.dto.ModelDistributionVO;
import com.deepdimen.matrix.knowledge.dto.ResponseTimeVO;
import com.deepdimen.matrix.knowledge.dto.TokenTrendVO;
import com.deepdimen.matrix.knowledge.dto.TokenTypeVO;
import com.deepdimen.matrix.knowledge.dto.UserRankingVO;
import com.deepdimen.matrix.knowledge.dto.WebSearchUsageVO;
import com.deepdimen.matrix.knowledge.dto.*;
import com.deepdimen.matrix.knowledge.entity.AiChatRecordEntity;

import java.util.List;
import java.util.Map;

/**
 * AI聊天记录服务接口 管理AI对话的历史记录、数据标注和训练样本
 */
public interface AiChatRecordService extends IService<AiChatRecordEntity> {

	/**
	 * 保存聊天记录 记录用户与AI的每次对话内容，用于历史查询和模型优化
	 * @param map 上下文参数，包含会话ID、用户信息等
	 * @param text 对话文本内容
	 * @param llmFlag LLM标志，true表示AI回复，false表示用户输入
	 */
	void saveRecord(Map<Object, Object> map, String text, boolean llmFlag);

	/**
	 * 更新并标注聊天记录 对历史对话进行质量标注，用于模型训练和优化
	 * @param aiChatRecord 包含更新内容和标注信息的聊天记录实体
	 * @return 操作结果
	 */
	R updateAndStandardRecord(AiChatRecordEntity aiChatRecord);

	/**
	 * 批量删除聊天记录及其标注 同时删除聊天记录和相关的标注数据
	 * @param ids 需要删除的记录ID数组
	 * @return 操作结果
	 */
	R removeRecordAndStandardByIds(Long[] ids);

	/**
	 * 消息评价（点赞/反对/取消）
	 * @param chatEvaluateDTO 评价请求体
	 * @return 操作结果
	 */
	R evaluate(ChatEvaluateDTO chatEvaluateDTO);

	/**
	 * 前台历史消息查询（按conversationId + 当前用户过滤）
	 * @param conversationId 会话ID
	 * @param username 当前用户名
	 * @return 对话记录列表
	 */
	List<AiChatRecordEntity> listByConversation(String conversationId, String username);

	/**
	 * 删除当前用户的指定会话记录。
	 */
	boolean removeConversation(String conversationId, String username);

	// ==================== Dashboard统计分析接口 ====================

	/**
	 * 获取Dashboard核心指标概览
	 * @param params 日期范围查询参数
	 * @return 核心指标概览数据
	 */
	DashboardOverviewVO getDashboardOverview(DateRangeDTO params);

	/**
	 * 获取Token使用趋势
	 * @param params 日期范围查询参数
	 * @return Token趋势数据
	 */
	TokenTrendVO getTokenTrend(DateRangeDTO params);

	ConversationSourceUsageVO getConversationSourceUsage(DateRangeDTO params);

	/**
	 * 获取用户活跃度排行榜
	 * @param params 日期范围查询参数
	 * @param limit 返回记录数量限制
	 * @param orderBy 排序字段（chats-按对话数，tokens-按Token数）
	 * @return 用户排名数据
	 */
	UserRankingVO getUserRanking(DateRangeDTO params, Integer limit, String orderBy);

	/**
	 * 获取AI模型使用分布统计
	 * @param params 日期范围查询参数
	 * @return 模型分布数据
	 */
	ModelDistributionVO getModelDistribution(DateRangeDTO params);

	/**
	 * 获取LLM调用状态分布统计
	 * @param params 日期范围查询参数
	 * @return LLM状态分布数据
	 */
	LLMStatusVO getLLMStatus(DateRangeDTO params);

	/**
	 * 获取知识库使用统计
	 * @param params 日期范围查询参数
	 * @return 知识库使用统计数据
	 */
	DatasetUsageVO getDatasetUsage(DateRangeDTO params);

	/**
	 * 获取时段活跃度热力图数据
	 * @param params 日期范围查询参数
	 * @return 热力图数据
	 */
	HeatmapVO getHeatmapData(DateRangeDTO params);

	/**
	 * 获取响应时间分布统计
	 * @param params 日期范围查询参数
	 * @return 响应时间分布数据
	 */
	ResponseTimeVO getResponseTime(DateRangeDTO params);

	/**
	 * 获取Token配额类型分布趋势
	 * @param params 日期范围查询参数
	 * @return Token类型分布数据
	 */
	TokenTypeVO getTokenTypeDistribution(DateRangeDTO params);

	/**
	 * 获取网络搜索使用情况统计
	 * @param params 日期范围查询参数
	 * @return 网络搜索使用数据
	 */
	WebSearchUsageVO getWebSearchUsage(DateRangeDTO params);

	// ==================== 新增统计接口 ====================

	/**
	 * 获取当前用户当月使用统计（含环比变化率和活跃率）
	 * @return 用户使用统计数据
	 */
	UserStatsVO getUserStats();

	/**
	 * 获取用户活跃度趋势
	 * @param query 查询参数（startDate、endDate、days）
	 * @return 用户活跃度趋势数据
	 */
	List<UserTrendVO> getUserTrend(UserStatsQueryDTO query);

	/**
	 * 获取按部门统计
	 * @param params 日期范围查询参数
	 * @return 部门统计数据
	 */
	DeptStatsVO getDeptStats(DateRangeDTO params);

	/**
	 * 获取按公司统计
	 * @param params 日期范围查询参数
	 * @return 公司统计数据
	 */
	CompanyStatsVO getCompanyStats(DateRangeDTO params);

	/**
	 * 获取智能体费用明细
	 * @param params 日期范围查询参数
	 * @return 智能体费用明细数据
	 */
	AgentCostVO getAgentCost(DateRangeDTO params);

	/**
	 * 获取按模型维度Token用量统计
	 * @param params 日期范围查询参数
	 * @param modelName 模型名称过滤（可选）
	 * @return 模型统计数据
	 */
	ModelStatsVO getModelStats(DateRangeDTO params, String modelName);

	/**
	 * 获取人员使用排行（含姓名和部门信息）
	 * @param params 日期范围查询参数
	 * @param pageNum 当前页码（默认1）
	 * @param pageSize 每页数量（默认20）
	 * @param keyword 用户名或姓名过滤（可选）
	 * @param sortBy 排序字段（chatCount-按调用量，tokens-按Token消耗）
	 * @return 人员排行数据
	 */
	UserRankVO getUserRank(DateRangeDTO params, Integer pageNum, Integer pageSize, String keyword, String sortBy);

	/**
	 * 获取当前用户最近对话列表（按conversationId去重，每个会话只取最新一条）
	 * @param limit 返回记录数量限制
	 * @param datasetId 知识库ID（可选）
	 * @return 最近对话列表
	 */
	List<RecentConversationVO> getRecentConversations(int limit, Long datasetId);

	Page<AiChatDetailVO> getChatDetailPage(Page<AiChatDetailVO> page, AiChatDetailQueryDTO query);

	List<AiChatDetailVO> getChatDetailExportData(AiChatDetailQueryDTO query);

}
