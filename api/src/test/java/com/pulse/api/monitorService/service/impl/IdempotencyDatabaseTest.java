package com.pulse.api.monitorService.service.impl;

import com.pulse.api.exception.IdempotencyKeyConflictException;
import com.pulse.api.monitorService.entity.IdempotencyOperation;
import com.pulse.api.monitorService.entity.IdempotencyRecordId;
import com.pulse.api.monitorService.repository.IdempotencyRecordRepository;
import com.pulse.api.user.entity.User;
import com.pulse.api.user.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import com.pulse.api.monitorService.dto.CreateServiceRequest;
import com.pulse.api.monitorService.dto.ServiceResponse;
import com.pulse.api.monitorService.repository.ServiceRepository;
import com.pulse.api.monitorService.service.ServiceService;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.util.UUID;

import tools.jackson.databind.ObjectMapper;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.transaction.annotation.Propagation;

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
        assertThat(serviceRepository.findAllByUserId(userId))
                .hasSize(1);

        // Act 2: retry with the same key and identical payload.
        ServiceResponse retryResponse =
                serviceService.createService(request, userId, key);

        // Assert 2: the original response is replayed.
        assertThat(retryResponse).isEqualTo(firstResponse);

        assertThat(serviceRepository.findAllByUserId(userId))
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

        assertThat(serviceRepository.findAllByUserId(userId))
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
            assertThat(serviceRepository.findAllByUserId(userId))
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
}