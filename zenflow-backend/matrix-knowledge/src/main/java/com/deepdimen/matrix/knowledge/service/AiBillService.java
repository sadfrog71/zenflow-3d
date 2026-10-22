package com.deepdimen.matrix.knowledge.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.deepdimen.matrix.knowledge.entity.AiBillEntity;
import dev.langchain4j.model.output.TokenUsage;

import java.util.List;
import java.util.Map;

/**
 * AI账单服务接口 负责管理和统计AI服务使用的Token消耗和费用
 */
public interface AiBillService extends IService<AiBillEntity> {

	/**
	 * 保存AI使用账单记录 记录每次AI调用的Token消耗情况，用于费用统计和成本控制
	 * @param messageKey 消息键值对，包含会话ID、用户ID等关键信息
	 * @param tokenUsage Token使用情况，包含输入Token数、输出Token数和总Token数
	 */
	void saveBill(Map<Object, Object> messageKey, TokenUsage tokenUsage);

	/**
	 * 获取账单汇总统计 按照不同维度（如用户、时间段、模型等）统计Token使用和费用
	 * @return 账单汇总数据列表，每个Map包含统计维度和对应的汇总值
	 */
	List<Map<String, Object>> getBillSum();

}
