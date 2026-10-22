package com.deepdimen.matrix.capsule.security;

public record CapsuleIdentity(Long tenantId, Long ownerUserId, String subjectType) {
    public CapsuleIdentity {
        subjectType=subjectType==null?"ADMIN_USER":subjectType;
        if (!java.util.List.of("ADMIN_USER","APP_USER").contains(subjectType)) throw new IllegalArgumentException("Invalid capsule subject");
    }
    public CapsuleIdentity(Long tenantId, Long ownerUserId) { this(tenantId,ownerUserId,"ADMIN_USER"); }
}
