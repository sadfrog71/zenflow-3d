package com.deepdimen.matrix.app.service.impl;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.deepdimen.matrix.app.api.entity.AiCollectEntity;
import com.deepdimen.matrix.app.mapper.AiCollectMapper;
import com.deepdimen.matrix.app.service.AiCollectService;
import com.deepdimen.matrix.common.security.util.SecurityUtils;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * 通用收藏服务实现
 *
 * @author matrix
 * @date 2025-03-11
 */
@Service
public class AiCollectServiceImpl extends ServiceImpl<AiCollectMapper, AiCollectEntity>
		implements AiCollectService {

	@Override
	public void collect(String collectType, Long targetId) {
		Long userId = SecurityUtils.getUser().getId();
		AiCollectEntity entity = new AiCollectEntity();
		entity.setUserId(userId);
		entity.setCollectType(collectType);
		entity.setTargetId(targetId);
		try {
			this.save(entity);
		}
		catch (DuplicateKeyException e) {
			// 重复收藏，幂等处理，直接返回成功
		}
	}

	@Override
	public void cancelCollect(String collectType, Long targetId) {
		Long userId = SecurityUtils.getUser().getId();
		this.remove(Wrappers.<AiCollectEntity>lambdaQuery()
				.eq(AiCollectEntity::getUserId, userId)
				.eq(AiCollectEntity::getCollectType, collectType)
				.eq(AiCollectEntity::getTargetId, targetId));
	}

	@Override
	public List<AiCollectEntity> listCollect(String collectType) {
		Long userId = SecurityUtils.getUser().getId();
		return this.list(Wrappers.<AiCollectEntity>lambdaQuery()
				.eq(AiCollectEntity::getUserId, userId)
				.eq(AiCollectEntity::getCollectType, collectType)
				.last("LIMIT 500"));
	}

	@Override
	public Long countCollect(String collectType) {
		Long userId = SecurityUtils.getUser().getId();
		return this.count(Wrappers.<AiCollectEntity>lambdaQuery()
				.eq(AiCollectEntity::getUserId, userId)
				.eq(AiCollectEntity::getCollectType, collectType));
	}

}
