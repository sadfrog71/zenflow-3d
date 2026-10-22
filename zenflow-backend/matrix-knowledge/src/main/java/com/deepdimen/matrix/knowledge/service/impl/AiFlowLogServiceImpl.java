package com.deepdimen.matrix.knowledge.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.deepdimen.matrix.knowledge.entity.AiFlowLogEntity;
import com.deepdimen.matrix.knowledge.mapper.AiFlowLogMapper;
import com.deepdimen.matrix.knowledge.service.AiFlowLogService;
import org.springframework.stereotype.Service;

/**
 * AI流程日志服务实现类
 * <p>
 * 记录和管理AI流程执行日志 用于追踪流程执行状态和调试 支持流程执行历史查询和分析
 *
 * @author matrix
 * @date 2025-03-03 10:21:15
 */
@Service
public class AiFlowLogServiceImpl extends ServiceImpl<AiFlowLogMapper, AiFlowLogEntity> implements AiFlowLogService {

}
