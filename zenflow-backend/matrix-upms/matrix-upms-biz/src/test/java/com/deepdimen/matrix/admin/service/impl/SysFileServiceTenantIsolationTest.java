package com.deepdimen.matrix.admin.service.impl;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.lang.reflect.Method;
import java.time.LocalDateTime;
import java.util.List;

import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.apache.ibatis.session.Configuration;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.test.util.ReflectionTestUtils;

import com.amazonaws.services.s3.model.AmazonS3Exception;
import com.baomidou.mybatisplus.core.conditions.AbstractWrapper;
import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.deepdimen.matrix.admin.api.entity.SysFile;
import com.deepdimen.matrix.admin.api.entity.SysTenant;
import com.deepdimen.matrix.admin.controller.SysFileController;
import com.deepdimen.matrix.admin.mapper.SysFileGroupMapper;
import com.deepdimen.matrix.admin.mapper.SysFileMapper;
import com.deepdimen.matrix.admin.mapper.SysTenantMapper;
import com.deepdimen.matrix.common.data.tenant.TenantContextHolder;
import com.deepdimen.matrix.common.file.core.FileProperties;
import com.deepdimen.matrix.common.file.core.FileTemplate;
import com.deepdimen.matrix.common.security.annotation.Inner;

import jakarta.servlet.http.HttpServletResponse;

class SysFileServiceTenantIsolationTest {

	private static final Long TENANT_ID = 2052188107445968897L;

	private final FileTemplate fileTemplate = mock(FileTemplate.class);

	private final SysFileGroupMapper fileGroupMapper = mock(SysFileGroupMapper.class);

	private final FileProperties properties = mock(FileProperties.class);

	private final SysTenantMapper tenantMapper = mock(SysTenantMapper.class);

	private final SysFileMapper fileMapper = mock(SysFileMapper.class);

	private SysFileServiceImpl service;

	@BeforeEach
	void setUp() {
		if (TableInfoHelper.getTableInfo(SysFile.class) == null) {
			TableInfoHelper.initTableInfo(new MapperBuilderAssistant(new Configuration(), "sys-file-test"),
					SysFile.class);
		}
		service = new SysFileServiceImpl(fileTemplate, fileGroupMapper, properties, tenantMapper);
		ReflectionTestUtils.setField(service, "baseMapper", fileMapper);
		service.setSigningSecret("0123456789abcdef0123456789abcdef");
		TenantContextHolder.setTenantId(TENANT_ID);
	}

	@AfterEach
	void tearDown() {
		TenantContextHolder.clear();
	}

	@Test
	void taskFilesCannotBypassBusinessAuthorizationWithGenericLinks() throws Exception {
		SysFile file = new SysFile();
		file.setType("WORK_TASK");
		file.setFileName("task.pdf");
		when(fileMapper.selectOne(any(), eq(false))).thenReturn(file);
		HttpServletResponse response = mock(HttpServletResponse.class);
		service.getFile("task.pdf", true, response);
		verify(response).sendError(403, "请从任务详情读取附件");
		org.junit.jupiter.api.Assertions.assertThrows(org.springframework.web.server.ResponseStatusException.class,
				() -> service.createSignedUrl("task.pdf", true));
	}

	@Test
	void taskEvidenceCannotBeDeletedFromGenericFileManagement() {
		SysFile file = new SysFile();
		file.setId(1L);
		file.setType("WORK_TASK");
		when(fileMapper.selectById(1L)).thenReturn(file);
		org.junit.jupiter.api.Assertions.assertThrows(org.springframework.web.server.ResponseStatusException.class,
				() -> service.deleteFile(1L));
	}

	@Test
	void privateDownloadUsesCurrentTenantAndDoesNotSkipIsolation() throws Exception {
		HttpServletResponse response = mock(HttpServletResponse.class);
		when(fileMapper.selectOne(any(), eq(false))).thenReturn(null);

		service.getFile("private.pdf", true, response);

		@SuppressWarnings("unchecked")
		ArgumentCaptor<Wrapper<SysFile>> wrapperCaptor = ArgumentCaptor.forClass(Wrapper.class);
		verify(fileMapper).selectOne(wrapperCaptor.capture(), eq(false));
		AbstractWrapper<?, ?, ?> wrapper = (AbstractWrapper<?, ?, ?>) wrapperCaptor.getValue();
		wrapper.getSqlSegment();
		assertTrue(wrapper.getParamNameValuePairs().containsValue(TENANT_ID));
		assertTrue(wrapper.getParamNameValuePairs().containsValue("private.pdf"));
		assertFalse(TenantContextHolder.getTenantSkip());
		verify(response).sendError(404, "文件不存在");
	}

	@Test
	void publicBrandEndpointRejectsUnreferencedFile() throws Exception {
		HttpServletResponse response = mock(HttpServletResponse.class);
		SysTenant tenant = activeTenant();
		tenant.setLogo("/admin/sys-file/oss/file?fileName=allowed.png");
		when(tenantMapper.selectList(any())).thenReturn(List.of(tenant));

		service.getPublicBrandFile("private.pdf", response);

		verify(response).sendError(404, "文件不存在");
		verify(fileMapper, never()).selectOne(any(), eq(false));
	}

	@Test
	void publicBrandEndpointReadsOnlyFromReferencingTenant() throws Exception {
		HttpServletResponse response = mock(HttpServletResponse.class);
		SysTenant tenant = activeTenant();
		tenant.setLogo("/admin/sys-file/oss/file?fileName=allowed.png");
		when(tenantMapper.selectList(any())).thenReturn(List.of(tenant));
		when(fileMapper.selectOne(any(), eq(false))).thenReturn(null);

		service.getPublicBrandFile("allowed.png", response);

		@SuppressWarnings("unchecked")
		ArgumentCaptor<Wrapper<SysFile>> wrapperCaptor = ArgumentCaptor.forClass(Wrapper.class);
		verify(fileMapper).selectOne(wrapperCaptor.capture(), eq(false));
		AbstractWrapper<?, ?, ?> wrapper = (AbstractWrapper<?, ?, ?>) wrapperCaptor.getValue();
		wrapper.getSqlSegment();
		assertTrue(wrapper.getParamNameValuePairs().containsValue(TENANT_ID));
		assertTrue(wrapper.getParamNameValuePairs().containsValue("allowed.png"));
		verify(response).sendError(404, "文件不存在");
	}

	@Test
	void missingBrandObjectReturnsNotFoundInsteadOfStorageUnavailable() throws Exception {
		HttpServletResponse response = mock(HttpServletResponse.class);
		SysTenant tenant = activeTenant();
		tenant.setLogo("/admin/sys-file/oss/file?fileName=missing.png");
		SysFile file = new SysFile();
		file.setFileName("missing.png");
		file.setOriginal("missing.png");
		file.setBucketName("matrix");
		when(tenantMapper.selectList(any())).thenReturn(List.of(tenant));
		when(fileMapper.selectOne(any(), eq(false))).thenReturn(file);
		AmazonS3Exception missing = new AmazonS3Exception("NoSuchKey");
		missing.setStatusCode(404);
		when(fileTemplate.getObject("matrix", null, "missing.png")).thenThrow(missing);

		service.getPublicBrandFile("missing.png", response);

		verify(response).sendError(404, "文件不存在");
	}

	@Test
	void signedUrlIsTenantBoundAndTamperingIsRejected() throws Exception {
		SysFile file = new SysFile();
		file.setFileName("private.pdf");
		file.setTenantId(TENANT_ID);
		when(fileMapper.selectOne(any(), eq(false))).thenReturn(file);

		String url = service.createSignedUrl("private.pdf", true);

		assertTrue(url.contains("tenantId=" + TENANT_ID));
		assertTrue(url.contains("inline=true"));
		assertTrue(url.contains("signature="));

		HttpServletResponse response = mock(HttpServletResponse.class);
		service.getSignedFile("private.pdf", TENANT_ID, Long.MAX_VALUE, true, "tampered", response);
		verify(response).sendError(404, "文件不存在");
		verify(tenantMapper, never()).selectCount(any());
	}

	@Test
	void onlyBrandAndSignedFileEndpointsArePublic() throws Exception {
		Method privateFile = SysFileController.class.getMethod("file", String.class, Boolean.class,
				HttpServletResponse.class);
		Method publicBrand = SysFileController.class.getMethod("publicBrandFile", String.class,
				HttpServletResponse.class);
		Method signedFile = SysFileController.class.getMethod("signedFile", String.class, Long.class, long.class,
				Boolean.class, String.class, HttpServletResponse.class);

		assertFalse(privateFile.isAnnotationPresent(Inner.class));
		assertTrue(publicBrand.isAnnotationPresent(Inner.class));
		assertTrue(signedFile.isAnnotationPresent(Inner.class));
	}

	private SysTenant activeTenant() {
		SysTenant tenant = new SysTenant();
		tenant.setId(TENANT_ID);
		tenant.setStatus("0");
		tenant.setDelFlag("0");
		tenant.setStartTime(LocalDateTime.now().minusDays(1));
		tenant.setEndTime(LocalDateTime.now().plusDays(1));
		return tenant;
	}

}
