package com.deepdimen.matrix.zenflow.identity;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import java.io.IOException;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.security.core.context.SecurityContext;
import com.deepdimen.matrix.common.security.service.MatrixUser;

@org.springframework.core.annotation.Order(org.springframework.core.Ordered.HIGHEST_PRECEDENCE + 1)
@Component
public class ZenflowLoginCsrfFilter extends OncePerRequestFilter {
    private final ZenflowProperties properties;
    private final ZenflowUserDetailsService identities;
    private final com.deepdimen.matrix.zenflow.admin.ZenflowAdminUserDetailsService administrators;
    @org.springframework.beans.factory.annotation.Autowired
    public ZenflowLoginCsrfFilter(ZenflowProperties properties, ZenflowUserDetailsService identities,
            com.deepdimen.matrix.zenflow.admin.ZenflowAdminUserDetailsService administrators) {
        this.properties = properties; this.identities = identities; this.administrators=administrators;
    }
    ZenflowLoginCsrfFilter(ZenflowProperties properties, ZenflowUserDetailsService identities) {
        this(properties,identities,null);
    }

    private boolean allowedClient(String client) { return properties.getOauthClientId().equals(client) || "zenflow-admin".equals(client); }

    @Override protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
            FilterChain chain) throws ServletException, IOException {
        if (request.getRequestURI().endsWith("/oauth2/authorize")) {
            if (!allowedClient(request.getParameter("client_id"))) {
                response.sendError(404);
                return;
            }
            if ("login".equals(request.getParameter("prompt"))) {
                String state = request.getParameter("state");
                if (state == null || state.isBlank()) {
                    response.sendError(400);
                    return;
                }
                HttpSession current = request.getSession(false);
                if (current == null || !state.equals(current.getAttribute("ZENFLOW_LOGIN_STATE"))) {
                    if (current != null) current.invalidate();
                    request.getSession(true).setAttribute("ZENFLOW_LOGIN_STATE", state);
                }
            }
            HttpSession session = request.getSession(false);
            Object saved = session == null ? null : session.getAttribute("SPRING_SECURITY_CONTEXT");
            if (saved instanceof SecurityContext context && context.getAuthentication() != null
                    && context.getAuthentication().getPrincipal() instanceof MatrixUser user) {
                try {
                    if("zenflow-admin".equals(request.getParameter("client_id"))) {
                        if(administrators==null)throw new IllegalStateException("ADMIN_IDENTITY_REQUIRED");
                        var current=administrators.loadUserByUser(user);
                        if(!current.isEnabled() || !current.isAccountNonLocked())throw new IllegalStateException("ADMIN_IDENTITY_REVOKED");
                    } else {
                        var current=identities.loadUserByUser(user);
                        if(!current.isEnabled() || !current.isAccountNonLocked())throw new IllegalStateException("CONSUMER_IDENTITY_REVOKED");
                    }
                }
                catch (RuntimeException ex) { session.invalidate(); }
            }
        }
        if ("POST".equals(request.getMethod()) && request.getRequestURI().endsWith("/oauth2/form")) {
            if (!allowedClient(request.getParameter("client_id"))) {
                response.sendError(404);
                return;
            }
            HttpSession session = request.getSession(false);
            String origin = properties.getPublicBaseUrl().getScheme() + "://" + properties.getPublicBaseUrl().getAuthority();
            String csrf = request.getParameter("zenflow_csrf");
            if (session == null || !origin.equals(request.getHeader("Origin"))
                    || csrf == null || !csrf.equals(session.getAttribute("ZENFLOW_CSRF"))) {
                response.sendError(403);
                return;
            }
        }
        chain.doFilter(request, response);
    }
}
