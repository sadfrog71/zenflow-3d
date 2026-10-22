package com.deepdimen.matrix.discover.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;
import java.util.Set;

/**
 * 收藏查询接口（仅查询，写操作由 matrix-app-server 负责）
 *
 * @author matrix
 * @date 2025-03-11
 */
@Mapper
public interface AiCollectReadMapper {

	/**
	 * 批量查询用户对指定目标的收藏ID集合，targetIds 为空时直接返回空集合
	 * @param userId 用户ID
	 * @param collectType 收藏类型
	 * @param targetIds 目标ID列表
	 * @return 已收藏的目标ID集合
	 */
	@Select("<script>SELECT target_id FROM ai_collect WHERE user_id=#{userId} AND collect_type=#{collectType}"
			+ "<if test='targetIds != null and targetIds.size() > 0'>"
			+ " AND target_id IN <foreach collection='targetIds' item='id' open='(' separator=',' close=')'>#{id}</foreach>"
			+ "</if>" + "<if test='targetIds == null or targetIds.size() == 0'> AND 1=0</if>" + "</script>")
	Set<Long> findCollectedTargetIds(@Param("userId") Long userId, @Param("collectType") String collectType,
			@Param("targetIds") List<Long> targetIds);

	/**
	 * 查询指定目标的收藏数量
	 * @param targetId 目标ID
	 * @param collectType 收藏类型
	 * @return 收藏数量
	 */
	@Select("SELECT COUNT(*) FROM ai_collect WHERE target_id=#{targetId} AND collect_type=#{collectType}")
	Long countByTarget(@Param("targetId") Long targetId, @Param("collectType") String collectType);

	/**
	 * 查询用户指定类型的收藏总数（关联验证目标未删除）
	 * @param userId 用户ID
	 * @param collectType 收藏类型
	 * @return 收藏数量
	 */
	@Select("SELECT COUNT(*) FROM ai_collect c INNER JOIN ai_agent a ON a.id = c.target_id AND a.del_flag = '0'"
			+ " WHERE c.user_id=#{userId} AND c.collect_type=#{collectType}")
	Long countByUserAndType(@Param("userId") Long userId, @Param("collectType") String collectType);

}
