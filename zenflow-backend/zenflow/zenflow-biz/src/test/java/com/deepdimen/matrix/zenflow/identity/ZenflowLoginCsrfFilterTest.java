package com.deepdimen.matrix.zenflow.identity;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.Mockito.mock;

import java.net.URI;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.mock.web.MockHttpSession;

class ZenflowLoginCsrfFilterTest {
    private final ZenflowProperties properties = new ZenflowProperties();

    @Test void rejectsOtherClientsAndFormPostsWithoutProductCsrf() throws Exception {
        properties.setOauthClientId("zenflow-ios");
        properties.setPublicBaseUrl(URI.create("https://zenflow.example"));
        var filter = new ZenflowLoginCsrfFilter(properties, mock(ZenflowUserDetailsService.class));

        var foreignAuthorize = new MockHttpServletRequest("GET", "/admin/oauth2/authorize");
        foreignAuthorize.addParameter("client_id", "old-client");
        var foreignResponse = new MockHttpServletResponse();
        filter.doFilter(foreignAuthorize, foreignResponse, new MockFilterChain());
        assertEquals(404, foreignResponse.getStatus());

        var foreignForm = new MockHttpServletRequest("POST", "/admin/oauth2/form");
        foreignForm.addParameter("client_id", "old-client");
        var formResponse = new MockHttpServletResponse();
        filter.doFilter(foreignForm, formResponse, new MockFilterChain());
        assertEquals(404, formResponse.getStatus());

        var missingCsrf = new MockHttpServletRequest("POST", "/admin/oauth2/form");
        missingCsrf.addParameter("client_id", "zenflow-ios");
        var csrfResponse = new MockHttpServletResponse();
        filter.doFilter(missingCsrf, csrfResponse, new MockFilterChain());
        assertEquals(403, csrfResponse.getStatus());

        var previous = new MockHttpSession();
        var freshLogin = new MockHttpServletRequest("GET", "/admin/oauth2/authorize");
        freshLogin.setSession(previous);
        freshLogin.addParameter("client_id", "zenflow-ios");
        freshLogin.addParameter("state", "new-random-state");
        freshLogin.addParameter("prompt", "login");
        filter.doFilter(freshLogin, new MockHttpServletResponse(), new MockFilterChain());
        assertNotSame(previous, freshLogin.getSession(false));
        assertEquals("new-random-state", freshLogin.getSession(false).getAttribute("ZENFLOW_LOGIN_STATE"));

        var resumedLogin = new MockHttpServletRequest("GET", "/admin/oauth2/authorize");
        resumedLogin.setSession(freshLogin.getSession(false));
        resumedLogin.addParameter("client_id", "zenflow-ios");
        resumedLogin.addParameter("state", "new-random-state");
        resumedLogin.addParameter("prompt", "login");
        filter.doFilter(resumedLogin, new MockHttpServletResponse(), new MockFilterChain());
        assertSame(freshLogin.getSession(false), resumedLogin.getSession(false));
    }
    @Test void administratorAuthorizationResumesItsOwnIdentityAndRejectsCrossClientReuse() throws Exception {
        properties.setOauthClientId("zenflow-ios");
        properties.setPublicBaseUrl(URI.create("https://invelle.example"));
        var consumers=mock(ZenflowUserDetailsService.class);
        var admins=mock(com.deepdimen.matrix.zenflow.admin.ZenflowAdminUserDetailsService.class);
        var user=mock(com.deepdimen.matrix.common.security.service.MatrixUser.class);
        org.mockito.Mockito.when(user.isEnabled()).thenReturn(true);
        org.mockito.Mockito.when(user.isAccountNonLocked()).thenReturn(true);
        org.mockito.Mockito.when(admins.loadUserByUser(user)).thenReturn(user);
        var filter=new ZenflowLoginCsrfFilter(properties,consumers,admins);
        var context=org.springframework.security.core.context.SecurityContextHolder.createEmptyContext();
        context.setAuthentication(new org.springframework.security.authentication.UsernamePasswordAuthenticationToken(user,null,java.util.List.of()));
        var session=new MockHttpSession();session.setAttribute("SPRING_SECURITY_CONTEXT",context);
        var request=new MockHttpServletRequest("GET","/admin/oauth2/authorize");
        request.setSession(session);request.addParameter("client_id","zenflow-admin");
        filter.doFilter(request,new MockHttpServletResponse(),new MockFilterChain());
        assertSame(session,request.getSession(false));
        org.mockito.Mockito.verify(admins).loadUserByUser(user);
        org.mockito.Mockito.verifyNoMoreInteractions(consumers);

        org.mockito.Mockito.when(consumers.loadUserByUser(user)).thenThrow(new org.springframework.security.core.userdetails.UsernameNotFoundException("wrong subject"));
        var cross=new MockHttpServletRequest("GET","/admin/oauth2/authorize");
        cross.setSession(session);cross.addParameter("client_id","zenflow-ios");
        filter.doFilter(cross,new MockHttpServletResponse(),new MockFilterChain());
        org.junit.jupiter.api.Assertions.assertTrue(session.isInvalid());
    }

}
