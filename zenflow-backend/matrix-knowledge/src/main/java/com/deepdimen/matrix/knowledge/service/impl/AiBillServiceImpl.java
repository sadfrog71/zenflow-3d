package com.deepdimen.matrix.knowledge.service.impl;

import cn.hutool.core.map.MapUtil;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.deepdimen.matrix.common.core.constant.enums.YesNoEnum;
import com.deepdimen.matrix.knowledge.config.properties.AiKnowledgeProperties;
import com.deepdimen.matrix.knowledge.dto.ChatMessageDTO;
import com.deepdimen.matrix.knowledge.entity.AiBillEntity;
import com.deepdimen.matrix.knowledge.entity.AiChatRecordEntity;
import com.deepdimen.matrix.knowledge.mapper.AiBillMapper;
import com.deepdimen.matrix.knowledge.service.AiBillService;
import dev.langchain4j.model.output.TokenUsage;
import lombok.RequiredArgsConstructor;
import org.springframework.ai.tokenizer.TokenCountEstimator;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * AI账单服务实现类
 * <p>
 * 记录和统计AI服务的Token使用情况 支持按用户、模型、时间等维度进行账单管理 用于成本核算和使用量监控
 *
 * @author matrix
 * @date 2024-03-26 11:26:59
 */
@Service
@RequiredArgsConstructor
public class AiBillServiceImpl extends ServiceImpl<AiBillMapper, AiBillEntity> implements AiBillService {

	/**
	 * AI知识库配置属性
	 */
	private final AiKnowledgeProperties aiKnowledgeProperties;

	/**
	 * Token计数估算器 用于估算文本的Token数量
	 */
	private final TokenCountEstimator tokenCountEstimator;

	/**
	 * 保存账单记录
	 * <p>
	 * 记录每次AI调用的Token使用情况 包括输入、输出和总Token数量
	 * @param map 包含用户信息、IP地址等上下文参数
	 * @param tokenUsage Token使用统计信息
	 */
	@Override
	public void saveBill(Map<Object, Object> map, TokenUsage tokenUsage) {
		AiBillEntity aiBillEntity = new AiBillEntity();
		// 设置Token类型（0：常规）
		aiBillEntity.setTokenType(YesNoEnum.NO.getCode());
		// 记录Token使用量
		aiBillEntity.setPromptTokens(tokenUsage.inputTokenCount().longValue());
		aiBillEntity.setCompletionTokens(tokenUsage.outputTokenCount().longValue());
		aiBillEntity.setTokens(tokenUsage.totalTokenCount().longValue());

		// 提取用户信息
		String ip = MapUtil.getStr(map, AiChatRecordEntity.Fields.ip);
		String username = MapUtil.getStr(map, AiChatRecordEntity.Fields.username);

		aiBillEntity.setIp(ip);
		aiBillEntity.setUsername(username);
		aiBillEntity.setCreateBy(username);

		// 记录模型响应时间
		Long useTime = MapUtil.getLong(map, AiBillEntity.Fields.useTime);
		aiBillEntity.setUseTime(useTime);

		// 关联聊天记录ID
		Long messageKey = MapUtil.getLong(map, AiChatRecordEntity.Fields.recordId);
		aiBillEntity.setMessageKey(messageKey);
		// 记录使用的模型
		String model = MapUtil.getStr(map, AiBillEntity.Fields.model);
		aiBillEntity.setModel(model);
		baseMapper.insert(aiBillEntity);
	}

	/**
	 * 获取账单统计信息
	 * <p>
	 * 统计最近30天的Token使用情况 按日期分组汇总，用于图表展示
	 * @return 包含日期和Token总数的统计列表
	 */
	@Override
	public List<Map<String, Object>> getBillSum() {
		// 获取30天前的日期作为起始时间
		LocalDateTime startDate = LocalDate.now().minusDays(30).atStartOfDay();

		// 查询指定时间范围内的账单数据
		List<AiBillEntity> bills = baseMapper.selectBillSum(startDate);

		// 按日期分组统计Token使用量
		Map<LocalDate, Long> groupedData = bills.stream()
			.collect(Collectors.groupingBy(bill -> bill.getCreateTime().toLocalDate(), LinkedHashMap::new,
					Collectors.summingLong(AiBillEntity::getTokens)));

		// 转换为前端需要的格式
		return groupedData.entrySet().stream().map(entry -> {
			Map<String, Object> map = new HashMap<>();
			map.put("date", entry.getKey());
			map.put("total_tokens", entry.getValue());
			return map;
		}).toList();
	}

	/**
	 * 估算输入文本的Token数量
	 * <p>
	 * 用于预估算成本
	 * @param chatMessageDTO 聊天消息DTO
	 * @return 估算的Token数量
	 */
	private Long getInputTokens(ChatMessageDTO chatMessageDTO) {
		return this.getTokens(chatMessageDTO.getContent());
	}

	/**
	 * 估算文本的Token数量
	 * <p>
	 * 使用tokenizer进行准确估算
	 * @param text 待估算的文本
	 * @return Token数量
	 */
	private Long getTokens(String text) {
		return (long) tokenCountEstimator.estimate(text);
	}

}
