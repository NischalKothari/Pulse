package com.pulse.api.rateLimit;

import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.junit.jupiter.api.Assertions.assertEquals;

class TokenBucketPolicyTest {
    @Test
    void developmentDefaultsMatchApprovedPolicies() {
        RateLimitProperties properties = new RateLimitProperties();

        assertEquals(new TokenBucketPolicy(5, 1, Duration.ofSeconds(12)),
                properties.getLogin().toPolicy());
        assertEquals(new TokenBucketPolicy(3, 1, Duration.ofMinutes(20)),
                properties.getRegistration().toPolicy());
        assertEquals(new TokenBucketPolicy(60, 1, Duration.ofSeconds(1)),
                properties.getAuthenticatedApi().toPolicy());
    }
}
