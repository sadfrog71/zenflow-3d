package com.deepdimen.matrix.discover.mapper;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.deepdimen.matrix.common.data.datascope.MatrixBaseMapper;
import com.deepdimen.matrix.discover.entity.AiAgentEntity;
import com.deepdimen.matrix.discover.vo.AiAgentEnterpriseRankVO;
import com.deepdimen.matrix.discover.vo.AiAgentMarketVO;
import com.deepdimen.matrix.discover.vo.AiAgentRankVO;
import com.deepdimen.matrix.discover.vo.AiAgentStatisticsVO;
import com.deepdimen.matrix.discover.vo.AiUserTopAgentVO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

/**
 * 智能体数据访问接口
 *
 * @author matrix
 * @date 2025-03-11
 */
@Mapper
public interface AiAgentMapper extends MatrixBaseMapper<AiAgentEntity> {

	/**
	 * 查询智能体概况统计(totalCount只含已发布+已停用)
	 */
	AiAgentStatisticsVO selectStatistics();

	/**
	 * 查询全局满意度(like数/总评价数)
	 */
	BigDecimal selectGlobalSatisfaction();

	/**
	 * 查询智能体总收藏数
	 */
	Long selectTotalFavorites();

	/**
	 * 查询智能体排行
	 * @param sortBy 排序字段: calls/rating/success
	 * @param limit 返回条数
	 */
	List<AiAgentRankVO> selectRankList(@Param("sortBy") String sortBy, @Param("limit") int limit);

	/**
	 * 查询企业排行榜（跨租户，需配合 TenantBroker.noneAs() 使用）
	 * @param sortBy 排序字段: satisfaction/accuracy/favorites
	 */
	List<AiAgentEnterpriseRankVO> selectEnterpriseRankList(@Param("sortBy") String sortBy);

	/**
	 * 查询当前用户可用的已发布智能体数量（联查权限表）
	 * @param userId 用户ID
	 * @param roleIds 用户角色ID列表
	 * @param deptIds 用户部门ID列表
	 */
	Long selectAvailableCount(@Param("userId") Long userId, @Param("roleIds") List<Long> roleIds,
			@Param("deptIds") List<Long> deptIds, @Param("excludeTag") String excludeTag);

	/**
	 * 查询指定来源的已发布智能体数量
	 * @param source 智能体来源
	 * @param excludeTag 需要排除的标签
	 */
	Long selectPublishedCount(@Param("source") String source, @Param("excludeTag") String excludeTag);

	/**
	 * 查询指定日期范围内的智能体相关对话数
	 * @param startTime 开始时间（含）
	 * @param endTime 结束时间（不含）
	 */
	Long selectCallCount(@Param("startTime") String startTime, @Param("endTime") String endTime);

	/**
	 * 查询当前用户常用智能体(按会话维度去重统计使用次数)
	 * @param username 用户名
	 * @param limit 返回条数
	 */
	List<AiUserTopAgentVO> selectUserTopAgents(@Param("username") String username, @Param("limit") int limit);

	/**
	 * 市场智能体SQL分页查询（含实时满意度和收藏状态）
	 */
	Page<AiAgentMarketVO> selectMarketPage(Page<AiAgentMarketVO> page, @Param("keyword") String keyword,
			@Param("category") String category, @Param("source") String source, @Param("tag") String tag,
			@Param("excludeTag") String excludeTag,
			@Param("sortBy") String sortBy, @Param("userId") Long userId, @Param("roleIds") List<Long> roleIds,
			@Param("deptIds") List<Long> deptIds, @Param("accessible") Boolean accessible,
			@Param("favoriteOnly") boolean favoriteOnly);

	/**
	 * 批量查询智能体实时满意度(like数/总评价数)
	 * @param agentIds 智能体ID列表
	 */
	List<Map<String, Object>> selectAgentSatisfaction(@Param("agentIds") List<Long> agentIds);

	/**
	 * 批量查询知识库真实名称，用于同步 Dify 外部知识库展示名称
	 * @param datasetIds 知识库ID列表
	 */
	List<Map<String, Object>> selectDatasetNames(@Param("datasetIds") List<Long> datasetIds);

}
