package com.pulse.api.rateLimit;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.security.authentication.UsernamePasswordAuthenticationToken.authenticated;
import org.springframework.security.core.context.SecurityContextHolder;

class RateLimitKeyResolverTest {
    private final RateLimitKeyResolver resolver =
            new RateLimitKeyResolver(new RateLimitProperties());

    @Test
    void healthAndSwaggerOpenApiPathsAreExcluded() {
        assertExcluded("/actuator/health");
        assertExcluded("/actuator/health/readiness");
        assertExcluded("/swagger-ui/index.html");
        assertExcluded("/swagger-ui.html");
        assertExcluded("/v3/api-docs");
        assertExcluded("/v3/api-docs/swagger-config");
    }

    @Test
    void optionsRequestsAreExcludedBeforeIdentityResolution() {
        SecurityContextHolder.getContext().setAuthentication(
                authenticated(UUID.randomUUID().toString(), null, java.util.List.of())
        );
        try {
            MockHttpServletRequest request = request("OPTIONS", "/api/v1/services");
            assertTrue(resolver.resolve(request).isEmpty());
        } finally {
            SecurityContextHolder.clearContext();
        }
    }

    @Test
    void loginAndRegistrationHaveDistinctIpScopes() {
        MockHttpServletRequest login = request("POST", "/api/v1/auth/login");
        MockHttpServletRequest registration = request("POST", "/api/v1/users/register");

        var loginBucket = resolver.resolve(login).orElseThrow();
        var registrationBucket = resolver.resolve(registration).orElseThrow();

        assertEquals("login:ip:127.0.0.1", loginBucket.key());
        assertEquals("registration:ip:127.0.0.1", registrationBucket.key());
        assertNotEquals(loginBucket.policy(), registrationBucket.policy());
    }

    private void assertExcluded(String path) {
        assertTrue(resolver.resolve(request("GET", path)).isEmpty(), path);
    }

    private MockHttpServletRequest request(String method, String path) {
        MockHttpServletRequest request = new MockHttpServletRequest(method, path);
        request.setServletPath(path);
        return request;
    }
}
