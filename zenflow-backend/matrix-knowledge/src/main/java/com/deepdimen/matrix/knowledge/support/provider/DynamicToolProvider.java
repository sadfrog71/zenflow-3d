package com.deepdimen.matrix.knowledge.support.provider;

import cn.hutool.core.util.StrUtil;
import com.deepdimen.matrix.knowledge.dto.ChatMessageDTO;
import com.deepdimen.matrix.knowledge.support.function.FunctionCalling;
import com.deepdimen.matrix.knowledge.support.util.ChatMessageContextHolder;
import dev.langchain4j.agent.tool.Tool;
import dev.langchain4j.service.tool.ToolProvider;
import dev.langchain4j.service.tool.ToolProviderRequest;
import dev.langchain4j.service.tool.ToolProviderResult;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;

/**
 * 动态工具提供者
 * <p>
 * 实现ToolProvider接口，根据聊天上下文动态提供函数调用工具 支持根据用户指定的函数名称，动态加载对应的函数工具 用于实现灵活的函数调用（Function
 * Calling）功能
 *
 * @author caoyuan
 * @date 2024/9/27
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DynamicToolProvider implements ToolProvider {

	/**
	 * 所有可用的函数调用实现列表
	 */
	private final List<FunctionCalling> functionCallingList;

	/**
	 * 根据请求动态提供工具 从聊天上下文中获取用户指定的函数名称 匹配并返回对应的函数工具实现
	 * @param request 工具提供请求
	 * @return 工具提供结果，包含匹配的函数工具
	 */
	@Override
	@Tool
	public ToolProviderResult provideTools(ToolProviderRequest request) {
		ChatMessageDTO chatMessageDTO = ChatMessageContextHolder.get();
		// 根据函数名称查找对应的函数实现
		Optional<FunctionCalling> optionalFunctionCalling = functionCallingList.stream()
			.filter(functionCalling -> StrUtil.equals(chatMessageDTO.getExtDetails().getFuncName(),
					functionCalling.functionName()))
			.findFirst();

		FunctionCalling functionCalling = optionalFunctionCalling.get();
		return ToolProviderResult.builder().add(functionCalling.buildToolSpecification(), functionCalling).build();

	}

}
