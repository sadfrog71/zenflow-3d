package com.deepdimen.matrix.discover.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.IService;
import com.deepdimen.matrix.discover.dto.AiAgentDTO;
import com.deepdimen.matrix.discover.dto.AiAgentImportDTO;
import com.deepdimen.matrix.discover.entity.AiAgentEntity;
import com.deepdimen.matrix.discover.vo.AiAgentEnterpriseRankVO;
import com.deepdimen.matrix.discover.vo.AiAgentMarketVO;
import com.deepdimen.matrix.discover.vo.AiAgentRankVO;
import com.deepdimen.matrix.discover.vo.AiAgentStatisticsVO;
import com.deepdimen.matrix.discover.vo.AiAgentVO;
import com.deepdimen.matrix.discover.vo.AiUserTopAgentVO;
import com.deepdimen.matrix.discover.vo.MarketOverviewVO;

import java.util.List;
import java.util.Map;
import com.deepdimen.matrix.admin.api.vo.AgentShareValidationVO;

/**
 * 智能体服务接口
 *
 * @author matrix
 * @date 2025-03-11
 */
public interface AiAgentService extends IService<AiAgentEntity> {

	/** 全局默认助手固定ID，跨租户共享 */
	Long DEFAULT_AGENT_ID = 2026L;

	/**
	 * 分页查询智能体列表
	 * @param page 分页对象
	 * @param agentDTO 查询条件
	 * @return 分页结果（VO，不含config/marketConfig大字段）
	 */
	Page<AiAgentVO> pageAgent(Page<AiAgentEntity> page, AiAgentDTO agentDTO);

	/**
	 * 查询当前租户智能体标签建议
	 * @param marketOnly 是否仅汇总已发布智能体的标签
	 * @return 去重后的标签列表
	 */
	List<String> listTagSuggestions(boolean marketOnly);

	/**
	 * 查询智能体详情
	 * @param id 智能体ID
	 * @return 智能体VO（含config字段），不存在时抛出异常
	 */
	AiAgentVO getAgentDetail(Long id);

	/**
	 * 创建智能体
	 * @param agentDTO 创建参数
	 * @return 新建的智能体VO
	 */
	AiAgentVO createAgent(AiAgentDTO agentDTO);

	/**
	 * 更新智能体
	 * @param agentDTO 更新参数
	 */
	void updateAgent(AiAgentDTO agentDTO);

	/**
	 * 发布智能体（草稿->已发布）
	 * @param id 智能体ID
	 */
	void publishAgent(Long id);

	/**
	 * 禁用智能体
	 * @param id 智能体ID
	 */
	void disableAgent(Long id);

	/**
	 * 启用智能体
	 * @param id 智能体ID
	 */
	void enableAgent(Long id);

	/**
	 * 市场智能体列表（本地+第三方，内存聚合分页）
	 * @param keyword 关键词
	 * @param category 分类
	 * @param source 来源过滤: local/external
	 * @param tag 单标签精确过滤
	 * @param accessible 权限过滤
	 * @param sortBy 排序: default/hot/rating/latest
	 * @param pageNum 页码
	 * @param pageSize 每页大小
	 * @return 市场列表
	 */
	Page<AiAgentMarketVO> marketList(String keyword, String category, String source, String tag, String excludeTag, Boolean accessible,
			String sortBy, int pageNum, int pageSize, boolean favoriteOnly);

	/**
	 * 市场统计概览
	 * @return 概览数据
	 */
	MarketOverviewVO marketOverview(String excludeTag);

	/**
	 * 智能体概况统计
	 * @return 统计数据
	 */
	AiAgentStatisticsVO statistics();

	/**
	 * 企业排行榜（仅平台管理员可调用）
	 * @param sortBy 排序: satisfaction/accuracy/favorites
	 * @return 企业排行列表
	 */
	List<AiAgentEnterpriseRankVO> enterpriseRank(String sortBy);

	/**
	 * 智能体排行Top N
	 * @param sortBy 排序: calls/rating/success
	 * @param limit 返回条数，默认10
	 * @return 排行列表
	 */
	List<AiAgentRankVO> rankList(String sortBy, int limit);

	/**
	 * 当前用户常用智能体(按会话维度去重统计使用频率Top N)
	 * @param limit 返回条数，默认3
	 * @return 常用智能体列表
	 */
	List<AiUserTopAgentVO> userTopAgents(int limit);

	/**
	 * 导入 Dify DSL 创建智能体
	 * @param importDTO 导入参数（name/description/category/dslContent/difyAppId），dslContent 与
	 * difyAppId 二选一
	 * @return 新建的智能体VO
	 */
	AiAgentVO importDifyAgent(AiAgentImportDTO importDTO);

	/**
	 * 查询当前租户可关联的 Dify 应用列表（tag 为空 或 包含当前租户标签），支持名称关键词过滤
	 * @param keyword 名称关键词，可为空
	 * @return 应用列表，每项包含 id/name/mode
	 */
	List<Map<String, Object>> listAvailableDifyApps(String keyword);

	/**
	 * 外部智能体连接测试（仅 source=external 生效）
	 * @param id 智能体ID
	 * @return Map: success(boolean), message(String), responseTime(long ms)
	 */
	Map<String, Object> testExternalConnection(Long id);

	/**
	 * 获取外部智能体 iframe URL（token 非空时后端按固定 token 参数拼接，含 URL 编码，前端不持有 token）
	 * @param id 智能体ID
	 * @return Map: url(String), name(String)
	 */
	Map<String, String> getExternalIframeUrl(Long id);

	/**
	 * 按智能体ID查询其所属租户ID（忽略租户上下文，供 share 免密登录定位租户使用）
	 * @param agentId 智能体ID
	 * @return 租户ID（数字字符串，对应 sys_user/ai_agent 的 bigint tenant_id，调用方可安全
	 * Long.parseLong），智能体不存在时返回 null
	 */
	String getTenantIdByAgentId(Long agentId);

	/**
	 * 校验智能体是否可用于分享登录
	 * @param agentId 智能体ID
	 * @return 分享校验结果
	 */
	AgentShareValidationVO validateShareAgent(Long agentId);

}
