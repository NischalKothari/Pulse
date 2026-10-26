package com.pulse.api.monitorService.service.impl;

import com.pulse.api.exception.IdempotencyKeyConflictException;
import com.pulse.api.monitorService.entity.IdempotencyOperation;
import com.pulse.api.monitorService.entity.IdempotencyRecord;
import com.pulse.api.monitorService.entity.IdempotencyRecordId;
import com.pulse.api.monitorService.entity.MonitoredService;
import com.pulse.api.monitorService.repository.IdempotencyRecordRepository;
import com.pulse.api.user.entity.User;
import com.pulse.api.user.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import com.pulse.api.monitorService.dto.CreateServiceRequest;
import com.pulse.api.monitorService.dto.ServiceResponse;
import com.pulse.api.monitorService.repository.ServiceRepository;
import com.pulse.api.monitorService.service.ServiceService;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.springframework.context.ApplicationContext;

import org.springframework.transaction.annotation.Propagation;

import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import java.time.Instant;
import java.util.UUID;

import tools.jackson.databind.ObjectMapper;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
class IdempotencyDatabaseTest {

    @Autowired
    private IdempotencyRecordRepository idempotencyRecordRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private ServiceRepository serviceRepository;

    @Autowired
    private ServiceService serviceService;

    @MockitoSpyBean
    private ObjectMapper objectMapper;

    @Autowired
    private ApplicationContext applicationContext;

    @Test
    void postgresAllowsOnlyOneClaimForSameKey() {
        // Arrange: create a unique test user.
        UUID userId = UUID.randomUUID();
        Instant now = Instant.now();

        User user = new User(
                userId,
                "idempotency-test-" + userId + "@example.com",
                "test-password-hash",
                now,
                now
        );

        userRepository.saveAndFlush(user);

        String key = "test-key-" + UUID.randomUUID();
        String requestHash = "a".repeat(64);

        // Act: attempt to claim the same key twice.
        int firstClaim = idempotencyRecordRepository.claimKey(
                userId,
                IdempotencyOperation.CREATE_SERVICE.name(),
                key,
                requestHash
        );

        int secondClaim = idempotencyRecordRepository.claimKey(
                userId,
                IdempotencyOperation.CREATE_SERVICE.name(),
                key,
                requestHash
        );

        // Assert: PostgreSQL accepts the first claim only.
        assertThat(firstClaim).isEqualTo(1);
        assertThat(secondClaim).isZero();
    }

    @Test
    void createServiceIsIdempotentAgainstPostgres() {
        // Arrange: create a unique user for this test.
        UUID userId = UUID.randomUUID();
        Instant now = Instant.now();

        User user = new User(
                userId,
                "idempotency-flow-" + userId + "@example.com",
                "test-password-hash",
                now,
                now
        );

        userRepository.saveAndFlush(user);

        String key = "service-create-" + UUID.randomUUID();

        CreateServiceRequest request = new CreateServiceRequest(
                "My Test Service",
                "https://example.com/health",
                60,
                2000
        );

        // Act 1: create the service for the first time.
        ServiceResponse firstResponse =
                serviceService.createService(request, userId, key);

        // Assert 1: exactly one service exists.
        assertThat(serviceRepository.findAllByUserId(
                userId,
                PageRequest.of(0, 100)
        ).getContent())
                .hasSize(1);

        // Act 2: retry with the same key and identical payload.
        ServiceResponse retryResponse =
                serviceService.createService(request, userId, key);

        // Assert 2: the original response is replayed.
        assertThat(retryResponse).isEqualTo(firstResponse);

        assertThat(serviceRepository.findAllByUserId(
                userId,
                PageRequest.of(0, 100)
        ).getContent())
                .hasSize(1);

        // Act 3: reuse the key with a different payload.
        CreateServiceRequest changedRequest = new CreateServiceRequest(
                "Different Service",
                "https://example.com/other",
                60,
                2000
        );

        // Assert 3: the changed request is rejected.
        assertThatThrownBy(() ->
                serviceService.createService(changedRequest, userId, key)
        ).isInstanceOf(IdempotencyKeyConflictException.class);

        assertThat(serviceRepository.findAllByUserId(
                userId,
                PageRequest.of(0, 100)
        ).getContent())
                .hasSize(1);
    }

    @Test
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    void failedResponseSerializationRollsBackServiceAndIdempotencyRecord() {
        UUID userId = UUID.randomUUID();
        Instant now = Instant.now();

        User user = new User(
                userId,
                "rollback-test-" + userId + "@example.com",
                "test-password-hash",
                now,
                now
        );

        userRepository.saveAndFlush(user);

        String key = "rollback-" + UUID.randomUUID();

        CreateServiceRequest request = new CreateServiceRequest(
                "Rollback Test Service",
                "https://example.com/health",
                60,
                2000
        );

        try {
            // Force a failure after the service is saved,
            // when the response is being serialized.
            doThrow(new IllegalStateException("Simulated serialization failure"))
                    .when(objectMapper)
                    .writeValueAsString(any(ServiceResponse.class));

            assertThatThrownBy(() ->
                    serviceService.createService(request, userId, key)
            ).isInstanceOf(IllegalStateException.class)
                    .hasMessage("Simulated serialization failure");

            // Inspect the database after the service transaction rolls back.
            assertThat(serviceRepository.findAllByUserId(
                    userId,
                    PageRequest.of(0, 100)
            ).getContent())
                    .isEmpty();

            IdempotencyRecordId recordId = new IdempotencyRecordId(
                    userId,
                    IdempotencyOperation.CREATE_SERVICE,
                    key
            );

            assertThat(idempotencyRecordRepository.findById(recordId))
                    .isEmpty();

        } finally {
            // Remove only this test's user.
            userRepository.deleteById(userId);
            userRepository.flush();
        }
    }


    @Test
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    void concurrentRequestsWithSameKeyCreateOnlyOneService()
            throws Exception {

        UUID userId = UUID.randomUUID();
        Instant now = Instant.now();

        User user = new User(
                userId,
                "concurrent-" + userId + "@example.com",
                "test-password-hash",
                now,
                now
        );

        String key = "concurrent-" + UUID.randomUUID();

        CreateServiceRequest request = new CreateServiceRequest(
                "Google",
                "https://google.com",
                60,
                5000
        );

        userRepository.saveAndFlush(user);

        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);

        ExecutorService executor = Executors.newFixedThreadPool(2);

        try {
            java.util.concurrent.Callable<ServiceResponse> task = () -> {
                ready.countDown();

                if (!start.await(10, TimeUnit.SECONDS)) {
                    throw new IllegalStateException(
                            "Timed out waiting for concurrent start"
                    );
                }

                return serviceService.createService(request, userId, key);
            };

            Future<ServiceResponse> first = executor.submit(task);
            Future<ServiceResponse> second = executor.submit(task);

            assertThat(ready.await(10, TimeUnit.SECONDS))
                    .as("Both requests should be ready")
                    .isTrue();

            // Release both requests.
            start.countDown();

            ServiceResponse firstResponse = first.get(20, TimeUnit.SECONDS);
            ServiceResponse secondResponse = second.get(20, TimeUnit.SECONDS);

            // Both calls must resolve to the same resource.
            assertThat(firstResponse.id())
                    .isEqualTo(secondResponse.id());

            // Exactly one service must exist for this user.
            assertThat(serviceRepository.findAllByUserId(
                    userId,
                    PageRequest.of(0, 100)
            ).getContent())
                    .hasSize(1);

            IdempotencyRecordId recordId = new IdempotencyRecordId(
                    userId,
                    IdempotencyOperation.CREATE_SERVICE,
                    key
            );

            IdempotencyRecord record = idempotencyRecordRepository
                    .findById(recordId)
                    .orElseThrow();

            assertThat(record.getResponseStatus()).isEqualTo(201);
            assertThat(record.getResponseBody()).isNotBlank();

        } finally {
            // Ensure waiting threads are released if an assertion fails.
            start.countDown();

            executor.shutdownNow();
            assertThat(executor.awaitTermination(10, TimeUnit.SECONDS))
                    .as("Concurrent test threads should terminate")
                    .isTrue();

            // Delete test data in foreign-key-safe order.
            org.springframework.jdbc.core.JdbcTemplate jdbcTemplate =
                    applicationContext.getBean(
                            org.springframework.jdbc.core.JdbcTemplate.class
                    );

            jdbcTemplate.update(
                    "DELETE FROM idempotency_records WHERE user_id = ?",
                    userId
            );
            jdbcTemplate.update(
                    "DELETE FROM services WHERE user_id = ?",
                    userId
            );
            jdbcTemplate.update(
                    "DELETE FROM users WHERE id = ?",
                    userId
            );
        }
    }


    @Test
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    void concurrentRequestsWithSameKeyAndDifferentPayloadsAllowOnlyOne()
            throws Exception {

        UUID userId = UUID.randomUUID();
        Instant now = Instant.now();

        User user = new User(
                userId,
                "conflict-" + userId + "@example.com",
                "test-password-hash",
                now,
                now
        );

        userRepository.saveAndFlush(user);

        String key = "conflict-" + UUID.randomUUID();

        CreateServiceRequest googleRequest = new CreateServiceRequest(
                "Google",
                "https://google.com",
                60,
                5000
        );

        CreateServiceRequest githubRequest = new CreateServiceRequest(
                "GitHub",
                "https://github.com",
                60,
                5000
        );

        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);
        ExecutorService executor = Executors.newFixedThreadPool(2);

        try {
            java.util.concurrent.Callable<Object> googleTask = () -> {
                ready.countDown();

                if (!start.await(10, TimeUnit.SECONDS)) {
                    throw new IllegalStateException("Timed out waiting to start");
                }

                try {
                    return serviceService.createService(
                            googleRequest, userId, key
                    );
                } catch (IdempotencyKeyConflictException exception) {
                    return exception;
                }
            };

            java.util.concurrent.Callable<Object> githubTask = () -> {
                ready.countDown();

                if (!start.await(10, TimeUnit.SECONDS)) {
                    throw new IllegalStateException("Timed out waiting to start");
                }

                try {
                    return serviceService.createService(
                            githubRequest, userId, key
                    );
                } catch (IdempotencyKeyConflictException exception) {
                    return exception;
                }
            };

            Future<Object> googleFuture = executor.submit(googleTask);
            Future<Object> githubFuture = executor.submit(githubTask);

            assertThat(ready.await(10, TimeUnit.SECONDS))
                    .as("Both requests should be ready")
                    .isTrue();

            start.countDown();

            Object googleResult = googleFuture.get(20, TimeUnit.SECONDS);
            Object githubResult = githubFuture.get(20, TimeUnit.SECONDS);

            // Exactly one request succeeds.
            int successCount =
                    (googleResult instanceof ServiceResponse ? 1 : 0)
                            + (githubResult instanceof ServiceResponse ? 1 : 0);

            assertThat(successCount).isEqualTo(1);

            // The other request must receive a key conflict.
            int conflictCount =
                    (googleResult instanceof IdempotencyKeyConflictException ? 1 : 0)
                            + (githubResult instanceof IdempotencyKeyConflictException ? 1 : 0);

            assertThat(conflictCount).isEqualTo(1);

            ServiceResponse winningResponse =
                    googleResult instanceof ServiceResponse
                            ? (ServiceResponse) googleResult
                            : (ServiceResponse) githubResult;

            // Only one service was created.
            List<MonitoredService> services =
                    serviceRepository.findAllByUserId(
                            userId,
                            PageRequest.of(0, 100)
                    ).getContent();

            assertThat(services).hasSize(1);
            assertThat(services.get(0).getId())
                    .isEqualTo(winningResponse.id());

            // The idempotency record contains the completed response.
            IdempotencyRecordId recordId = new IdempotencyRecordId(
                    userId,
                    IdempotencyOperation.CREATE_SERVICE,
                    key
            );

            IdempotencyRecord record = idempotencyRecordRepository
                    .findById(recordId)
                    .orElseThrow();

            assertThat(record.getResponseStatus()).isEqualTo(201);
            assertThat(record.getResponseBody()).isNotBlank();

        } finally {
            start.countDown();

            executor.shutdownNow();
            assertThat(executor.awaitTermination(10, TimeUnit.SECONDS))
                    .as("Concurrent test threads should terminate")
                    .isTrue();

            org.springframework.jdbc.core.JdbcTemplate jdbcTemplate =
                    applicationContext.getBean(
                            org.springframework.jdbc.core.JdbcTemplate.class
                    );

            jdbcTemplate.update(
                    "DELETE FROM idempotency_records WHERE user_id = ?",
                    userId
            );
            jdbcTemplate.update(
                    "DELETE FROM services WHERE user_id = ?",
                    userId
            );
            jdbcTemplate.update(
                    "DELETE FROM users WHERE id = ?",
                    userId
            );
        }
    }


}