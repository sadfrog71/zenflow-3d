package com.deepdimen.matrix.knowledge.service;

import com.deepdimen.matrix.common.core.util.R;
import com.deepdimen.matrix.knowledge.dto.AiConfirmFormDTO;

/**
 * AI功能服务接口
 * <p>
 * 提供AI功能调用相关服务，包括函数工具的注册、查询和调用， 支持LLM的Function Calling功能，实现AI与外部系统的集成
 * </p>
 *
 * @author caoyuan
 * @date 2025/06/03
 */
public interface AiFuncService {

	/**
	 * 获取可用的函数调用列表
	 * <p>
	 * 查询当前系统中注册的所有可供AI调用的函数工具， 包括函数名称、描述、参数定义等信息
	 * </p>
	 * @return 函数调用列表信息
	 */
	R listFunctionCalling();

	/**
	 * 提交函数
	 * @param confirmFormDTO 确认表单DTO
	 * @return 提交结果
	 */
	R submitFunction(AiConfirmFormDTO confirmFormDTO);

}
