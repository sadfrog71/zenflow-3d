package com.deepdimen.matrix.discover.controller;

import com.deepdimen.matrix.discover.service.impl.*;
import com.deepdimen.matrix.discover.skill.AgentSkillPackageParser;
import org.junit.jupiter.api.Test;
import org.springframework.web.multipart.MultipartFile;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class ManagedSkillControllerTest {

	@Test
	void oversizedArchiveIsRejectedBeforeReadingBytesOrImporting() throws Exception {
		var packages = mock(ManagedSkillPackageService.class);
		var controller = new ManagedSkillController(packages, mock(ManagedSkillReleaseService.class),
				mock(ManagedSkillBindingService.class), mock(ManagedSkillCatalogService.class));
		var file = mock(MultipartFile.class);
		when(file.getSize()).thenReturn(AgentSkillPackageParser.MAX_ARCHIVE_BYTES + 1);
		assertThrows(IllegalArgumentException.class, () -> controller.importPackage(file));
		verify(file, never()).getBytes();
		verifyZeroInteractions(packages);
	}

}
