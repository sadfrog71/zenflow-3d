package com.deepdimen.matrix.knowledge.support.dataquery;

import org.springframework.stereotype.Component;
import org.springframework.jdbc.core.JdbcTemplate;
import javax.sql.DataSource;
import java.net.URI;
import java.util.*;

/**
 * Read-only global denial check for legacy SQL. Does not confer any query authorization.
 */
@Component
public final class DataQueryLegacyFence {

	private final JdbcTemplate jdbc;

	private volatile boolean registryObserved;

	/**
	 * Keep registered datasets fenced even if persistence is subsequently switched off.
	 */
	public java.util.Set<Long> registeredPilotIds(boolean required) {
		try {
			var ids = new java.util.HashSet<>(
					jdbc.queryForList("SELECT DISTINCT data_id FROM ai_dataquery_binding", Long.class));
			registryObserved = true;
			return ids;
		}
		catch (org.springframework.dao.DataAccessException ex) {
			// Pre-feature installations may have no table; outages/permission errors must
			// not look like no pilots.
			boolean missing = false;
			for (Throwable cause = ex; cause != null; cause = cause.getCause()) {
				if (cause instanceof java.sql.SQLException sql
						&& (sql.getErrorCode() == 1146 || sql.getErrorCode() == 42102))
					missing = true;
			}
			if (missing && !required && !registryObserved)
				return java.util.Set.of();
			throw new DataQueryException("PILOT_INVENTORY_UNAVAILABLE");
		}
	}

	public DataQueryLegacyFence(DataSource platformSource) {
		jdbc = new JdbcTemplate(platformSource);
		jdbc.setQueryTimeout(5);
	}

	private record Source(String name, String target) {
	}

	private String target(String url) {
		try {
			if (url == null || !url.startsWith("jdbc:"))
				throw new IllegalArgumentException();
			var uri = URI.create(url.substring(5));
			if (uri.getHost() == null || uri.getPath() == null || uri.getUserInfo() != null)
				throw new IllegalArgumentException();
			int port = uri.getPort() < 0 ? ("mysql".equalsIgnoreCase(uri.getScheme()) ? 3306 : -1) : uri.getPort();
			return uri.getScheme().toLowerCase(Locale.ROOT) + "://" + uri.getHost().toLowerCase(Locale.ROOT) + ":"
					+ port + uri.getPath();
		}
		catch (RuntimeException e) {
			throw new DataQueryException("PILOT_INVENTORY_UNAVAILABLE");
		}
	}

	private List<Source> sources(long id) {
		return jdbc.query("""
				SELECT s.name,s.url FROM ai_data d JOIN ai_datasource s ON s.name=d.ds_name
				WHERE d.data_id=? AND d.del_flag='0' AND s.del_flag='0'
				""", (rs, n) -> new Source(rs.getString(1), target(rs.getString(2))), id);
	}

	public void rejectProtectedAlias(long candidate, Set<Long> pilots) {
		if (pilots.isEmpty())
			return;
		try {
			var protectedSources = new ArrayList<Source>();
			for (long id : pilots) {
				var found = sources(id);
				if (found.isEmpty())
					throw new DataQueryException("PILOT_INVENTORY_UNAVAILABLE");
				protectedSources.addAll(found);
			}
			var candidateSources = sources(candidate);
			if (candidateSources.isEmpty())
				throw new DataQueryException("PILOT_INVENTORY_UNAVAILABLE");
			for (var source : candidateSources)
				for (var protectedSource : protectedSources) {
					if (source.name().equalsIgnoreCase(protectedSource.name())
							|| source.target().equals(protectedSource.target()))
						throw new DataQueryException("CONTROLLED_QUERY_REQUIRED");
				}
		}
		catch (DataQueryException e) {
			throw e;
		}
		catch (RuntimeException e) {
			throw new DataQueryException("PILOT_INVENTORY_UNAVAILABLE");
		}
	}

}
