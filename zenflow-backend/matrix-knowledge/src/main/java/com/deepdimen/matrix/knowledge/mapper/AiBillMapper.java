package com.deepdimen.matrix.knowledge.mapper;

import com.deepdimen.matrix.common.data.datascope.MatrixBaseMapper;
import com.deepdimen.matrix.knowledge.entity.AiBillEntity;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDateTime;
import java.util.List;

/**
 * AI账单数据访问接口
 * <p>
 * 提供AI使用账单的数据库访问操作 记录用户使用AI服务的token消耗和费用明细
 *
 * @author matrix
 * @date 2024-03-26 11:26:59
 */
@Mapper
public interface AiBillMapper extends MatrixBaseMapper<AiBillEntity> {

	/**
	 * 查询账单汇总信息
	 * @param startDate 查询开始日期
	 * @return 账单汇总信息列表
	 */
	List<AiBillEntity> selectBillSum(@Param("startDate") LocalDateTime startDate);

}
