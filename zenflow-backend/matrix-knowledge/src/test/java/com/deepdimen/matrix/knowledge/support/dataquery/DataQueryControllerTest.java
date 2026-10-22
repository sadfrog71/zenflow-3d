package com.deepdimen.matrix.knowledge.support.dataquery;

import com.deepdimen.matrix.common.security.service.MatrixUser;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import java.time.Clock;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import static com.deepdimen.matrix.knowledge.support.dataquery.ControlledQueryService.*;

class DataQueryControllerTest {

	private static final String PLAN = """
			{"dataset":"top.patrol","metric":"valid_patrol_count","dimension":"department",
			"from":"2026-08-01","to":"2026-09-01","timezone":"Asia/Shanghai","order":"DESC","filters":{}}
			""";

	private MockMvc mvc;

	private final AtomicInteger executions = new AtomicInteger();

	private Identity resolvedIdentity;

	@BeforeEach
	void setup() {
		QueryAudit audit = mock(QueryAudit.class);
		var service = new ControlledQueryService(identity -> {
			resolvedIdentity = identity;
			return new Authorization("approved-scope");
		}, identity -> new PublishedRule("approved-rule", DEFINITION, true), query -> {
			executions.incrementAndGet();
			return List.of(new PatrolRow("physical-company", "physical-dept", null, 12L, 2, 1L));
		}, audit, Clock.systemUTC(), 10, Duration.ofMinutes(5));
		mvc = MockMvcBuilders.standaloneSetup(new DataQueryController(service)).build();
	}

	@AfterEach
	void clearIdentity() {
		SecurityContextHolder.clearContext();
	}

	private void login(long tenant, long userId) {
		MatrixUser user = mock(MatrixUser.class);
		when(user.getTenantId()).thenReturn(tenant);
		when(user.getId()).thenReturn(userId);
		SecurityContextHolder.getContext().setAuthentication(new TestingAuthenticationToken(user, null, "USER"));
	}

	@Test
	void requiresMatrixPrincipalNotUserString() throws Exception {
		mvc.perform(post("/data-query/validate").contentType("application/json").content(PLAN))
			.andExpect(status().isUnauthorized())
			.andExpect(jsonPath("$.code").value("UNAUTHENTICATED"));
		SecurityContextHolder.getContext().setAuthentication(new TestingAuthenticationToken("dify-user", null, "USER"));
		mvc.perform(post("/data-query/validate").contentType("application/json").content(PLAN))
			.andExpect(status().isUnauthorized());
		assertThat(executions.get()).isZero();
	}

	@Test
	void rejectsExtraIdentitySqlAndFilterFields() throws Exception {
		login(1L, 2L);
		for (String field : List.of("tenantId", "companyId", "sql", "ruleVersion")) {
			String extra = PLAN.stripTrailing();
			extra = extra.substring(0, extra.length() - 1) + ",\"" + field + "\":\"untrusted\"}";
			mvc.perform(post("/data-query/validate").contentType("application/json").content(extra))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.code").value("INVALID_REQUEST"));
		}
		mvc.perform(post("/data-query/validate").contentType("application/json")
			.content(PLAN.replace("\"filters\":{}", "\"filters\":{\"companyId\":\"untrusted\"}")))
			.andExpect(jsonPath("$.code").value("UNSUPPORTED_FILTERS"));
		assertThat(executions.get()).isZero();
	}

	@Test
	void validatesExecutesAndDoesNotLeakPhysicalKeys() throws Exception {
		login(1L, 2L);
		String validated = mvc.perform(post("/data-query/validate").contentType("application/json").content(PLAN))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.status").value("READY"))
			.andExpect(jsonPath("$.mock").value(false))
			.andReturn()
			.getResponse()
			.getContentAsString();
		assertThat(resolvedIdentity).isEqualTo(new Identity("1", "2"));
		String handle = new ObjectMapper().readTree(validated).get("planHandle").asText();
		String execute = "{\"planHandle\":\"" + handle + "\"}";
		String response = mvc.perform(post("/data-query/execute").contentType("application/json").content(execute))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.status").value("SUCCEEDED"))
			.andExpect(jsonPath("$.rows[0].validPatrolCount").value(12))
			.andExpect(jsonPath("$.rows[0].departmentName").isEmpty())
			.andExpect(jsonPath("$.quality.nameConflictGroups").value(1))
			.andExpect(jsonPath("$.quality.missingNameCount").value(1))
			.andReturn()
			.getResponse()
			.getContentAsString();
		assertThat(response).doesNotContain("physical-company", "physical-dept", "dev_patrol_record", "SELECT ");
		mvc.perform(post("/data-query/execute").contentType("application/json").content(execute))
			.andExpect(jsonPath("$.code").value("INVALID_PLAN_HANDLE"));
		assertThat(executions.get()).isEqualTo(1);
	}

	@Test
	void rejectsMalformedDateAndMockHandle() throws Exception {
		login(1L, 2L);
		mvc.perform(post("/data-query/validate").contentType("application/json")
			.content(PLAN.replace("2026-08-01", "not-a-date"))).andExpect(status().isBadRequest());
		mvc.perform(post("/data-query/execute").contentType("application/json")
			.content("{\"planHandle\":\"mock-only-not-a-valid-executable-handle\"}"))
			.andExpect(jsonPath("$.code").value("INVALID_PLAN_HANDLE"));
	}

	@Test
	void configurationIsOptInAndDefaultsDeny() {
		new ApplicationContextRunner().withUserConfiguration(DataQueryConfiguration.class).run(context -> {
			assertThat(context).doesNotHaveBean(ControlledQueryService.class);
		});
		new ApplicationContextRunner().withUserConfiguration(DataQueryConfiguration.class)
			.withPropertyValues("ai.dataquery.enabled=true")
			.withBean(javax.sql.DataSource.class, () -> mock(javax.sql.DataSource.class))
			.withBean(ObjectMapper.class, ObjectMapper::new)
			.run(context -> {
				assertThat(context).hasSingleBean(ControlledQueryService.class);
				var resolver = context.getBean(AuthorizationResolver.class);
				org.assertj.core.api.Assertions.assertThatThrownBy(() -> resolver.resolve(new Identity("1", "2")))
					.isInstanceOf(DataQueryException.class)
					.hasMessage("DATASET_NOT_AUTHORIZED");
			});
	}

	@Test
	void metricsRemainJsonIntegersWithActualMatrixLongIdSerialization() throws Exception {
		var builder = new org.springframework.http.converter.json.Jackson2ObjectMapperBuilder();
		new com.deepdimen.matrix.common.core.config.JacksonConfiguration().customizer().customize(builder);
		ObjectMapper mapper = builder.build();
		var plan = new PatrolQueryCompiler.Candidate("top.patrol", "valid_patrol_count", "department",
				java.time.LocalDate.of(2026, 8, 1), java.time.LocalDate.of(2026, 9, 1), "Asia/Shanghai", "DESC");
		var result = DataQueryResponses
			.success(new QueryResult("query", "r1", plan, List.of(new PatrolRow("c", "d", null, 2147483648L, 2, 1))));
		var document = mapper.readTree(mapper.writeValueAsString(result));
		assertThat(document.at("/rows/0/validPatrolCount").isIntegralNumber()).isTrue();
		assertThat(document.at("/rows/0/validPatrolCount").asLong()).isEqualTo(2147483648L);
		assertThat(document.at("/rows/0/missingNameCount").isIntegralNumber()).isTrue();
		assertThat(document.at("/quality/missingNameCount").isIntegralNumber()).isTrue();
		assertThat(mapper.readTree(mapper.writeValueAsString(java.util.Map.of("recordId", 2039983535236706305L)))
			.get("recordId")
			.isTextual()).isTrue();
	}

	@Test
	void missingSourceIdsAreCountedAndExposedNotExcludedBySystemPermissions() {
		var candidate = new PatrolQueryCompiler.Candidate("top.patrol", "valid_patrol_count", "department",
				java.time.LocalDate.of(2026, 8, 1), java.time.LocalDate.of(2026, 9, 1), "Asia/Shanghai", "DESC");
		var result = DataQueryResponses.success(new QueryResult("synthetic-query", "synthetic-rule", candidate,
				List.of(new PatrolRow(null, null, null, 3, 0, 3))));
		assertThat(result.get("authorizationModel")).isEqualTo("MATRIX_DATASET_ACL_V1");
		assertThat(result.get("warnings").toString()).contains("DEPARTMENT_ID_MISSING", "COMPANY_ID_MISSING")
			.doesNotContain("EXCLUDED_BY_SCOPE");
		assertThat(result.get("quality").toString()).contains("missingDepartmentIds=3", "missingCompanyIds=3");
		assertThat(result.get("queryDefinition").toString()).contains("scope=authorized_dataset");
	}

}
