package com.deepdimen.matrix.knowledge.support.dataquery;

import com.alibaba.druid.pool.DruidDataSource;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Properties;
import java.util.concurrent.Semaphore;
import static com.deepdimen.matrix.knowledge.support.dataquery.ControlledQueryService.*;

/**
 * Uses approved existing MATRIX datasource records by tenant and ID, not the global
 * name-based Anyline registry. No fallback to the platform datasource or model SQL.
 * Short-lived pools avoid retaining stale rotated credentials/targets; global limit is
 * two.
 */
public final class ApprovedDatasourceQueryExecutor implements QueryExecutor {

	@FunctionalInterface
	interface ReaderPoolFactory {

		DruidDataSource create(DataQueryMetadataRepository.ReaderCredentials credentials);

	}

	private final DataQueryMetadataRepository metadata;

	private final ReaderPoolFactory pools;

	private final Semaphore permits = new Semaphore(2);

	public ApprovedDatasourceQueryExecutor(DataQueryMetadataRepository metadata) {
		this(metadata, ApprovedDatasourceQueryExecutor::newReaderPool);
	}

	ApprovedDatasourceQueryExecutor(DataQueryMetadataRepository metadata, ReaderPoolFactory pools) {
		this.metadata = Objects.requireNonNull(metadata);
		this.pools = Objects.requireNonNull(pools);
	}

	@Override
	public List<PatrolRow> execute(QueryExecution execution) {
		if (!permits.tryAcquire())
			throw new DataQueryException("QUERY_BUSY");
		try {
			// Recheck just before opening a reader connection; includes grant contents
			// and binding.
			if (!metadata.resolve(execution.identity()).equals(execution.authorization())) {
				throw new DataQueryException("AUTHORIZATION_CHANGED");
			}
			var credentials = metadata.credentials(execution.identity(), execution.authorization().source());
			try (DruidDataSource pool = pools.create(credentials)) {
				return new JdbcPatrolQueryExecutor(pool, 1).execute(execution);
			}
		}
		catch (DataQueryException e) {
			throw e;
		}
		catch (RuntimeException e) {
			throw new DataQueryException("QUERY_FAILED");
		}
		finally {
			permits.release();
		}
	}

	static DruidDataSource newReaderPool(DataQueryMetadataRepository.ReaderCredentials credentials) {
		return newReaderPool(credentials, 30);
	}

	static DruidDataSource newReaderPool(DataQueryMetadataRepository.ReaderCredentials credentials, int seconds) {
		seconds = Math.min(60, Math.max(1, seconds));
		String url = canonicalReaderUrl(credentials.url);
		if (credentials.username == null || credentials.username.isBlank() || credentials.password == null
				|| credentials.password.isBlank()) {
			throw new DataQueryException("DATASOURCE_NOT_CONFIGURED");
		}
		DruidDataSource pool = new DruidDataSource();
		pool.setName("dataquery-approved-reader");
		pool.setDriverClassName("com.mysql.cj.jdbc.Driver");
		pool.setUrl(url);
		pool.setUsername(credentials.username);
		pool.setPassword(credentials.password);
		pool.setInitialSize(0);
		pool.setMinIdle(0);
		pool.setMaxActive(1);
		pool.setMaxWait(3000);
		pool.setConnectTimeout(5000);
		pool.setSocketTimeout((seconds + 5) * 1000);
		pool.setQueryTimeout(seconds);
		// Keep the server-side cap aligned with this bounded, dedicated reader pool.
		pool.setConnectionInitSqls(java.util.List.of("SET SESSION max_execution_time = " + seconds * 1000));
		pool.setDefaultReadOnly(true);
		pool.setConnectionErrorRetryAttempts(0);
		pool.setBreakAfterAcquireFailure(true);
		pool.setFailFast(true);
		Properties properties = new Properties();
		properties.setProperty("sslMode", "VERIFY_IDENTITY");
		properties.setProperty("connectionTimeZone", "Asia/Shanghai");
		properties.setProperty("forceConnectionTimeZoneToSession", "true");
		properties.setProperty("allowMultiQueries", "false");
		properties.setProperty("allowLoadLocalInfile", "false");
		properties.setProperty("allowUrlInLocalInfile", "false");
		properties.setProperty("allowPublicKeyRetrieval", "false");
		properties.setProperty("autoDeserialize", "false");
		properties.setProperty("connectTimeout", "5000");
		properties.setProperty("socketTimeout", Integer.toString((seconds + 5) * 1000));
		properties.setProperty("characterEncoding", "UTF-8");
		pool.setConnectProperties(properties);
		return pool;
	}

	/**
	 * Only a single MySQL host and the TOP schema; reject embedded secrets and driver
	 * extensions.
	 */
	static String canonicalReaderUrl(String jdbcUrl) {
		try {
			if (jdbcUrl == null || !jdbcUrl.startsWith("jdbc:mysql://"))
				throw new URISyntaxException("", "scheme");
			URI uri = new URI(jdbcUrl.substring(5));
			if (uri.getHost() == null || uri.getUserInfo() != null || uri.getFragment() != null
					|| !"/top_device_biz".equals(uri.getRawPath()) || uri.getPort() == 0 || uri.getPort() > 65535) {
				throw new URISyntaxException("", "target");
			}
			// Existing benign values may be present; effective URL is rebuilt without
			// options.
			Map<String, String> permitted = Map.of("useUnicode", "true", "characterEncoding", "UTF-8", "sslMode",
					"VERIFY_IDENTITY", "connectionTimeZone", "Asia/Shanghai", "forceConnectionTimeZoneToSession",
					"true", "allowMultiQueries", "false", "allowPublicKeyRetrieval", "false", "serverTimezone",
					"Asia/Shanghai");
			Map<String, String> seen = new HashMap<>();
			if (uri.getRawQuery() != null) {
				for (String item : uri.getRawQuery().split("&", -1)) {
					String[] parts = item.split("=", 2);
					if (parts.length != 2)
						throw new URISyntaxException("", "option");
					String key = URLDecoder.decode(parts[0], StandardCharsets.UTF_8);
					String value = URLDecoder.decode(parts[1], StandardCharsets.UTF_8);
					if (!value.equals(permitted.get(key)) || seen.put(key, value) != null) {
						throw new URISyntaxException("", "option");
					}
				}
			}
			return "jdbc:" + new URI("mysql", null, uri.getHost(), uri.getPort(), "/top_device_biz", null, null);
		}
		catch (URISyntaxException | IllegalArgumentException e) {
			throw new DataQueryException("DATASOURCE_CONFIGURATION_UNSAFE");
		}
	}

}
