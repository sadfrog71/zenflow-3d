package com.deepdimen.matrix.discover.skill;

import com.deepdimen.matrix.common.data.tenant.TenantContextHolder;
import com.deepdimen.matrix.discover.mapper.AiSkillRunMapper;
import com.deepdimen.matrix.discover.entity.AiSkillRunEntity;
import com.deepdimen.matrix.skill.api.SkillContract.Consumer;
import java.util.List;
import org.junit.jupiter.api.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.anyString;

class ManagedSkillRunMetadataReaderTest {

	final AiSkillRunMapper runs = mock(AiSkillRunMapper.class);

	final ManagedSkillRunMetadataReader reader = new ManagedSkillRunMetadataReader(runs);

	final Consumer consumer = new Consumer("INFORMATION", "9", "REVIEW", "default");

	@BeforeEach
	void tenant() {
		TenantContextHolder.setTenantId(7L);
	}

	@AfterEach
	void clear() {
		TenantContextHolder.clear();
	}

	AiSkillRunEntity row() {
		var r = new AiSkillRunEntity();
		r.setId(100L);
		r.setReleaseId(101L);
		r.setBindingRevisionId(102L);
		r.setTenantId("7");
		r.setActorId("information-collector");
		r.setSubjectId("information-collector");
		r.setConsumerType("INFORMATION");
		r.setConsumerId("9");
		r.setSlotCode("REVIEW");
		r.setBindingKey("default");
		r.setBusinessRunId("10");
		r.setStatus("UNKNOWN");
		r.setErrorCode("EXECUTION_UNKNOWN");
		return r;
	}

	void rows(List<AiSkillRunEntity> values){when(runs.findBusiness(anyString(),anyString(),anyString(),anyString(),anyString(),anyString(),anyString())).thenReturn(values);}

	@Test
	void tenantMismatchCannotQuery() {
		assertThrows(IllegalArgumentException.class, () -> reader.find("8", "information-collector", consumer, "10"));
		verifyZeroInteractions(runs);
	}

	@Test
	void noIndexIsNotSuccess() {
		rows(List.of());
		assertNull(reader.find("7", "information-collector", consumer, "10"));
	}

	@Test
	void unknownExecutionRetainsActualVersionAndError() {
		rows(List.of(row()));
		var value = reader.find("7", "information-collector", consumer, "10");
		assertEquals("UNKNOWN", value.status());
		assertEquals("101", value.releaseId());
		assertEquals("102", value.bindingRevisionId());
		assertEquals("EXECUTION_UNKNOWN", value.errorCode());
	}

	@Test
	void wrongBusinessAndAmbiguousAssociationsRejected() {
		var wrong = row();
		wrong.setBusinessRunId("11");
		rows(List.of(wrong));
		assertThrows(IllegalArgumentException.class, () -> reader.find("7", "information-collector", consumer, "10"));
		rows(List.of(row(), row()));
		assertThrows(IllegalStateException.class, () -> reader.find("7", "information-collector", consumer, "10"));
	}

}
