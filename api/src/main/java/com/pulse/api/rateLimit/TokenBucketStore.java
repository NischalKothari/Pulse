package com.pulse.api.rateLimit;

/** Storage boundary for atomic token consumption; a shared Redis adapter can implement this later. */
public interface TokenBucketStore {
    TokenBucketDecision consume(String key, TokenBucketPolicy policy);
}
