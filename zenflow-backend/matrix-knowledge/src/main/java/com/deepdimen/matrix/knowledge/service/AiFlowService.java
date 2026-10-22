package com.deepdimen.matrix.knowledge.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.deepdimen.matrix.common.core.util.R;
import com.deepdimen.matrix.knowledge.dto.AiFlowExecuteDTO;
import com.deepdimen.matrix.knowledge.entity.AiFlowEntity;
import jakarta.servlet.http.HttpServletResponse;

/**
 * AI流程服务接口
 * <p>
 * 提供AI流程的编排、执行、导出等功能，基于LiteFlow实现流程编排， 支持多种AI组件的组合使用（如LLM、知识库检索、函数调用等）
 * </p>
 *
 * @author matrix
 */
public interface AiFlowService extends IService<AiFlowEntity> {

	/**
	 * 执行AI流程
	 * <p>
	 * 根据流程定义执行AI工作流，支持同步和异步执行模式， 流程中可包含LLM调用、知识库检索、条件判断等多种节点
	 * </p>
	 * @param executeDTO 流程执行参数（包含输入数据、执行模式等）
	 * @return 流程执行结果
	 */
	Object executeFlow(AiFlowExecuteDTO executeDTO);

	/**
	 * 复制流程
	 * <p>
	 * 创建现有流程的副本，便于基于已有流程创建新的流程
	 * </p>
	 * @param id 原流程ID
	 * @return 复制结果，包含新流程信息
	 */
	R copyFlow(Long id);

	/**
	 * 导出流程
	 * <p>
	 * 将流程定义导出为文件，支持流程的备份和迁移
	 * </p>
	 * @param id 流程ID
	 * @param response HTTP响应对象，用于文件下载
	 */
	void exportFlow(Long id, HttpServletResponse response);

}
