package com.deepdimen.matrix.work.api.service;

import java.util.List;

public interface TaskFileAccess {

	void requireAttachable(Long tenantId, Long actorId, List<Long> fileIds);

}
