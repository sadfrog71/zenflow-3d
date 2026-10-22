package com.deepdimen.matrix.knowledge.support.flow.model.processor;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.Map;

/**
 * AI 节点处理器工厂 负责管理和提供各种类型的节点处理器实例 使用Spring的依赖注入机制自动收集所有实现了AiNodeProcessor接口的Bean
 *
 * @author caoyuan
 * @date 2025/03/03
 */
@Service
@RequiredArgsConstructor
public class AiNodeProcessorFactory {

	/**
	 * 处理器映射表 key: 节点类型（对应@Component的名称） value: 对应的节点处理器实例
	 * Spring会自动注入所有实现了AiNodeProcessor接口的Bean
	 */
	private final Map<String, AiNodeProcessor> processorMap;

	/**
	 * 根据节点类型获取对应的处理器
	 * @param type 节点类型（如：START、END、LLM、HTTP等）
	 * @return 对应类型的节点处理器
	 * @throws RuntimeException 当找不到对应类型的处理器时抛出异常
	 */
	public AiNodeProcessor getProcessor(String type) {
		AiNodeProcessor processor = processorMap.get(type);
		if (processor == null) {
			throw new RuntimeException("未知的节点类型: " + type);
		}
		return processor;
	}

}
