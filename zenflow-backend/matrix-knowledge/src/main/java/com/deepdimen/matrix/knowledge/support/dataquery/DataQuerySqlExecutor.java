package com.deepdimen.matrix.knowledge.support.dataquery;

import com.alibaba.druid.pool.DruidDataSource;
import java.nio.charset.StandardCharsets;
import java.sql.*;
import java.util.*;
import java.util.concurrent.Semaphore;
import static com.deepdimen.matrix.knowledge.support.dataquery.ControlledQueryService.*;

/**
 * Dynamic SELECT rows using the existing approved TLS reader, not the global ds-name
 * registry.
 */
public final class DataQuerySqlExecutor implements DataQueryAiChatService.Executor {

	@FunctionalInterface
	interface ReaderFactory {

		DruidDataSource create(DataQueryMetadataRepository.ReaderCredentials credentials);

	}

	public record Rows(List<String> columns, List<List<Object>> values) {
		public Rows {
			columns = List.copyOf(columns);
			values = values.stream().map(row -> Collections.unmodifiableList(new ArrayList<>(row))).toList();
		}

		@Override
		public String toString() {
			return "Rows[REDACTED]";
		}
	}

	private final DataQueryMetadataRepository metadata;

	@FunctionalInterface
	interface BudgetReaderFactory {

		DruidDataSource create(DataQueryMetadataRepository.ReaderCredentials credentials, int seconds);

	}

	private final BudgetReaderFactory readers;

	private static final Semaphore permits = new Semaphore(2);

	public DataQuerySqlExecutor(DataQueryMetadataRepository metadata) {
		this(metadata, (credentials, seconds) -> ApprovedDatasourceQueryExecutor.newReaderPool(credentials, seconds));
	}

	DataQuerySqlExecutor(DataQueryMetadataRepository metadata, ReaderFactory readers) {
		this(metadata, (credentials, seconds) -> readers.create(credentials));
	}

	DataQuerySqlExecutor(DataQueryMetadataRepository metadata, BudgetReaderFactory readers) {
		this.metadata = metadata;
		this.readers = readers;
	}

	public Rows execute(Identity identity, Authorization authorization, DataQuerySqlGuard.Prepared sql) {
		int seconds = budget(sql);
		return snapshot(identity, authorization, seconds, connection -> read(connection, sql, seconds));
	}

	@Override
	public Rows execute(Identity identity, Authorization authorization, DataQuerySqlGuard.Prepared sql,
			DataQueryExecutionTrace trace, int attempt) {
		int seconds = budget(sql);
		int connect = trace.start("ACQUIRE_CONNECTION", attempt, null);
		try {
			return snapshot(identity, authorization, seconds, connection -> {
				trace.success(connect);
				return read(connection, sql, seconds, trace, attempt);
			});
		}
		catch (RuntimeException error) {
			trace.failure(connect, error instanceof DataQueryException ? error.getMessage() : "QUERY_FAILED");
			throw error;
		}
	}

	static int budget(DataQuerySqlGuard.Prepared sql) {
		return DataQueryResultPreview.simpleAggregation(sql) ? 60 : 30;
	}

	@FunctionalInterface
	interface SnapshotWork<T> {

		T run(Connection connection) throws SQLException;

	}

	<T> T snapshot(Identity identity, Authorization authorization, SnapshotWork<T> work) {
		return snapshot(identity, authorization, 30, work);
	}

	private <T> T snapshot(Identity identity, Authorization authorization, int seconds, SnapshotWork<T> work) {

		if (!permits.tryAcquire())
			throw new DataQueryException("QUERY_BUSY");
		try {
			if (!authorization.equals(metadata.resolve(identity)))
				throw new DataQueryException("AUTHORIZATION_CHANGED");
			try (var pool = readers.create(metadata.credentials(identity, authorization.source()), seconds);
					var connection = pool.getConnection()) {
				connection.setReadOnly(true);
				connection.setTransactionIsolation(Connection.TRANSACTION_REPEATABLE_READ);
				connection.setAutoCommit(false);
				try {
					return work.run(connection);
				}
				finally {
					connection.rollback();
				}
			}
		}
		catch (DataQueryException e) {
			throw e;
		}
		catch (SQLException e) {
			throw failure(e);
		}
		catch (RuntimeException e) {
			throw new DataQueryException("QUERY_FAILED");
		}
		finally {
			permits.release();
		}
	}

	static DataQueryException failure(SQLException error) {
		if (error instanceof SQLTimeoutException)
			return new DataQueryException("QUERY_TIMEOUT").databaseError(error);
		// Druid fail-fast wraps the driver exception with SQLState=null/vendorCode=0.
		// Copy only bounded, structured diagnostics; never expose a driver message or
		// retain its cause.
		SQLException detail = error;
		Set<Throwable> seen = Collections.newSetFromMap(new IdentityHashMap<>());
		for (Throwable cause = error; cause != null && seen.size() < 8 && seen.add(cause); cause = cause.getCause()) {
			if (cause instanceof SQLException sql) {
				if (sql.getSQLState() != null)
					detail = sql;
				if (sql.getErrorCode() != 0) {
					detail = sql;
					break;
				}
			}
		}
		if (detail instanceof SQLTimeoutException
				|| ("HY000".equals(detail.getSQLState()) && detail.getErrorCode() == 3024))
			return new DataQueryException("QUERY_TIMEOUT").databaseError(detail);
		if (Set.of(1040, 1203, 1226).contains(detail.getErrorCode()))
			return new DataQueryException("DATASOURCE_RESOURCE_LIMIT").databaseError(detail);
		// Retain the existing direct-statement repair boundary; an opaque connect-time
		// wrapper must not trigger SQL repair.
		String state = error.getSQLState();
		// MySQL 1054 / SQLState 42S22; H2 42122 is used by local integration tests.
		if ("42S22".equals(state) || (error.getErrorCode() == 1054 && "42000".equals(state)))
			return new DataQueryException("SQL_COLUMN_REFERENCE_INVALID").databaseError(detail);
		return new DataQueryException("QUERY_FAILED").databaseError(detail);
	}

	static Rows read(Connection connection, DataQuerySqlGuard.Prepared sql) throws SQLException {
		return read(connection, sql, 30);
	}

	static Rows read(Connection connection, DataQuerySqlGuard.Prepared sql, int seconds) throws SQLException {
		return read(connection, sql, seconds, null, 0);
	}

	static Rows read(Connection connection, DataQuerySqlGuard.Prepared sql, int seconds, DataQueryExecutionTrace trace,
			int attempt) throws SQLException {
		int binding = trace == null ? -1
				: trace.start("BIND_STATEMENT", attempt, DataQueryMetadataRepository.fingerprint(sql.sql()));
		int reading = -1;
		try (var statement = connection.prepareStatement(sql.sql())) {
			statement.setQueryTimeout(Math.min(60, Math.max(1, seconds)));
			statement.setMaxRows(1001);
			statement.setFetchSize(100);
			for (int i = 0; i < sql.parameters().size(); i++)
				statement.setObject(i + 1, sql.parameters().get(i));
			if (trace != null)
				trace.success(binding);
			try (var result = invoke(statement, sql, trace, attempt)) {
				reading = trace == null ? -1 : trace.start("READ_RESULT", attempt, null);
				var meta = result.getMetaData();
				int count = meta.getColumnCount();
				if (count < 1 || count > 64)
					throw new DataQueryException("RESULT_LIMIT_EXCEEDED");
				var columns = new ArrayList<String>();
				long bytes = 0;
				for (int i = 1; i <= count; i++) {
					String label = meta.getColumnLabel(i);
					if (label == null || label.length() > 256)
						throw new DataQueryException("RESULT_LIMIT_EXCEEDED");
					columns.add(label);
					bytes += label.getBytes(StandardCharsets.UTF_8).length;
					switch (meta.getColumnType(i)) {
						case Types.BINARY, Types.VARBINARY, Types.LONGVARBINARY, Types.BLOB, Types.CLOB, Types.NCLOB,
								Types.ARRAY, Types.JAVA_OBJECT, Types.OTHER, Types.SQLXML:
							throw new DataQueryException("RESULT_TYPE_UNSUPPORTED");
						default:
							break;
					}
				}
				var rows = new ArrayList<List<Object>>();
				while (result.next()) {
					if (rows.size() == 1000)
						throw new DataQueryException("RESULT_LIMIT_EXCEEDED");
					var row = new ArrayList<Object>();
					for (int i = 1; i <= count; i++) {
						String text;
						// Connector/J rejects character streams for numeric/date values.
						// These bounded scalar types use strings without floating-point
						// loss;
						// potentially unbounded text must still use the limited stream
						// below.
						switch (meta.getColumnType(i)) {
							case Types.TINYINT, Types.SMALLINT, Types.INTEGER, Types.BIGINT, Types.NUMERIC,
									Types.DECIMAL, Types.FLOAT, Types.REAL, Types.DOUBLE, Types.BOOLEAN, Types.BIT,
									Types.DATE, Types.TIME, Types.TIMESTAMP, Types.TIME_WITH_TIMEZONE,
									Types.TIMESTAMP_WITH_TIMEZONE, Types.NULL:
								text = result.getString(i);
								break;
							default:
								try (var reader = result.getCharacterStream(i)) {
									if (reader == null)
										text = null;
									else {
										var chars = new char[8193];
										int n = 0, got;
										while (n < chars.length
												&& (got = reader.read(chars, n, chars.length - n)) != -1)
											n += got;
										if (n > 8192)
											throw new DataQueryException("RESULT_LIMIT_EXCEEDED");
										text = new String(chars, 0, n);
									}
								}
								catch (java.io.IOException e) {
									throw new DataQueryException("QUERY_FAILED");
								}
						}
						bytes += text == null ? 0 : text.getBytes(StandardCharsets.UTF_8).length;
						if (bytes > 1024 * 1024)
							throw new DataQueryException("RESULT_LIMIT_EXCEEDED");
						// Decimal strings preserve BIGINT/DECIMAL precision for browser
						// and chart consumers.
						row.add(text);
					}
					rows.add(row);
				}
				var rowsResult = new Rows(columns, rows);
				if (trace != null)
					trace.success(reading);
				return rowsResult;
			}
		}
		catch (SQLException | RuntimeException error) {
			if (trace != null) {
				String code = error instanceof DataQueryException ? error.getMessage()
						: error instanceof SQLException sqlError ? failure(sqlError).getMessage() : "QUERY_FAILED";
				trace.failure(binding, code);
				if (reading >= 0)
					trace.failure(reading, code);
			}
			throw error;
		}
	}

	private static ResultSet invoke(PreparedStatement statement, DataQuerySqlGuard.Prepared sql,
			DataQueryExecutionTrace trace, int attempt) throws SQLException {
		if (trace == null)
			return statement.executeQuery();
		int step = trace.start("JDBC_QUERY", attempt, DataQueryMetadataRepository.fingerprint(sql.sql()));
		ResultSet result;
		try {
			result = statement.executeQuery();
		}
		catch (SQLException | RuntimeException error) {
			trace.failure(step,
					error instanceof SQLException sqlError ? failure(sqlError).getMessage() : "QUERY_FAILED");
			throw error;
		}
		try {
			trace.success(step);
			return result;
		}
		catch (RuntimeException audit) {
			result.close();
			throw audit;
		}
	}

}
