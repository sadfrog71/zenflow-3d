package com.deepdimen.matrix.capsule.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.deepdimen.matrix.capsule.api.vo.CapsuleVersionVO;
import com.deepdimen.matrix.capsule.entity.CapsuleRevisionEntity;
import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface CapsuleRevisionMapper extends BaseMapper<CapsuleRevisionEntity> {
    List<CapsuleVersionVO> selectOwnedVersionsPage(@Param("tenantId") Long tenantId,@Param("ownerUserId") Long ownerUserId,
        @Param("subjectType") String subjectType,@Param("capsuleId") Long capsuleId,@Param("beforeRevision") Integer beforeRevision,@Param("limit") int limit);

    default List<CapsuleVersionVO> selectOwnedVersions(Long tenantId,
            Long ownerUserId,
            Long capsuleId) {
        return selectOwnedVersions(tenantId, ownerUserId, "ADMIN_USER", capsuleId);
    }

    List<CapsuleVersionVO> selectOwnedVersions(@Param("tenantId") Long tenantId,
            @Param("ownerUserId") Long ownerUserId,
            @Param("subjectType") String subjectType,
            @Param("capsuleId") Long capsuleId);
}
