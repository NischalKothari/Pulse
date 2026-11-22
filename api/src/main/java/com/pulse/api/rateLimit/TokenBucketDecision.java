package com.pulse.api.rateLimit;

public record TokenBucketDecision(
        boolean allowed,
        long retryAfterSeconds
) {
    public static TokenBucketDecision allow() {
        return new TokenBucketDecision(true, 0);
    }

    public static TokenBucketDecision limit(long retryAfterSeconds) {
        return new TokenBucketDecision(false, Math.max(1, retryAfterSeconds));
    }
}
