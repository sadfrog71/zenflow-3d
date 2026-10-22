package com.deepdimen.matrix.knowledge.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.IService;
import com.deepdimen.matrix.knowledge.entity.AiMaterialLogEntity;

/**
 * AI素材日志服务接口
 * <p>
 * 管理AI处理过程中产生的素材日志，包括文档解析、向量化、 知识提取等操作的记录，支持日志查询和审计功能。
 *
 * @author caoyuan
 * @date 2025/05/08
 */
public interface AiMaterialLogService extends IService<AiMaterialLogEntity> {

	/**
	 * 根据数据权限范围分页查询素材日志
	 * <p>
	 * 结合用户的数据权限范围，查询其可见的素材处理日志。 支持按时间、类型、状态等条件过滤，便于追踪和审计。
	 * @param page 分页参数对象，包含当前页码、每页记录数等信息
	 * @param wrapper 查询条件构造器，支持动态组合查询条件和排序规则
	 * @return 符合条件的素材日志分页结果，包含总记录数和当前页数据
	 */
	Page getPageByScope(Page page, LambdaQueryWrapper<AiMaterialLogEntity> wrapper);

}