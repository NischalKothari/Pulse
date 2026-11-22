package com.pulse.api.rateLimit;

import com.pulse.api.exception.ErrorResponse;
import jakarta.servlet.FilterChain;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import tools.jackson.databind.ObjectMapper;

import java.time.Duration;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.mockito.ArgumentCaptor;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class RateLimitFilterTest {
    private static final TokenBucketPolicy POLICY =
            new TokenBucketPolicy(5, 1, Duration.ofSeconds(12));

    private RateLimitKeyResolver keyResolver;
    private TokenBucketStore tokenBucketStore;
    private ObjectMapper objectMapper;
    private RateLimitFilter filter;

    @BeforeEach
    void setUp() {
        keyResolver = mock(RateLimitKeyResolver.class);
        tokenBucketStore = mock(TokenBucketStore.class);
        objectMapper = mock(ObjectMapper.class);
        filter = new RateLimitFilter(keyResolver, tokenBucketStore, objectMapper);
    }

    @Test
    void allowedRequestConsumesTokenAndContinuesChainExactlyOnce() throws Exception {
        var rateLimitRequest = new RateLimitKeyResolver.RateLimitRequest("login:ip:127.0.0.1", POLICY);
        when(keyResolver.resolve(any())).thenReturn(Optional.of(rateLimitRequest));
        when(tokenBucketStore.consume(rateLimitRequest.key(), POLICY))
                .thenReturn(TokenBucketDecision.allow());
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/v1/auth/login");
        MockHttpServletResponse response = new MockHttpServletResponse();
        AtomicInteger chainCalls = new AtomicInteger();
        FilterChain chain = (req, res) -> chainCalls.incrementAndGet();

        filter.doFilter(request, response, chain);

        assertEquals(1, chainCalls.get());
        verify(tokenBucketStore).consume(rateLimitRequest.key(), POLICY);
        assertEquals(200, response.getStatus());
        verifyNoInteractions(objectMapper);
    }

    @Test
    void unresolvedRequestPassesThroughWithoutAccessingTokenStore() throws Exception {
        when(keyResolver.resolve(any())).thenReturn(Optional.empty());
        AtomicInteger chainCalls = new AtomicInteger();

        filter.doFilter(
                new MockHttpServletRequest("OPTIONS", "/api/v1/services"),
                new MockHttpServletResponse(),
                (request, response) -> chainCalls.incrementAndGet()
        );

        assertEquals(1, chainCalls.get());
        verifyNoInteractions(tokenBucketStore, objectMapper);
    }

    @Test
    void deniedRequestReturns429RetryAfterAndErrorResponseWithoutContinuingChain() throws Exception {
        var rateLimitRequest = new RateLimitKeyResolver.RateLimitRequest("registration:ip:127.0.0.1", POLICY);
        when(keyResolver.resolve(any())).thenReturn(Optional.of(rateLimitRequest));
        when(tokenBucketStore.consume(rateLimitRequest.key(), POLICY))
                .thenReturn(TokenBucketDecision.limit(42));
        doNothing().when(objectMapper).writeValue(any(java.io.OutputStream.class), any());
        MockHttpServletResponse response = new MockHttpServletResponse();
        AtomicInteger chainCalls = new AtomicInteger();

        filter.doFilter(
                new MockHttpServletRequest("POST", "/api/v1/users/register"),
                response,
                (request, servletResponse) -> chainCalls.incrementAndGet()
        );

        assertEquals(429, response.getStatus());
        assertEquals("42", response.getHeader("Retry-After"));
        assertTrue(response.getContentType().startsWith("application/json"));
        assertEquals("UTF-8", response.getCharacterEncoding());
        assertEquals(0, chainCalls.get());
        ArgumentCaptor<ErrorResponse> errorCaptor = ArgumentCaptor.forClass(ErrorResponse.class);
        verify(objectMapper).writeValue(eq(response.getOutputStream()), errorCaptor.capture());
        assertEquals(429, errorCaptor.getValue().status());
        assertEquals("Rate limit exceeded", errorCaptor.getValue().message());
        assertNotNull(errorCaptor.getValue().timestamp());
    }

    @Test
    void nestedDispatchOfSameRequestDoesNotConsumeTokenTwice() throws Exception {
        var rateLimitRequest = new RateLimitKeyResolver.RateLimitRequest("api:user:123", POLICY);
        when(keyResolver.resolve(any())).thenReturn(Optional.of(rateLimitRequest));
        when(tokenBucketStore.consume(rateLimitRequest.key(), POLICY))
                .thenReturn(TokenBucketDecision.allow());
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/services");
        MockHttpServletResponse response = new MockHttpServletResponse();
        AtomicInteger chainCalls = new AtomicInteger();
        FilterChain[] chain = new FilterChain[1];
        chain[0] = (req, res) -> {
            if (chainCalls.incrementAndGet() == 1) {
                filter.doFilter(req, res, chain[0]);
            }
        };

        filter.doFilter(request, response, chain[0]);

        assertEquals(2, chainCalls.get());
        verify(tokenBucketStore, times(1)).consume(rateLimitRequest.key(), POLICY);
    }
}
