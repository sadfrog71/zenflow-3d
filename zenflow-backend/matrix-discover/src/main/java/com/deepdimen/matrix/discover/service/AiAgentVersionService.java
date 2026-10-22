package com.deepdimen.matrix.discover.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.deepdimen.matrix.discover.dto.AiAgentVersionGrayDTO;
import com.deepdimen.matrix.discover.entity.AiAgentVersionEntity;
import com.deepdimen.matrix.discover.vo.AiAgentVersionVO;

import java.util.List;
import java.util.Map;

/**
 * 智能体版本服务接口
 *
 * @author matrix
 * @date 2025-03-11
 */
public interface AiAgentVersionService extends IService<AiAgentVersionEntity> {

	/**
	 * 查询版本列表
	 * @param agentId 智能体ID
	 * @return 版本列表
	 */
	List<AiAgentVersionVO> listVersions(Long agentId);

	/**
	 * 版本对比
	 * @param agentId 智能体ID
	 * @param v1 版本号1
	 * @param v2 版本号2
	 * @return 两个版本的config快照，key为版本号
	 */
	Map<String, String> compareVersions(Long agentId, String v1, String v2);

	/**
	 * 版本回滚（将指定版本config写回ai_agent表，并设为active）
	 * @param agentId 智能体ID
	 * @param versionId 版本ID
	 */
	void rollback(Long agentId, Long versionId);

	/**
	 * 配置灰度规则
	 * @param agentId 智能体ID
	 * @param versionId 版本ID
	 * @param dto 灰度配置
	 */
	void setGrayConfig(Long agentId, Long versionId, AiAgentVersionGrayDTO dto);

	/**
	 * 创建版本快照（发布时调用），版本号自增
	 * @param agentId 智能体ID
	 * @param config 当前配置快照
	 * @param remark 版本说明
	 */
	void createSnapshot(Long agentId, String config, String remark);

	/**
	 * 获取灰度命中版本的config（无灰度命中则返回null）
	 * @param agentId 智能体ID
	 * @param userId 当前用户ID
	 * @return 灰度版本config，未命中返回null
	 */
	String resolveGrayConfig(Long agentId, Long userId);

}
