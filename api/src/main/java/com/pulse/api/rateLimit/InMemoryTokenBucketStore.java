package com.pulse.api.rateLimit;

import java.time.Duration;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.function.LongSupplier;

/**
 * Single-process token buckets. Synchronization makes refill and consume atomic;
 * the bounded map and opportunistic idle sweep keep arbitrary client keys bounded.
 */
public class InMemoryTokenBucketStore implements TokenBucketStore {
    private final Map<String, Bucket> buckets = new HashMap<>();
    private final int maxEntries;
    private final long idleTtlNanos;
    private final long cleanupIntervalNanos;
    private final LongSupplier nanoTime;
    private long lastCleanupNanos;

    public InMemoryTokenBucketStore(RateLimitProperties properties) {
        this(
                properties.getMaxEntries(),
                properties.getIdleTtl(),
                properties.getCleanupInterval(),
                System::nanoTime
        );
    }

    public InMemoryTokenBucketStore(
            int maxEntries,
            Duration idleTtl,
            Duration cleanupInterval,
            LongSupplier nanoTime
    ) {
        if (maxEntries < 1 || idleTtl.isZero() || idleTtl.isNegative()
                || cleanupInterval.isZero() || cleanupInterval.isNegative()) {
            throw new IllegalArgumentException("Storage bounds and durations must be positive");
        }
        this.maxEntries = maxEntries;
        this.idleTtlNanos = idleTtl.toNanos();
        this.cleanupIntervalNanos = cleanupInterval.toNanos();
        this.nanoTime = nanoTime;
        this.lastCleanupNanos = nanoTime.getAsLong();
    }

    @Override
    public synchronized TokenBucketDecision consume(String key, TokenBucketPolicy policy) {
        long now = nanoTime.getAsLong();
        cleanupWhenDue(now);

        Bucket bucket = buckets.get(key);
        if (bucket == null) {
            if (buckets.size() >= maxEntries) {
                // Do not scan the whole map for every attacker-controlled new key.
                // The periodic sweep will reclaim idle entries; this delay is
                // conservative and prevents memory-pressure requests from causing
                // repeated O(n) work.
                return TokenBucketDecision.limit(idleTtlSeconds());
            }
            bucket = new Bucket(policy.capacity(), now);
            buckets.put(key, bucket);
        }

        bucket.refill(policy, now);
        bucket.lastAccessNanos = now;
        if (bucket.tokens >= 1.0) {
            bucket.tokens -= 1.0;
            return TokenBucketDecision.allow();
        }

        double nanosPerToken = (double) policy.refillPeriodNanos() / policy.refillTokens();
        long retryNanos = (long) Math.ceil((1.0 - bucket.tokens) * nanosPerToken);
        long retrySeconds = Math.max(1, (retryNanos + 999_999_999L) / 1_000_000_000L);
        return TokenBucketDecision.limit(retrySeconds);
    }

    private void cleanupWhenDue(long now) {
        if (now - lastCleanupNanos >= cleanupIntervalNanos) {
            removeIdleEntries(now);
            lastCleanupNanos = now;
        }
    }

    private void removeIdleEntries(long now) {
        Iterator<Map.Entry<String, Bucket>> iterator = buckets.entrySet().iterator();
        while (iterator.hasNext()) {
            Bucket bucket = iterator.next().getValue();
            if (now - bucket.lastAccessNanos >= idleTtlNanos) {
                iterator.remove();
            }
        }
    }

    private long idleTtlSeconds() {
        return Math.max(1, (idleTtlNanos + 999_999_999L) / 1_000_000_000L);
    }

    private static final class Bucket {
        private double tokens;
        private long lastRefillNanos;
        private long lastAccessNanos;

        private Bucket(int capacity, long now) {
            this.tokens = capacity;
            this.lastRefillNanos = now;
            this.lastAccessNanos = now;
        }

        private void refill(TokenBucketPolicy policy, long now) {
            tokens = Math.min(tokens, policy.capacity());
            long elapsedNanos = now - lastRefillNanos;
            if (elapsedNanos > 0) {
                tokens = Math.min(
                        policy.capacity(),
                        tokens + (double) elapsedNanos * policy.refillTokens()
                                / policy.refillPeriodNanos()
                );
                lastRefillNanos = now;
            }
        }
    }
}
