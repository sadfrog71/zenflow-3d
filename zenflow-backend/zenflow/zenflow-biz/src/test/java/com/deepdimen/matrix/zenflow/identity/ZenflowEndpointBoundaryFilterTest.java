package com.deepdimen.matrix.zenflow.identity;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

class ZenflowEndpointBoundaryFilterTest {

	private final ZenflowEndpointBoundaryFilter filter = new ZenflowEndpointBoundaryFilter();

	@Test
	void allowsProductAndAuthorizationPathsOnly() throws Exception {
		for (String path : new String[] { "/zenflow/v1/community/posts", "/zenflow/v1/community/posts/123/withdraw",
                "/zenflow/v1/community/attachments/123", "/zenflow-admin/v1/community/posts",
                "/zenflow-admin/v1/community/posts/123/review", "/zenflow-admin/v1/community/attachments/123",
                "/zenflow/v1/media/uploads", "/zenflow/v1/media/uploads/01234567-89ab-cdef-0123-456789abcdef/complete", "/zenflow/v1/media/01234567-89ab-cdef-0123-456789abcdef/content", "/zenflow/v1/garden/state", "/zenflow/v1/operations/request_123", "/zenflow/v1/bootstrap", "/zenflow/v1/profile", "/oauth2/authorize",
				"/zenflow/v1/voice/corrections",
				"/oauth2/token", "/token/login", "/zenflow-admin/v1/references", "/zenflow-admin/v1/companion/previews",
				"/zenflow-admin/v1/companion/previews/123", "/zenflow-admin/v1/companion/previews/123/events",
				"/zenflow-admin/v1/companion/previews/123/cancel", "/zenflow-admin/v1/references/123/revoke",
				"/zenflow-admin/v1/models", "/zenflow-admin/v1/users", "/zenflow-admin/v1/users/123/state",
				"/zenflow-admin/v1/embedding-models", "/zenflow-admin/v1/embedding-models/123/test",
				"/zenflow-admin/v1/embedding-models/123/activate", "/zenflow-admin/v1/logout",
				"/zenflow/v1/runs/run_1/citations", "/zenflow/v1/runs/run_1/citations/PRIVATE_abc" }) {
			var request = new MockHttpServletRequest("GET", path);
			request.setServletPath(path);
			var response = new MockHttpServletResponse();
			var chain = new MockFilterChain();
			filter.doFilter(request, response, chain);
			assertTrue(chain.getRequest() != null, path);
		}
		for (String path : new String[] { "/zenflow/v1/community/posts/123/private", "/zenflow/v1/voice/debug", "/zenflow-admin/v1/community/posts/foreign/review",
                "/zenflow/v1/media/other/content", "/zenflow/v1/media/uploads/debug", "/zenflow/v1/media/01234567-89ab-cdef-0123-456789abcdef/storage-key", "/zenflow/v1/garden/42", "/zenflow/v1/operations/short", "/zenflow/v1/garden/state/private", "/admin/user/list", "/token/password", "/zenflow/v1/debug",
				"/zenflow-admin/v1/user/list", "/zenflow-admin/v1/users/foreign/state",
				"/zenflow-admin/v1/users/123/records", "/zenflow-admin/v1/embedding-models/123/secret",
				"/zenflow-admin/v1/embedding-models/foreign/test", "/zenflow-admin/v1/companion/previews/foreign",
				"/zenflow-admin/v1/companion/previews/123/history" }) {
			var request = new MockHttpServletRequest("GET", path);
			request.setServletPath(path);
			var response = new MockHttpServletResponse();
			var chain = new MockFilterChain();
			filter.doFilter(request, response, chain);
			assertEquals(404, response.getStatus(), path);
			assertTrue(chain.getRequest() == null, path);
		}
	}

}
