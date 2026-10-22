package com.deepdimen.matrix.discover.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.deepdimen.matrix.discover.entity.DifyChatRecordEntity;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface DifyChatRecordMapper extends BaseMapper<DifyChatRecordEntity> {

	void incrementAgentUseCount(@Param("agentId") Long agentId);

	void updateEvaluateByMessageId(@Param("tenantId") Long tenantId, @Param("messageId") String messageId,
			@Param("evaluate") String evaluate);

}
