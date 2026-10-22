package com.deepdimen.matrix.zenflow.identity;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.Set;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/** Keeps inherited administration endpoints out of the product server. */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class ZenflowEndpointBoundaryFilter extends OncePerRequestFilter {

	private static final Set<String> PRODUCT_PATHS = Set.of("/zenflow/v1/bootstrap", "/zenflow/v1/profile",
			"/zenflow/v1/me", "/zenflow/v1/activity", "/zenflow/v1/me/preferences", "/zenflow/v1/auth/policy",
			"/zenflow/v1/auth/external/challenge", "/zenflow/v1/auth/external/complete",
			"/zenflow/v1/account/login-methods", "/zenflow/v1/auth/email/challenges",
			"/zenflow/v1/auth/email/verifications", "/zenflow/v1/auth/registrations",
			"/zenflow/v1/auth/password-resets", "/zenflow/v1/auth/logout");

	private static final Set<String> AUTH_PATHS = Set.of("/token/login", "/oauth2/authorize", "/oauth2/form",
			"/oauth2/token", "/oauth2/jwks", "/.well-known/oauth-authorization-server",
			"/.well-known/openid-configuration");

	@Override
	protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
			throws ServletException, IOException {
		String path = request.getServletPath();
		boolean adminPath = path.equals("/zenflow-admin/v1/users")
				|| path.matches("/zenflow-admin/v1/users/[0-9]+/state") || path.equals("/zenflow-admin/v1/me")
				|| path.equals("/zenflow-admin/v1/models") || path.equals("/zenflow-admin/v1/logout")
				|| path.equals("/zenflow-admin/v1/account/password") || path.equals("/zenflow-admin/v1/home-slogans")
				|| path.equals("/zenflow-admin/v1/operations")
				|| path.matches("/zenflow-admin/v1/models/[0-9]+(/activate|/test)?")
				|| path.equals("/zenflow-admin/v1/embedding-models")
				|| path.matches("/zenflow-admin/v1/embedding-models/[0-9]+(/activate|/test)?")
				|| path.equals("/zenflow-admin/v1/references/extract")
                || path.matches("/zenflow-admin/v1/references/[0-9]+/chunks(/[0-9]+)?")
                || path.equals("/zenflow-admin/v1/references")
				|| path.matches("/zenflow-admin/v1/references/[0-9]+(/revoke)?")
				|| path.equals("/zenflow-admin/v1/companion/previews")
				|| path.matches("/zenflow-admin/v1/companion/previews/[0-9]+(/events|/cancel)?")
				|| path.equals("/zenflow-admin/v1/companion")
				|| path.matches("/zenflow-admin/v1/companion/(drafts|disable)")
				|| path.matches("/zenflow-admin/v1/companion/revisions/[0-9]+(/prepare|/publish|/rollback|/preview)?")
				|| path.equals("/zenflow-admin/v1/books")
				|| path.equals("/zenflow-admin/v1/books/import")
				|| path.matches("/zenflow-admin/v1/books/[0-9]+")
				|| path.matches("/zenflow-admin/v1/books/[0-9]+/(chapters|source-review|revoke|translations)")
				|| path.matches("/zenflow-admin/v1/books/[0-9]+/translations/import")
				|| path.matches("/zenflow-admin/v1/books/[0-9]+/translations/[0-9]+")
				|| path.matches("/zenflow-admin/v1/books/[0-9]+/translations/[0-9]+/asset")
				|| path.matches("/zenflow-admin/v1/books/[0-9]+/translations/[0-9]+/(retry|qa|publish|unpublish)")
				|| path.matches("/zenflow-admin/v1/books/[0-9]+/translations/[0-9]+/chapters/[0-9]+(/review)?");
		boolean communityPath = path.matches("/zenflow/v1/community/(posts(/[0-9]+(/withdraw)?)?|attachments(/[0-9]+)?)")
                || path.matches("/zenflow-admin/v1/community/(posts(/[0-9]+/review)?|attachments/[0-9]+)");
        boolean notePath = path.equals("/zenflow/v1/notes")
				|| path.matches("/zenflow/v1/notes/[0-9]+(/state|/versions|/consents(/[0-9]+)?)?");
		boolean memoryPath = path.equals("/zenflow/v1/memories") || path.matches("/zenflow/v1/memories/[0-9]+");
		boolean gardenPath = path.equals("/zenflow/v1/garden/state") || path.matches("/zenflow/v1/operations/[A-Za-z0-9_-]{8,128}");
        String mediaId="[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}";
        boolean mediaPath=path.equals("/zenflow/v1/media/uploads")||path.matches("/zenflow/v1/media/uploads/"+mediaId+"/(content|complete)")||path.matches("/zenflow/v1/media/"+mediaId+"/(status|content)");
		boolean voicePath = path.equals("/zenflow/v1/voice/corrections");
		boolean bookPath = path.equals("/zenflow/v1/books")
				|| path.equals("/zenflow/v1/books/state")
				|| path.matches("/zenflow/v1/books/[0-9]+/versions/[0-9]+");
		boolean conversationPath = path.equals("/zenflow/v1/conversations") || path
			.matches("/zenflow/v1/conversations/[A-Za-z0-9_-]{1,64}(/messages(/[A-Za-z0-9_-]{1,64}/citations)?)?");
		boolean runPath = path
			.matches("/zenflow/v1/runs/[A-Za-z0-9_-]{1,64}(/events|/cancel|/citations(/[A-Za-z0-9_-]{1,128})?)?");
		if (communityPath || adminPath || notePath || memoryPath || voicePath || gardenPath || mediaPath || bookPath || conversationPath || runPath
				|| ("DELETE".equals(request.getMethod())
						&& path.matches("/zenflow/v1/account/login-methods/(google|facebook|apple)"))
				|| PRODUCT_PATHS.contains(path) || AUTH_PATHS.contains(path) || "/error".equals(path)) {
			chain.doFilter(request, response);
			return;
		}
		response.sendError(HttpServletResponse.SC_NOT_FOUND);
	}

}
