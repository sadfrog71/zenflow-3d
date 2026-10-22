package com.deepdimen.matrix.common.audit.handle;

import com.deepdimen.matrix.admin.api.entity.SysAuditLog;
import com.deepdimen.matrix.common.audit.annotation.Audit;
import org.javers.core.Changes;

import java.util.List;

/**
 * @author caoyuan
 * @date 2023/2/26
 *
 * 审计日志处理器
 */
public interface IAuditLogHandle {

	void handle(Audit audit, Changes changes);

	void asyncSend(List<SysAuditLog> auditLogList);

}
