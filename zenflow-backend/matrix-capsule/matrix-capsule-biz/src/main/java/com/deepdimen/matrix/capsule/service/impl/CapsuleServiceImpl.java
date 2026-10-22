package com.deepdimen.matrix.capsule.service.impl;

import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.core.toolkit.IdWorker;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.deepdimen.matrix.capsule.api.dto.CapsuleCreateRequest;
import com.deepdimen.matrix.capsule.api.dto.CapsulePageQuery;
import com.deepdimen.matrix.capsule.api.dto.CapsuleUpdateRequest;
import com.deepdimen.matrix.capsule.api.enums.CapsuleRecordType;
import com.deepdimen.matrix.capsule.api.enums.CapsuleStatus;
import com.deepdimen.matrix.capsule.api.vo.CapsuleDetailVO;
import com.deepdimen.matrix.capsule.api.vo.CapsuleOverviewVO;
import com.deepdimen.matrix.capsule.api.vo.CapsuleSummaryVO;
import com.deepdimen.matrix.capsule.api.vo.CapsuleVersionVO;
import com.deepdimen.matrix.capsule.entity.CapsuleEntity;
import com.deepdimen.matrix.capsule.entity.CapsuleRevisionEntity;
import com.deepdimen.matrix.capsule.mapper.CapsuleMapper;
import com.deepdimen.matrix.capsule.mapper.CapsuleRevisionMapper;
import com.deepdimen.matrix.capsule.security.CapsuleIdentity;
import com.deepdimen.matrix.capsule.service.CapsuleService;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDateTime;
import java.util.HexFormat;
import java.util.List;
import java.util.Arrays;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class CapsuleServiceImpl implements CapsuleService {

    private static final int MAX_PAGE_SIZE = 100;
    private static final String CONTENT_FORMAT = "MARKDOWN";

    private final CapsuleMapper capsuleMapper;
    private final CapsuleRevisionMapper revisionMapper;

    @Override
    public IPage<CapsuleSummaryVO> page(CapsuleIdentity identity, CapsulePageQuery query) {
        long current = Math.max(1, query.getCurrent());
        long size = Math.min(MAX_PAGE_SIZE, Math.max(1, query.getSize()));
        return capsuleMapper.selectOwnedPage(
                new Page<>(current, size), identity.tenantId(), identity.ownerUserId(), identity.subjectType(), query);
    }

    @Override
    public CapsuleDetailVO detail(CapsuleIdentity identity, Long id) {
        CapsuleDetailVO detail = capsuleMapper.selectOwnedDetail(
                identity.tenantId(), identity.ownerUserId(), identity.subjectType(), id);
        if (detail == null) {
            throw notFound();
        }
        return detail;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public CapsuleDetailVO create(CapsuleIdentity identity, CapsuleCreateRequest request) {
        LocalDateTime now = LocalDateTime.now();
        long capsuleId = IdWorker.getId();
        long revisionId = IdWorker.getId();

        CapsuleEntity capsule = new CapsuleEntity();
        capsule.setId(capsuleId);
        capsule.setTenantId(identity.tenantId());
        capsule.setOwnerUserId(identity.ownerUserId());
        capsule.setOwnerSubjectType(identity.subjectType());
        capsule.setRecordType(request.getRecordType());
        capsule.setTitle(normalizeTitle(request.getTitle(), request.getContent()));
        capsule.setCurrentRevisionId(revisionId);
        capsule.setSourceType("MANUAL");
        capsule.setSourceLabel(request.getSourceLabel());
        capsule.setTags(normalizeTags(request.getTags()));
        capsule.setStatus(CapsuleStatus.ACTIVE);
        capsule.setFavoriteFlag("0");
        capsule.setCapturedAt(request.getCapturedAt()==null?now:request.getCapturedAt());
        capsule.setVersion(1);
        capsule.setDelFlag("0");

        CapsuleRevisionEntity revision = buildRevision(
                revisionId, capsuleId, identity, 1, request.getContent(), "创建胶囊");
        if (capsuleMapper.insert(capsule) != 1 || revisionMapper.insert(revision) != 1) {
            throw new IllegalStateException("胶囊创建失败");
        }
        return detail(identity, capsuleId);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public CapsuleDetailVO update(
            CapsuleIdentity identity, Long id, CapsuleUpdateRequest request) {
        CapsuleDetailVO current = detail(identity, id);
        if (!request.getVersion().equals(current.getVersion())) {
            throw versionConflict();
        }

        int nextRevisionNo = current.getRevisionNo() + 1;
        long revisionId = IdWorker.getId();
        CapsuleRevisionEntity revision = buildRevision(
                revisionId,
                id,
                identity,
                nextRevisionNo,
                request.getContent(),
                "用户编辑");
        if (revisionMapper.insert(revision) != 1) {
            throw new IllegalStateException("胶囊版本保存失败");
        }

        LambdaUpdateWrapper<CapsuleEntity> update = ownedVersionUpdate(identity, id, current.getVersion());
        update.set(CapsuleEntity::getTitle, normalizeTitle(request.getTitle(), request.getContent()))
                .set(CapsuleEntity::getRecordType, request.getRecordType())
                .set(CapsuleEntity::getSourceLabel, request.getSourceLabel())
                .set(CapsuleEntity::getTags, normalizeTags(request.getTags()))
                .set(CapsuleEntity::getCurrentRevisionId, revisionId)
                .set(CapsuleEntity::getVersion, current.getVersion() + 1);
        if (capsuleMapper.update(null, update) != 1) {
            throw versionConflict();
        }
        return detail(identity, id);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public CapsuleDetailVO updateMetadata(CapsuleIdentity identity,Long id,String title,String tags,Boolean favorite,int expectedVersion) {
        var current=detail(identity,id);
        if(current.getVersion()!=expectedVersion)throw versionConflict();
        var update=ownedVersionUpdate(identity,id,expectedVersion);
        if(title!=null)update.set(CapsuleEntity::getTitle,normalizeTitle(title,current.getContent()));
        if(tags!=null)update.set(CapsuleEntity::getTags,normalizeTags(tags));
        if(favorite!=null)update.set(CapsuleEntity::getFavoriteFlag,favorite?"1":"0");
        update.set(CapsuleEntity::getVersion,expectedVersion+1);
        if(capsuleMapper.update(null,update)!=1)throw versionConflict();
        return detail(identity,id);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void trash(CapsuleIdentity identity,Long id,int expectedVersion) {
        detail(identity,id);
        var update=ownedVersionUpdate(identity,id,expectedVersion);
        update.set(CapsuleEntity::getStatus,CapsuleStatus.TRASHED).set(CapsuleEntity::getVersion,expectedVersion+1);
        if(capsuleMapper.update(null,update)!=1)throw versionConflict();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public CapsuleDetailVO changeStatus(
            CapsuleIdentity identity, Long id, int expectedVersion, CapsuleStatus status) {
        CapsuleDetailVO current = detail(identity, id);
        if (current.getVersion() != expectedVersion) {
            throw versionConflict();
        }
        if (status != CapsuleStatus.ACTIVE && status != CapsuleStatus.ARCHIVED) {
            throw new IllegalArgumentException("当前版本仅支持归档和恢复");
        }
        LambdaUpdateWrapper<CapsuleEntity> update = ownedVersionUpdate(identity, id, expectedVersion);
        update.set(CapsuleEntity::getStatus, status)
                .set(CapsuleEntity::getVersion, expectedVersion + 1);
        if (capsuleMapper.update(null, update) != 1) {
            throw versionConflict();
        }
        return detail(identity, id);
    }

    @Override
    public List<CapsuleVersionVO> versions(CapsuleIdentity identity, Long id) {
        detail(identity, id);
        return revisionMapper.selectOwnedVersions(identity.tenantId(), identity.ownerUserId(), identity.subjectType(), id);
    }
    @Override
    public List<CapsuleVersionVO> versions(CapsuleIdentity identity,Long id,Integer beforeRevision,int limit) {
        detail(identity,id);
        return revisionMapper.selectOwnedVersionsPage(identity.tenantId(),identity.ownerUserId(),identity.subjectType(),id,beforeRevision,Math.min(51,Math.max(1,limit)));
    }

    @Override
    public List<CapsuleDetailVO> searchForContext(
            CapsuleIdentity identity,
            String query,
            LocalDateTime from,
            LocalDateTime to,
            CapsuleRecordType recordType,
            List<Long> explicitIds,
            int limit) {
        return capsuleMapper.selectOwnedForContext(
                identity.tenantId(),
                identity.ownerUserId(), identity.subjectType(),
                query,
                from,
                to,
                recordType,
                explicitIds,
                Math.min(50, Math.max(1, limit)));
    }

    @Override
    public long countOwnedExplicitIds(CapsuleIdentity identity, List<Long> explicitIds) {
        if (explicitIds == null || explicitIds.isEmpty()) {
            return 0;
        }
        return capsuleMapper.countOwnedExplicitIds(
                identity.tenantId(), identity.ownerUserId(), identity.subjectType(), explicitIds);
    }

    @Override
    public CapsuleOverviewVO tenantOverview(Long tenantId) {
        CapsuleOverviewVO overview = capsuleMapper.selectTenantOverview(tenantId);
        return overview == null ? new CapsuleOverviewVO() : overview;
    }

    private CapsuleRevisionEntity buildRevision(
            long revisionId,
            long capsuleId,
            CapsuleIdentity identity,
            int revisionNo,
            String content,
            String changeReason) {
        CapsuleRevisionEntity revision = new CapsuleRevisionEntity();
        revision.setId(revisionId);
        revision.setCapsuleId(capsuleId);
        revision.setTenantId(identity.tenantId());
        revision.setOwnerUserId(identity.ownerUserId());
        revision.setOwnerSubjectType(identity.subjectType());
        revision.setRevisionNo(revisionNo);
        revision.setContentFormat(CONTENT_FORMAT);
        revision.setContent(content);
        revision.setContentHash(sha256(content));
        revision.setChangeReason(changeReason);
        return revision;
    }

    private LambdaUpdateWrapper<CapsuleEntity> ownedVersionUpdate(
            CapsuleIdentity identity, Long id, int expectedVersion) {
        return new LambdaUpdateWrapper<CapsuleEntity>()
                .eq(CapsuleEntity::getId, id)
                .eq(CapsuleEntity::getTenantId, identity.tenantId())
                .eq(CapsuleEntity::getOwnerUserId, identity.ownerUserId())
                .eq(CapsuleEntity::getOwnerSubjectType, identity.subjectType())
                .eq(CapsuleEntity::getVersion, expectedVersion)
                .eq(CapsuleEntity::getDelFlag, "0");
    }

    static String normalizeTitle(String title, String content) {
        String candidate = title == null || title.isBlank()
                ? content.lines().filter(line -> !line.isBlank()).findFirst().orElse("未命名胶囊")
                : title.trim();
        if (title == null || title.isBlank()) {
            candidate = candidate.stripLeading().replaceFirst("^#{1,6}\\s+", "").trim();
            if (candidate.isBlank()) {
                candidate = "未命名胶囊";
            }
        }
        return candidate.length() > 120 ? candidate.substring(0, 120) : candidate;
    }

    private String normalizeTags(String rawTags) {
        if (rawTags == null || rawTags.isBlank()) {
            return null;
        }
        String normalized = Arrays.stream(rawTags.split(","))
                .map(String::trim)
                .filter(tag -> !tag.isBlank())
                .map(tag -> tag.replace(",", ""))
                .distinct()
                .limit(20)
                .collect(Collectors.joining(","));
        return normalized.isBlank() ? null : normalized.substring(0, Math.min(1000, normalized.length()));
    }

    private String sha256(String content) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(content.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("运行环境不支持 SHA-256", exception);
        }
    }

    private IllegalArgumentException notFound() {
        return new IllegalArgumentException("胶囊不存在或无权访问");
    }

    private IllegalStateException versionConflict() {
        return new IllegalStateException("胶囊已被更新，请刷新后重试");
    }
}
