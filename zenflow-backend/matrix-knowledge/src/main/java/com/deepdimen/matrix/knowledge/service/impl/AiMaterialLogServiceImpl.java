package com.deepdimen.matrix.knowledge.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.deepdimen.matrix.common.data.datascope.DataScope;
import com.deepdimen.matrix.common.security.util.SecurityUtils;
import com.deepdimen.matrix.knowledge.entity.AiMaterialLogEntity;
import com.deepdimen.matrix.knowledge.mapper.AiMaterialLogMapper;
import com.deepdimen.matrix.knowledge.service.AiMaterialLogService;
import org.springframework.stereotype.Service;

/**
 * AI资源日志服务实现类
 * <p>
 * 记录和管理AI生成的资源使用情况 支持基于用户权限的数据范围查询 仅允许用户查看自己创建的资源记录
 *
 * @author matrix ai
 * @date 2025-05-08 11:06:03
 */
@Service
public class AiMaterialLogServiceImpl extends ServiceImpl<AiMaterialLogMapper, AiMaterialLogEntity>
		implements AiMaterialLogService {

	/**
	 * 按数据权限范围分页查询
	 * <p>
	 * 应用数据权限过滤条件 仅返回当前用户创建的资源记录
	 * @param page 分页对象
	 * @param wrapper 查询条件包装器
	 * @return 符合权限的分页结果
	 */
	@Override
	public Page getPageByScope(Page page, LambdaQueryWrapper<AiMaterialLogEntity> wrapper) {
		// 只查询自己创建的资源
		DataScope dataScope = new DataScope();
		dataScope.setUsername(SecurityUtils.getUser().getUsername());
		return baseMapper.selectPageByScope(page, wrapper, dataScope);
	}

}