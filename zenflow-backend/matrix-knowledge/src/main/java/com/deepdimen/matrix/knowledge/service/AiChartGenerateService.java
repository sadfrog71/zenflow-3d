package com.deepdimen.matrix.knowledge.service;

import com.deepdimen.matrix.knowledge.dto.ChatMessageDTO;

/**
 * AI图表生成服务接口 提供基于数据和AI的智能图表生成功能
 *
 * @author caoyuan
 * @date 2025/3/23
 */
public interface AiChartGenerateService {

	/**
	 * 生成智能图表 根据用户提示和数据，通过AI分析生成合适的可视化图表
	 * @param chatMessageDTO 聊天消息对象，包含用户提示、图表类型等信息
	 * @param data 需要可视化的原始数据
	 */
	void generateChart(ChatMessageDTO chatMessageDTO, String data);

	/**
	 * 获取已生成的图表 根据图表ID获取之前生成并存储的图表数据或配置
	 * @param chartId 图表唯一标识ID
	 * @return 图表的JSON配置或渲染数据
	 */
	String getChart(String chartId);

}
