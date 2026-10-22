package com.deepdimen.matrix.knowledge.mapper;

import com.deepdimen.matrix.common.data.datascope.MatrixBaseMapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.deepdimen.matrix.knowledge.dto.*;
import com.deepdimen.matrix.knowledge.entity.AiChatRecordEntity;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;
import java.util.Map;

/**
 * AI聊天记录数据访问接口
 * <p>
 * 提供AI对话历史的数据库访问操作 管理用户与AI的问答记录、模型使用情况和知识库引用信息
 *
 * @author matrix
 * @date 2024-07-03 12:39:32
 */
@Mapper
public interface AiChatRecordMapper extends MatrixBaseMapper<AiChatRecordEntity> {

	/**
	 * 查询核心指标概览统计数据
	 * @param params 查询参数（包含startDate、endDate）
	 * @return 核心指标统计数据
	 */
	Map<String, Object> selectOverviewStats(@Param("params") Map<String, Object> params);

	/**
	 * 查询上一周期核心指标统计数据（用于计算增长率）
	 * @param params 查询参数（包含startDate、endDate）
	 * @return 上一周期核心指标统计数据
	 */
	Map<String, Object> selectPreviousOverviewStats(@Param("params") Map<String, Object> params);

	/**
	 * 查询Token使用趋势数据
	 * @param params 查询参数（包含startDate、endDate、interval）
	 * @return Token趋势数据列表
	 */
	List<Map<String, Object>> selectTokenTrend(@Param("params") Map<String, Object> params);

	List<ConversationSourceUsageVO.SourceUsageItem> selectConversationSourceUsage(
			@Param("params") Map<String, Object> params);

	/**
	 * 查询用户活跃度排行榜
	 * @param params 查询参数（包含startDate、endDate、limit、orderBy）
	 * @return 用户排名列表
	 */
	List<UserRankingVO.UserRankingItem> selectUserRanking(@Param("params") Map<String, Object> params);

	/**
	 * 查询模型使用分布统计
	 * @param params 查询参数（包含startDate、endDate）
	 * @return 模型分布列表
	 */
	List<ModelDistributionVO.ModelDistributionItem> selectModelDistribution(
			@Param("params") Map<String, Object> params);

	/**
	 * 查询LLM调用状态分布统计
	 * @param params 查询参数（包含startDate、endDate）
	 * @return LLM状态分布列表
	 */
	List<LLMStatusVO.LLMStatusItem> selectLLMStatus(@Param("params") Map<String, Object> params);

	/**
	 * 查询知识库使用统计（包含平均匹配度）
	 * @param params 查询参数（包含startDate、endDate）
	 * @return 知识库使用统计列表
	 */
	List<DatasetUsageVO.DatasetUsageItem> selectDatasetUsage(@Param("params") Map<String, Object> params);

	/**
	 * 查询时段活跃度热力图数据（按小时和星期分组）
	 * @param params 查询参数（包含startDate、endDate）
	 * @return 热力图数据列表
	 */
	List<HeatmapVO.HeatmapItem> selectHeatmapData(@Param("params") Map<String, Object> params);

	/**
	 * 查询响应时间分布数据（按模型分组）
	 * @param params 查询参数（包含startDate、endDate）
	 * @return 响应时间统计列表
	 */
	List<Map<String, Object>> selectResponseTimeStats(@Param("params") Map<String, Object> params);

	/**
	 * 查询Token配额类型分布趋势
	 * @param params 查询参数（包含startDate、endDate）
	 * @return Token类型分布列表
	 */
	List<TokenTypeVO.TokenTypeItem> selectTokenTypeDistribution(@Param("params") Map<String, Object> params);

	/**
	 * 查询网络搜索使用统计
	 * @param params 查询参数（包含startDate、endDate）
	 * @return 网络搜索使用数据
	 */
	Map<String, Object> selectWebSearchUsage(@Param("params") Map<String, Object> params);

	/**
	 * 查询用户使用统计（对话次数、Token用量、活跃天数）
	 * @param params 查询参数（包含userId、startDate、endDate）
	 * @return 用户使用统计数据
	 */
	Map<String, Object> selectUserStats(@Param("params") Map<String, Object> params);

	/**
	 * 查询用户活跃度趋势（按天分组）
	 * @param params 查询参数（包含userId、startDate、endDate）
	 * @return 每日对话次数列表
	 */
	List<Map<String, Object>> selectUserTrend(@Param("params") Map<String, Object> params);

	/**
	 * 查询按部门统计（通过username关联sys_user获取dept_id）
	 * @param params 查询参数（包含startDate、endDate）
	 * @return 部门统计列表
	 */
	List<DeptStatsVO.DeptStatsItem> selectDeptStats(@Param("params") Map<String, Object> params);

	/**
	 * 查询按公司统计（按tenant_id分组关联sys_tenant）
	 * @param params 查询参数（包含startDate、endDate）
	 * @return 公司统计列表
	 */
	List<CompanyStatsVO.CompanyStatsItem> selectCompanyStats(@Param("params") Map<String, Object> params);

	/**
	 * 查询智能体费用明细（按智能体和模型维度统计Token消耗量）
	 * @param params 查询参数（包含startDate、endDate）
	 * @return 费用明细列表
	 */
	List<AgentCostVO.AgentCostItem> selectAgentCost(@Param("params") Map<String, Object> params);

	/**
	 * 查询按模型维度Token用量统计
	 * @param params 查询参数（包含startDate、endDate、modelName可选）
	 * @return 模型统计列表
	 */
	List<ModelStatsVO.ModelStatsItem> selectModelStats(@Param("params") Map<String, Object> params);

	/**
	 * 查询人员使用排行（含姓名和部门信息）
	 * @param params 查询参数（包含startDate、endDate、offset、pageSize、keyword、sortBy）
	 * @return 人员排行列表
	 */
	List<UserRankVO.UserRankItem> selectUserRank(@Param("params") Map<String, Object> params);

	/**
	 * 查询人员使用排行总数
	 * @param params 查询参数（包含startDate、endDate、keyword）
	 * @return 人员排行总数
	 */
	Long countUserRank(@Param("params") Map<String, Object> params);

	/**
	 * 查询当前用户最近对话列表（按conversationId去重，每个会话取最新一条）
	 * @param params 查询参数（包含userId、limit）
	 * @return 最近对话列表
	 */
	List<RecentConversationVO> selectRecentConversations(@Param("params") Map<String, Object> params);

	List<AiChatDetailVO> selectChatDetailPage(Page<AiChatDetailVO> page, @Param("query") AiChatDetailQueryDTO query);

	List<AiChatDetailVO> selectChatDetailList(@Param("query") AiChatDetailQueryDTO query);

	long countChatDetails(@Param("query") AiChatDetailQueryDTO query);

}
