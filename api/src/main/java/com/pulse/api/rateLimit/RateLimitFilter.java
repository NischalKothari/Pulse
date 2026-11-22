package com.pulse.api.rateLimit;

import com.pulse.api.exception.ErrorResponse;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.http.HttpStatus;

import java.io.IOException;
import java.time.Instant;
import tools.jackson.databind.ObjectMapper;

/** Security-chain-only filter. Constructed in SecurityConfig to avoid servlet auto-registration. */
public class RateLimitFilter extends OncePerRequestFilter {
    private final RateLimitKeyResolver keyResolver;
    private final TokenBucketStore tokenBucketStore;
    private final ObjectMapper objectMapper;

    public RateLimitFilter(
            RateLimitKeyResolver keyResolver,
            TokenBucketStore tokenBucketStore,
            ObjectMapper objectMapper
    ) {
        this.keyResolver = keyResolver;
        this.tokenBucketStore = tokenBucketStore;
        this.objectMapper = objectMapper;
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain
    ) throws ServletException, IOException {
        var rateLimitRequest = keyResolver.resolve(request);
        if (rateLimitRequest.isEmpty()) {
            filterChain.doFilter(request, response);
            return;
        }

        TokenBucketDecision decision = tokenBucketStore.consume(
                rateLimitRequest.get().key(),
                rateLimitRequest.get().policy()
        );
        if (decision.allowed()) {
            filterChain.doFilter(request, response);
            return;
        }

        writeError(response, HttpStatus.TOO_MANY_REQUESTS.value(),
                "Rate limit exceeded", decision.retryAfterSeconds());
    }

    @Override
    protected boolean shouldNotFilterAsyncDispatch() {
        return true;
    }

    private void writeError(
            HttpServletResponse response,
            int status,
            String message,
            long retryAfterSeconds
    ) throws IOException {
        response.setStatus(status);
        response.setContentType("application/json");
        response.setCharacterEncoding("UTF-8");
        response.setHeader("Retry-After", Long.toString(retryAfterSeconds));
        objectMapper.writeValue(
                response.getOutputStream(),
                new ErrorResponse(status, message, Instant.now())
        );
    }
}
