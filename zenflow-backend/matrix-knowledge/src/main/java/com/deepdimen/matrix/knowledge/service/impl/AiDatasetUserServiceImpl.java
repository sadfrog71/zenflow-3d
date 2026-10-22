package com.deepdimen.matrix.knowledge.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.deepdimen.matrix.knowledge.entity.AiDatasetUserEntity;
import com.deepdimen.matrix.knowledge.mapper.AiDatasetUserMapper;
import com.deepdimen.matrix.knowledge.service.AiDatasetUserService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/**
 * 知识库用户权限服务实现类
 * <p>
 * 管理知识库与用户的关联关系 控制用户对私有知识库的访问权限 支持批量授权和权限查询
 *
 * @author matrix
 * @date 2025-06-06 10:20:20
 */
@Service
@RequiredArgsConstructor
public class AiDatasetUserServiceImpl extends ServiceImpl<AiDatasetUserMapper, AiDatasetUserEntity>
		implements AiDatasetUserService {

}
