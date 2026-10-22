package com.deepdimen.matrix.auth.support.filter;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import java.util.concurrent.atomic.AtomicBoolean;
import static org.junit.jupiter.api.Assertions.assertTrue;
class ZenflowPasswordDecoderFilterTest {
    @Test void publicPkceGrantNeedsNoLegacyPasswordEncryptionKey() throws Exception {
        var request=new MockHttpServletRequest("POST","/admin/oauth2/token");
        request.setParameter("grant_type","authorization_code");request.setParameter("client_id","zenflow-ios");
        request.setParameter("code","fixture-code");request.setParameter("code_verifier","fixture-verifier");
        var passed=new AtomicBoolean();
        new PasswordDecoderFilter(new AuthSecurityConfigProperties()).doFilter(request,new MockHttpServletResponse(),(a,b)->passed.set(true));
        assertTrue(passed.get());
    }
}
