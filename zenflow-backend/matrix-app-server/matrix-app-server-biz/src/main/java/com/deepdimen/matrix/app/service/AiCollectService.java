package com.deepdimen.matrix.app.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.deepdimen.matrix.app.api.entity.AiCollectEntity;

import java.util.List;

/**
 * 通用收藏服务接口
 *
 * @author matrix
 * @date 2025-03-11
 */
public interface AiCollectService extends IService<AiCollectEntity> {

	/**
	 * 收藏（幂等，重复收藏返回成功）
	 * @param collectType 收藏类型
	 * @param targetId 目标ID
	 */
	void collect(String collectType, Long targetId);

	/**
	 * 取消收藏
	 * @param collectType 收藏类型
	 * @param targetId 目标ID
	 */
	void cancelCollect(String collectType, Long targetId);

	/**
	 * 查询收藏列表（不分页，最多500条）
	 * @param collectType 收藏类型
	 * @return 收藏列表
	 */
	List<AiCollectEntity> listCollect(String collectType);

	/**
	 * 查询收藏数量
	 * @param collectType 收藏类型
	 * @return 数量
	 */
	Long countCollect(String collectType);

}
