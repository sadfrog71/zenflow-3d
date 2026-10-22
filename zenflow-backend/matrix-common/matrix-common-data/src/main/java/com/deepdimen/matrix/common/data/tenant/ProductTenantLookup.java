package com.deepdimen.matrix.common.data.tenant;

/** Optional single-product tenant source, avoiding a public self-HTTP administration lookup. */
public interface ProductTenantLookup {
    long defaultTenantId();
    boolean active(long tenantId);
}
