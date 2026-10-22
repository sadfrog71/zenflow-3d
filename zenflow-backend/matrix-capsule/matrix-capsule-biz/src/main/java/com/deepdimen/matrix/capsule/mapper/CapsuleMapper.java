package com.deepdimen.matrix.capsule.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.deepdimen.matrix.capsule.api.dto.CapsulePageQuery;
import com.deepdimen.matrix.capsule.api.enums.CapsuleRecordType;
import com.deepdimen.matrix.capsule.api.vo.CapsuleDetailVO;
import com.deepdimen.matrix.capsule.api.vo.CapsuleOverviewVO;
import com.deepdimen.matrix.capsule.api.vo.CapsuleSummaryVO;
import com.deepdimen.matrix.capsule.entity.CapsuleEntity;
import java.time.LocalDateTime;
import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface CapsuleMapper extends BaseMapper<CapsuleEntity> {

    default IPage<CapsuleSummaryVO> selectOwnedPage(Page<CapsuleSummaryVO> page,
            Long tenantId,
            Long ownerUserId,
            CapsulePageQuery query) {
        return selectOwnedPage(page, tenantId, ownerUserId, "ADMIN_USER", query);
    }

    IPage<CapsuleSummaryVO> selectOwnedPage(Page<CapsuleSummaryVO> page,
            @Param("tenantId") Long tenantId,
            @Param("ownerUserId") Long ownerUserId,
            @Param("subjectType") String subjectType,
            @Param("query") CapsulePageQuery query);

    default CapsuleDetailVO selectOwnedDetail(Long tenantId,
            Long ownerUserId,
            Long id) {
        return selectOwnedDetail(tenantId, ownerUserId, "ADMIN_USER", id);
    }

    CapsuleDetailVO selectOwnedDetail(@Param("tenantId") Long tenantId,
            @Param("ownerUserId") Long ownerUserId,
            @Param("subjectType") String subjectType,
            @Param("id") Long id);

    default List<CapsuleDetailVO> selectOwnedForContext(Long tenantId,
            Long ownerUserId,
            String queryText,
            LocalDateTime from,
            LocalDateTime to,
            CapsuleRecordType recordType,
            List<Long> explicitIds,
            int limit) {
        return selectOwnedForContext(tenantId, ownerUserId, "ADMIN_USER", queryText, from, to, recordType, explicitIds, limit);
    }

    List<CapsuleDetailVO> selectOwnedForContext(@Param("tenantId") Long tenantId,
            @Param("ownerUserId") Long ownerUserId,
            @Param("subjectType") String subjectType,
            @Param("queryText") String queryText,
            @Param("from") LocalDateTime from,
            @Param("to") LocalDateTime to,
            @Param("recordType") CapsuleRecordType recordType,
            @Param("explicitIds") List<Long> explicitIds,
            @Param("limit") int limit);

    default long countOwnedExplicitIds(Long tenantId,
            Long ownerUserId,
            List<Long> explicitIds) {
        return countOwnedExplicitIds(tenantId, ownerUserId, "ADMIN_USER", explicitIds);
    }

    long countOwnedExplicitIds(@Param("tenantId") Long tenantId,
            @Param("ownerUserId") Long ownerUserId,
            @Param("subjectType") String subjectType,
            @Param("explicitIds") List<Long> explicitIds);

    CapsuleOverviewVO selectTenantOverview(@Param("tenantId") Long tenantId);
}
