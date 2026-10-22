package com.deepdimen.matrix.capsule.service;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.deepdimen.matrix.capsule.mapper.CapsuleMapper;
import com.deepdimen.matrix.capsule.mapper.CapsuleRevisionMapper;
import com.deepdimen.matrix.capsule.security.CapsuleIdentity;
import com.deepdimen.matrix.capsule.service.impl.CapsuleServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class CapsuleServiceOwnershipTest {

    @Mock private CapsuleMapper capsuleMapper;
    @Mock private CapsuleRevisionMapper revisionMapper;

    private CapsuleService service;

    @BeforeEach
    void setUp() {
        service = new CapsuleServiceImpl(capsuleMapper, revisionMapper);
    }

    @Test
    void detailRequiresTenantAndOwnerAndDoesNotFallBackToIdOnly() {
        CapsuleIdentity identity = new CapsuleIdentity(7L, 42L);
        when(capsuleMapper.selectOwnedDetail(7L, 42L, "ADMIN_USER", 99L)).thenReturn(null);

        assertThrows(IllegalArgumentException.class, () -> service.detail(identity, 99L));

        verify(capsuleMapper).selectOwnedDetail(7L, 42L, "ADMIN_USER", 99L);
    }

    @Test
    void versionListingReauthorizesTheCapsuleBeforeReadingRevisions() {
        CapsuleIdentity identity = new CapsuleIdentity(7L, 43L);
        when(capsuleMapper.selectOwnedDetail(7L, 43L, "ADMIN_USER", 99L)).thenReturn(null);

        assertThrows(IllegalArgumentException.class, () -> service.versions(identity, 99L));

        verify(capsuleMapper).selectOwnedDetail(7L, 43L, "ADMIN_USER", 99L);
    }
}
