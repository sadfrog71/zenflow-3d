package com.deepdimen.matrix.knowledge.mapper;

import com.deepdimen.matrix.common.data.datascope.MatrixBaseMapper;
import com.deepdimen.matrix.knowledge.dto.AiSliceTextSearchDTO;
import com.deepdimen.matrix.knowledge.entity.AiSliceEntity;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * AI文档切片Mapper接口
 * <p>
 * 该接口提供了对AI文档切片数据表的数据访问操作。 继承自MatrixBaseMapper，自动拥有基本的CRUD操作功能。
 * </p>
 *
 * @author matrix code generator
 * @since 2023-01-01
 */
@Mapper
public interface AiSliceMapper extends MatrixBaseMapper<AiSliceEntity> {

	/**
	 * Search persisted slice text inside one already-authorized dataset. Tenant filters
	 * are explicit as defence in depth in addition to the MyBatis tenant interceptor.
	 */
	List<AiSliceTextSearchDTO> searchByText(@Param("tenantId") Long tenantId,
			@Param("datasetId") Long datasetId, @Param("terms") List<String> terms,
			@Param("limit") int limit);

}
