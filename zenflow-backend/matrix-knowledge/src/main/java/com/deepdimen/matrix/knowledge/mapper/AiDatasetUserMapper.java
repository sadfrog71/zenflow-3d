package com.deepdimen.matrix.knowledge.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.deepdimen.matrix.knowledge.entity.AiDatasetUserEntity;
import org.apache.ibatis.annotations.Mapper;

import java.util.List;

/**
 * 知识库用户权限数据访问接口
 * <p>
 * 提供知识库用户权限表的数据库访问操作 用于管理用户对知识库的访问权限控制
 *
 */
@Mapper
public interface AiDatasetUserMapper extends BaseMapper<AiDatasetUserEntity> {

	/**
	 * 查询指定用户去重后的数据集ID列表
	 * @param userId 用户ID
	 * @return 去重后的数据集ID列表
	 */
	List<Long> selectDistinctDatasetId(Long userId);

}
