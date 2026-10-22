package com.deepdimen.matrix.work.controller;

import com.deepdimen.matrix.work.service.InformationReviewModel;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class InformationModelCapabilityControllerTest {

	@SuppressWarnings("unchecked")
	final ObjectProvider<InformationReviewModel> models = mock(ObjectProvider.class);

	final InformationModelCapabilityController controller = new InformationModelCapabilityController(models);

	@Test
	void missingModelDoesNotAccessAdapter() {
		assertEquals("MODEL_REQUIRED", controller.inspect(" ").reason());
		verifyZeroInteractions(models);
	}

	@Test
	void absentAndLegacyAdaptersRemainUnavailable() {
		assertEquals("ADAPTER_MISSING", controller.inspect("fixed").reason());
		InformationReviewModel legacy = (id, system, user) -> {
			throw new AssertionError("must not invoke model");
		};
		when(models.getIfAvailable()).thenReturn(legacy);
		assertEquals("MODEL_UNSUPPORTED", controller.inspect("fixed").reason());
	}

	@Test
	void capabilityDoesNotGenerateText() {
		var model = mock(InformationReviewModel.class);
		when(models.getIfAvailable()).thenReturn(model);
		when(model.supportsManagedModel("fixed")).thenReturn(true);
		assertTrue(controller.inspect("fixed").supported());
		verify(model).supportsManagedModel("fixed");
		verifyNoMoreInteractions(model);
	}

	@Test void adapterFailureDoesNotExposeDetails() {
        when(models.getIfAvailable()).thenThrow(new IllegalStateException("private connection details"));
        var result=controller.inspect("fixed");
        assertFalse(result.supported());
        assertEquals("ADAPTER_UNAVAILABLE",result.reason());
        assertFalse(result.message().contains("private"));
    }

}
