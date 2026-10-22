package com.deepdimen.matrix.knowledge.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.deepdimen.matrix.knowledge.dto.AiPromptDTO;
import com.deepdimen.matrix.knowledge.entity.AiPromptEntity;

/**
 * AI提示词服务接口 提供提示词管理和优化功能，帮助用户构建更有效的AI交互提示
 *
 * @author matrix
 */
public interface AiPromptService extends IService<AiPromptEntity> {

	/**
	 * 优化提示词 使用AI技术对用户输入的提示词进行优化，提高提示词的准确性和有效性
	 * @param prompt 原始提示词对象，包含需要优化的提示内容
	 * @return 返回优化后的提示词文本
	 */
	String optimizePrompt(AiPromptDTO prompt);

}
