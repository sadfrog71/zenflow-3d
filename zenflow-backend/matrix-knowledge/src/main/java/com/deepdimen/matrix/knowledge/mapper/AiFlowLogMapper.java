package com.deepdimen.matrix.knowledge.mapper;

import com.deepdimen.matrix.common.data.datascope.MatrixBaseMapper;
import com.deepdimen.matrix.knowledge.entity.AiFlowLogEntity;
import org.apache.ibatis.annotations.Mapper;

/**
 * 大模型流程日志数据访问接口
 * <p>
 * 提供大模型流程日志表的数据库访问操作 用于记录AI流程的执行日志，包括输入输出参数、执行状态、错误信息等
 *
 * @author matrix
 */
@Mapper
public interface AiFlowLogMapper extends MatrixBaseMapper<AiFlowLogEntity> {

}