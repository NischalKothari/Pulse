package com.pulse.api.rateLimit;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

import java.util.Optional;
import java.util.UUID;

@Component
public class RateLimitKeyResolver {
    private static final String LOGIN_PATH = "/api/v1/auth/login";
    private static final String REGISTRATION_PATH = "/api/v1/users/register";

    private final RateLimitProperties properties;

    public RateLimitKeyResolver(RateLimitProperties properties) {
        this.properties = properties;
    }

    public Optional<RateLimitRequest> resolve(HttpServletRequest request) {
        String path = request.getRequestURI();
        String contextPath = request.getContextPath();
        if (contextPath != null && !contextPath.isEmpty() && path.startsWith(contextPath)) {
            path = path.substring(contextPath.length());
        }
        if ("OPTIONS".equalsIgnoreCase(request.getMethod()) || isExcludedPath(path)) {
            return Optional.empty();
        }

        if ("POST".equalsIgnoreCase(request.getMethod()) && LOGIN_PATH.equals(path)) {
            return Optional.of(new RateLimitRequest(
                    "login:ip:" + request.getRemoteAddr(),
                    properties.getLogin().toPolicy()
            ));
        }

        if ("POST".equalsIgnoreCase(request.getMethod()) && REGISTRATION_PATH.equals(path)) {
            return Optional.of(new RateLimitRequest(
                    "registration:ip:" + request.getRemoteAddr(),
                    properties.getRegistration().toPolicy()
            ));
        }

        if (!path.startsWith("/api/")) {
            return Optional.empty();
        }

        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication != null
                && authentication.isAuthenticated()
                && authentication.getPrincipal() instanceof UUID userId) {
            return Optional.of(new RateLimitRequest(
                    "authenticated-api:user:" + userId,
                    properties.getAuthenticatedApi().toPolicy()
            ));
        }

        return Optional.empty();
    }

    private boolean isExcludedPath(String path) {
        return path.equals("/swagger-ui.html")
                || path.equals("/swagger-ui")
                || path.startsWith("/swagger-ui/")
                || path.equals("/v3/api-docs")
                || path.startsWith("/v3/api-docs/")
                || path.equals("/actuator/health")
                || path.startsWith("/actuator/health/");
    }

    public record RateLimitRequest(String key, TokenBucketPolicy policy) { }
}
