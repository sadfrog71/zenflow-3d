package com.deepdimen.matrix.knowledge.support.dataquery;

import java.io.StringReader;
import java.sql.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

/** Mirrors Connector/J: numeric/date cells cannot be read as binary/character streams. */
class DataQuerySqlExecutorTest {

	@Test
	void mysqlScalarsUseLosslessStringsNotCharacterStreams() throws Exception {
		Object[][] cases = { { Types.TINYINT, "127" }, { Types.SMALLINT, "32767" }, { Types.INTEGER, "2147483647" },
				{ Types.BIGINT, "18446744073709551615" }, { Types.NUMERIC, "12345678901234567890.123456789" },
				{ Types.DECIMAL, "99999999999999999999.000000001" }, { Types.FLOAT, "1.5" }, { Types.REAL, "2.5" },
				{ Types.DOUBLE, "3.5" }, { Types.BOOLEAN, "1" }, { Types.BIT, "0" }, { Types.DATE, "2026-09-12" },
				{ Types.TIME, "13:00:00" }, { Types.TIMESTAMP, "2026-09-12 13:00:00.123456" },
				{ Types.TIME_WITH_TIMEZONE, "13:00:00+08:00" },
				{ Types.TIMESTAMP_WITH_TIMEZONE, "2026-09-12 13:00:00+08:00" }, { Types.NULL, null } };
		for (Object[] item : cases) {
			var fixture = new Fixture((Integer) item[0]);
			when(fixture.result.getString(1)).thenReturn((String) item[1]);
			when(fixture.result.getCharacterStream(1))
				.thenThrow(new SQLDataException("Conversion unavailable", "22018"));
			assertThat(fixture.read().values().get(0).get(0)).as("JDBC type %s", item[0]).isEqualTo(item[1]);
			verify(fixture.result, never()).getCharacterStream(anyInt());
			verify(fixture.statement).setQueryTimeout(30);
			verify(fixture.statement).setMaxRows(1001);
			verify(fixture.statement).setFetchSize(100);
			verify(fixture.statement).close();
			verify(fixture.result).close();
		}
	}

	@Test
	void textKeepsBoundedStreamingAndSourceNull() throws Exception {
		for (int type : new int[] { Types.CHAR, Types.VARCHAR, Types.LONGVARCHAR, Types.NCHAR, Types.NVARCHAR,
				Types.LONGNVARCHAR }) {
			var fixture = new Fixture(type);
			when(fixture.result.getCharacterStream(1)).thenReturn(new StringReader("甲".repeat(8192)));
			assertThat(fixture.read().values().get(0).get(0)).isEqualTo("甲".repeat(8192));
			verify(fixture.result, never()).getString(anyInt());
		}
		var missing = new Fixture(Types.VARCHAR);
		assertThat(missing.read().values().get(0).get(0)).isNull();
	}

	@Test
	void oversizedTextStillFailsAndClosesResources() throws Exception {
		var fixture = new Fixture(Types.LONGVARCHAR);
		when(fixture.result.getCharacterStream(1)).thenReturn(new StringReader("x".repeat(8193)));
		assertThatThrownBy(fixture::read).hasMessage("RESULT_LIMIT_EXCEEDED");
		verify(fixture.statement).close();
		verify(fixture.result).close();
		verify(fixture.result, never()).getString(anyInt());
	}

	@Test
	void binaryAndObjectTypesRemainUnsupported() throws Exception {
		for (int type : new int[] { Types.BINARY, Types.VARBINARY, Types.LONGVARBINARY, Types.BLOB, Types.CLOB,
				Types.NCLOB, Types.ARRAY, Types.JAVA_OBJECT, Types.OTHER, Types.SQLXML }) {
			var fixture = new Fixture(type);
			assertThatThrownBy(fixture::read).hasMessage("RESULT_TYPE_UNSUPPORTED");
			verify(fixture.result, never()).getString(anyInt());
			verify(fixture.result, never()).getCharacterStream(anyInt());
		}
	}

	@Test
	void connectionQuotaFailureClosesPoolAndReleasesPermitWithoutTryingSqlOrAnotherAccount() throws Exception {
		var fixture = new DataQueryAiChatTest();
		fixture.setup();
		var scope = fixture.metadata.resolve(fixture.owner);
		var pool = mock(com.alibaba.druid.pool.DruidDataSource.class);
		var root = new SQLException("SYNTHETIC credential DO_NOT_PERSIST", "42000", 1226);
		when(pool.getConnection()).thenThrow(new com.alibaba.druid.pool.DataSourceNotAvailableException(root));
		var executor = new DataQuerySqlExecutor(fixture.metadata, credentials -> pool);
		for (int attempt = 0; attempt < 3; attempt++) {
			assertThatThrownBy(() -> executor.execute(fixture.owner, scope, new DataQuerySqlGuard.Prepared(
					"SELECT COUNT(*) FROM dev_patrol_record", List.of(), Set.of("dev_patrol_record"))))
				.hasMessage("DATASOURCE_RESOURCE_LIMIT");
		}
		verify(pool, times(3)).getConnection();
		verify(pool, times(3)).close();
		verifyNoMoreInteractions(pool);
	}

	static final class Fixture {

		final Connection connection = mock(Connection.class);

		final PreparedStatement statement = mock(PreparedStatement.class);

		final ResultSet result = mock(ResultSet.class);

		Fixture(int type) throws Exception {
			var metadata = mock(ResultSetMetaData.class);
			when(connection.prepareStatement(anyString())).thenReturn(statement);
			when(statement.executeQuery()).thenReturn(result);
			when(result.getMetaData()).thenReturn(metadata);
			when(metadata.getColumnCount()).thenReturn(1);
			when(metadata.getColumnLabel(1)).thenReturn("value");
			when(metadata.getColumnType(1)).thenReturn(type);
			when(result.next()).thenReturn(true, false);
		}

		DataQuerySqlExecutor.Rows read() throws Exception {
			return DataQuerySqlExecutor.read(connection, new DataQuerySqlGuard.Prepared(
					"SELECT COUNT(*) FROM dev_patrol_record", List.of(), Set.of("dev_patrol_record")));
		}

	}

}