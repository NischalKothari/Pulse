package com.pulse.api.rateLimit;

import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.*;

class InMemoryTokenBucketStoreTest {
    private static final TokenBucketPolicy POLICY =
            new TokenBucketPolicy(5, 1, Duration.ofSeconds(12));

    @Test
    void consumesCapacityAndRefillsUsingElapsedMonotonicTime() {
        AtomicLong time = new AtomicLong(0);
        InMemoryTokenBucketStore store = store(10, time);

        for (int i = 0; i < 5; i++) {
            assertTrue(store.consume("user:1", POLICY).allowed());
        }
        TokenBucketDecision limited = store.consume("user:1", POLICY);
        assertFalse(limited.allowed());
        assertEquals(12, limited.retryAfterSeconds());

        time.addAndGet(Duration.ofSeconds(12).toNanos());
        assertTrue(store.consume("user:1", POLICY).allowed());
    }

    @Test
    void bucketsAreIndependentByKey() {
        InMemoryTokenBucketStore store = store(10, new AtomicLong(0));
        for (int i = 0; i < 5; i++) {
            assertTrue(store.consume("user:1", POLICY).allowed());
        }
        assertFalse(store.consume("user:1", POLICY).allowed());
        assertTrue(store.consume("user:2", POLICY).allowed());
    }

    @Test
    void idleEntriesAreRemovedAndStorageRemainsBounded() {
        AtomicLong time = new AtomicLong(0);
        InMemoryTokenBucketStore store = new InMemoryTokenBucketStore(
                1,
                Duration.ofSeconds(10),
                Duration.ofSeconds(1),
                time::get
        );

        assertTrue(store.consume("ip:1", POLICY).allowed());
        TokenBucketDecision full = store.consume("ip:2", POLICY);
        assertFalse(full.allowed());
        assertEquals(10, full.retryAfterSeconds());

        time.addAndGet(Duration.ofSeconds(10).toNanos());
        assertTrue(store.consume("ip:2", POLICY).allowed());
    }

    @Test
    void concurrentConsumersCannotExceedAvailableTokens() throws Exception {
        AtomicLong time = new AtomicLong(0);
        InMemoryTokenBucketStore store = store(10, time);
        int contenders = 40;
        CountDownLatch ready = new CountDownLatch(contenders);
        CountDownLatch start = new CountDownLatch(1);
        ExecutorService executor = Executors.newFixedThreadPool(contenders);
        List<Future<Boolean>> results = new ArrayList<>();

        try {
            for (int i = 0; i < contenders; i++) {
                results.add(executor.submit(() -> {
                    ready.countDown();
                    start.await();
                    return store.consume("same-user", POLICY).allowed();
                }));
            }
            ready.await();
            start.countDown();

            int accepted = 0;
            for (Future<Boolean> result : results) {
                if (result.get()) accepted++;
            }
            assertEquals(POLICY.capacity(), accepted);
        } finally {
            executor.shutdownNow();
        }
    }

    private InMemoryTokenBucketStore store(int maxEntries, AtomicLong time) {
        return new InMemoryTokenBucketStore(
                maxEntries,
                Duration.ofMinutes(30),
                Duration.ofMinutes(1),
                time::get
        );
    }
}
