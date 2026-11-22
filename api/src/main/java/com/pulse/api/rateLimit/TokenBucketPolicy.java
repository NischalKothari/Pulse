package com.pulse.api.rateLimit;

import java.time.Duration;

public record TokenBucketPolicy(
        int capacity,
        int refillTokens,
        Duration refillPeriod
) {
    public TokenBucketPolicy {
        if (capacity < 1) {
            throw new IllegalArgumentException("capacity must be positive");
        }
        if (refillTokens < 1) {
            throw new IllegalArgumentException("refillTokens must be positive");
        }
        if (refillPeriod == null || refillPeriod.isZero() || refillPeriod.isNegative()) {
            throw new IllegalArgumentException("refillPeriod must be positive");
        }
    }

    long refillPeriodNanos() {
        return refillPeriod.toNanos();
    }
}
