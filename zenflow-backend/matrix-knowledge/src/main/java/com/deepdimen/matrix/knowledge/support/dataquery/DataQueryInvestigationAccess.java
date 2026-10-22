package com.deepdimen.matrix.knowledge.support.dataquery;

import org.springframework.jdbc.core.JdbcTemplate;
import javax.sql.DataSource;
import java.util.*;
import java.util.function.BiConsumer;

/**
 * Re-check current tenant role/menu grants, not a saved access token or worker thread
 * SecurityContext.
 */
final class DataQueryInvestigationAccess implements BiConsumer<DataQuerySecurity.ManagementActor, String> {

	private final JdbcTemplate jdbc;

	DataQueryInvestigationAccess(DataSource source) {
		jdbc = new JdbcTemplate(source);
		jdbc.setQueryTimeout(5);
	}

	@Override
	public void accept(DataQuerySecurity.ManagementActor actor, String capability) {
		var id = actor.identity();
		var live = new HashSet<>(jdbc.queryForList(
				"SELECT DISTINCT m.permission FROM sys_user u JOIN sys_tenant_user tu ON tu.user_id=u.user_id JOIN sys_user_role ur ON ur.user_id=u.user_id AND ur.tenant_id=tu.tenant_id JOIN sys_role r ON r.role_id=ur.role_id AND r.tenant_id=ur.tenant_id JOIN sys_role_menu rm ON rm.role_id=r.role_id JOIN sys_menu m ON m.menu_id=rm.menu_id AND m.tenant_id=r.tenant_id WHERE tu.tenant_id=? AND u.user_id=? AND u.del_flag='0' AND u.lock_flag='0' AND r.del_flag='0' AND m.del_flag='0' AND m.permission LIKE 'knowledge_dataquery_%'",
				String.class, id.tenantId(), id.userId()));
		live.retainAll(actor.permissions());
		var current = new DataQuerySecurity.ManagementActor(id, live);
		if ("read".equals(capability))
			current.requireRead();
		else
			current.require(capability, Set.of("review", "publish").contains(capability));
	}

}
