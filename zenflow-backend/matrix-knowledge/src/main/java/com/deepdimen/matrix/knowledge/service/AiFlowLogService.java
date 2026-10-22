package com.deepdimen.matrix.knowledge.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.deepdimen.matrix.knowledge.entity.AiFlowLogEntity;

/**
 * AI流程日志服务接口
 * <p>
 * 记录AI流程的执行日志，包括流程执行轨迹、节点执行结果、错误信息等， 用于流程调试、监控和审计
 * </p>
 *
 * @author matrix
 */
public interface AiFlowLogService extends IService<AiFlowLogEntity> {

}