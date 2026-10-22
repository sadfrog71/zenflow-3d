package com.deepdimen.matrix.admin.api.service;

import java.time.LocalDateTime;
import java.util.List;

import com.deepdimen.matrix.admin.api.dto.BusinessMessageSnapshot;

/** Read-only cross-domain port for the current user's visible business messages. */
public interface BusinessMessageReader {

	List<BusinessMessageSnapshot> listUnread(Long tenantId, Long userId, Long orgId, LocalDateTime from, int limit);

}
