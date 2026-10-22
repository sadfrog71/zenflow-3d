package com.deepdimen.matrix.capsule.service;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.deepdimen.matrix.capsule.api.dto.CapsuleCreateRequest;
import com.deepdimen.matrix.capsule.api.dto.CapsulePageQuery;
import com.deepdimen.matrix.capsule.api.dto.CapsuleUpdateRequest;
import com.deepdimen.matrix.capsule.api.enums.CapsuleRecordType;
import com.deepdimen.matrix.capsule.api.enums.CapsuleStatus;
import com.deepdimen.matrix.capsule.api.vo.CapsuleDetailVO;
import com.deepdimen.matrix.capsule.api.vo.CapsuleOverviewVO;
import com.deepdimen.matrix.capsule.api.vo.CapsuleSummaryVO;
import com.deepdimen.matrix.capsule.api.vo.CapsuleVersionVO;
import com.deepdimen.matrix.capsule.security.CapsuleIdentity;
import java.time.LocalDateTime;
import java.util.List;

public interface CapsuleService {

    IPage<CapsuleSummaryVO> page(CapsuleIdentity identity, CapsulePageQuery query);

    CapsuleDetailVO detail(CapsuleIdentity identity, Long id);

    CapsuleDetailVO create(CapsuleIdentity identity, CapsuleCreateRequest request);

    CapsuleDetailVO update(CapsuleIdentity identity, Long id, CapsuleUpdateRequest request);

    CapsuleDetailVO updateMetadata(CapsuleIdentity identity, Long id, String title, String tags, Boolean favorite, int expectedVersion);

    void trash(CapsuleIdentity identity, Long id, int expectedVersion);

    CapsuleDetailVO changeStatus(
            CapsuleIdentity identity, Long id, int expectedVersion, CapsuleStatus status);

    List<CapsuleVersionVO> versions(CapsuleIdentity identity, Long id);
    List<CapsuleVersionVO> versions(CapsuleIdentity identity,Long id,Integer beforeRevision,int limit);

    List<CapsuleDetailVO> searchForContext(
            CapsuleIdentity identity,
            String query,
            LocalDateTime from,
            LocalDateTime to,
            CapsuleRecordType recordType,
            List<Long> explicitIds,
            int limit);

    long countOwnedExplicitIds(CapsuleIdentity identity, List<Long> explicitIds);

    CapsuleOverviewVO tenantOverview(Long tenantId);
}
