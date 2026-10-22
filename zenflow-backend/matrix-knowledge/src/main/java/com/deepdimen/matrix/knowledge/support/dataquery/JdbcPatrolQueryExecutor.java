package com.deepdimen.matrix.knowledge.support.dataquery;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.SQLTimeoutException;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.Semaphore;

/**
 * Executes only server-compiled queries against an injected, approved TOP reader pool. Do
 * not expose as a SQL endpoint or inject the platform application's default datasource.
 * Pool acquisition/socket timeouts and TLS must also be configured on that reader pool.
 */
public final class JdbcPatrolQueryExecutor implements ControlledQueryService.QueryExecutor {

	private final DataSource reader;

	private final Semaphore permits;

	public JdbcPatrolQueryExecutor(DataSource reader, int maxConcurrentQueries) {
		this.reader = Objects.requireNonNull(reader);
		if (maxConcurrentQueries < 1 || maxConcurrentQueries > 2) {
			throw new IllegalArgumentException("TOP query concurrency must be 1 or 2");
		}
		this.permits = new Semaphore(maxConcurrentQueries);
	}

	@Override
	public List<ControlledQueryService.PatrolRow> execute(ControlledQueryService.QueryExecution execution) {
		return execute(execution.query());
	}

	public List<ControlledQueryService.PatrolRow> execute(PatrolQueryCompiler.CompiledQuery query) {
		if (!permits.tryAcquire()) {
			throw new DataQueryException("QUERY_BUSY");
		}
		try (Connection connection = reader.getConnection()) {
			boolean wasReadOnly = connection.isReadOnly();
			try {
				connection.setReadOnly(true);
				try (PreparedStatement statement = connection.prepareStatement(query.sql())) {
					statement.setQueryTimeout(30);
					statement.setMaxRows(1001);
					statement.setFetchSize(100);
					for (int i = 0; i < query.parameters().size(); i++) {
						// JDBC 4.2 LocalDateTime preserves TOP local DATETIME bounds
						// without JVM-zone conversion.
						statement.setObject(i + 1, query.parameters().get(i));
					}
					List<ControlledQueryService.PatrolRow> rows = new ArrayList<>();
					try (ResultSet result = statement.executeQuery()) {
						while (result.next()) {
							if (rows.size() >= 1000) {
								throw new DataQueryException("RESULT_LIMIT_EXCEEDED");
							}
							rows.add(new ControlledQueryService.PatrolRow(result.getString("company_id"),
									result.getString("patrol_dept_id"), result.getString("department_name"),
									result.getLong("valid_patrol_count"), result.getInt("name_variant_count"),
									result.getLong("missing_name_count")));
						}
					}
					return List.copyOf(rows);
				}
			}
			finally {
				connection.setReadOnly(wasReadOnly);
			}
		}
		catch (SQLTimeoutException e) {
			throw new DataQueryException("QUERY_TIMEOUT");
		}
		catch (SQLException e) {
			throw new DataQueryException("QUERY_FAILED");
		}
		finally {
			permits.release();
		}
	}

}
