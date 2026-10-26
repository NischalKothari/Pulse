
package com.pulse.api.monitorService.service;

import com.pulse.api.monitorService.entity.MonitoredService;
import com.pulse.api.monitorService.repository.ServiceRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

import com.pulse.api.monitorService.dto.ServiceResponse;
import com.pulse.api.monitorService.service.ServiceService;
import java.util.stream.Stream;

@SpringBootTest
@ActiveProfiles("test")
class ServicePaginationDatabaseTest {

    @Autowired
    private ServiceRepository serviceRepository;

    @Autowired
    private ServiceService serviceService;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private PlatformTransactionManager transactionManager;

    private UUID userId;
    private final List<UUID> serviceIds = new ArrayList<>();

    @BeforeEach
    void setUp() {
        userId = UUID.randomUUID();

        Instant now = Instant.now();
        Timestamp timestamp = Timestamp.from(now);

        jdbcTemplate.update("""
            INSERT INTO users (
                id, email, password_hash, created_at, updated_at
            )
            VALUES (?, ?, ?, ?, ?)
            """,
                userId,
                "pagination-" + userId + "@example.com",
                "test-password-hash",
                timestamp,
                timestamp
        );
    }

    @AfterEach
    void cleanUp() {
        for (UUID id : serviceIds) {
            jdbcTemplate.update(
                    "DELETE FROM services WHERE id = ?", id
            );
        }

        jdbcTemplate.update(
                "DELETE FROM users WHERE id = ?", userId
        );
    }

    private void createService(
            UUID ownerId,
            String name,
            Instant createdAt
    ) {
        UUID serviceId = UUID.randomUUID();
        serviceIds.add(serviceId);

        MonitoredService service = new MonitoredService(
                serviceId,
                ownerId,
                name,
                "https://example.com/" + name.toLowerCase(),
                60,
                5000,
                "UNKNOWN",
                createdAt,
                createdAt
        );

        TransactionTemplate transaction =
                new TransactionTemplate(transactionManager);

        transaction.execute(status -> {
            serviceRepository.saveAndFlush(service);
            return null;
        });
    }

    @Test
    void paginationReturnsCorrectRecordsInDescendingOrder() {
        // We'll implement this test in the next step.
        createServices(5);
        Page<ServiceResponse> firstPage =
                serviceService.getMyServices(userId, 0, 2);

        Page<ServiceResponse> secondPage =
                serviceService.getMyServices(userId, 1, 2);

        Page<ServiceResponse> thirdPage =
                serviceService.getMyServices(userId, 2, 2);

        // Assert 1: each page contains the expected number of records.
        assertEquals(2, firstPage.getContent().size());
        assertEquals(2, secondPage.getContent().size());
        assertEquals(1, thirdPage.getContent().size());

        // Assert 2: pagination metadata is correct.
        assertEquals(5, firstPage.getTotalElements());
        assertEquals(3, firstPage.getTotalPages());
        assertEquals(3, thirdPage.getTotalPages());

        // Assert 3: all pages together contain exactly five records.
        List<ServiceResponse> allServices = Stream.of(
                        firstPage, secondPage, thirdPage
                )
                .flatMap(page -> page.getContent().stream())
                .toList();

        assertEquals(5, allServices.size());

        List<UUID> actualIds = allServices.stream()
                .map(ServiceResponse::id)
                .toList();

        assertEquals(5, actualIds.stream().distinct().count());

        // Assert 4: services are ordered newest to oldest.
        List<String> actualNames = allServices.stream()
                .map(ServiceResponse::name)
                .toList();

        assertEquals(
                List.of(
                        "Service 4",
                        "Service 3",
                        "Service 2",
                        "Service 1",
                        "Service 0"
                ),
                actualNames
        );

    }

    private void createServices(int count) {
        Instant baseTime = Instant.parse("2026-01-01T10:00:00Z");

        for (int i = 0; i < count; i++) {
            createService(
                    userId,
                    "Service " + i,
                    baseTime.plusSeconds(i * 60L)
            );
        }
    }


    @Test
    void paginationReturnsOnlyServicesOwnedByRequestedUser() {
        UUID userAId = userId;
        UUID userBId = UUID.randomUUID();

        Instant now = Instant.now();
        Timestamp timestamp = Timestamp.from(now);

        jdbcTemplate.update("""
        INSERT INTO users (
            id, email, password_hash, created_at, updated_at
        )
        VALUES (?, ?, ?, ?, ?)
        """,
                userBId,
                "pagination-" + userBId + "@example.com",
                "test-password-hash",
                timestamp,
                timestamp
        );

        try {
            // Arrange: create services owned by two different users.
            createService(userAId, "UserA-Service1", now);
            createService(userAId, "UserA-Service2", now.plusSeconds(60));
            createService(userBId, "UserB-Service1", now.plusSeconds(120));

            // Act: request each user's services.
            Page<ServiceResponse> userAPage =
                    serviceService.getMyServices(userAId, 0, 10);

            Page<ServiceResponse> userBPage =
                    serviceService.getMyServices(userBId, 0, 10);

            // Assert: correct totals.
            assertEquals(2, userAPage.getTotalElements());
            assertEquals(1, userBPage.getTotalElements());

            // Assert: every returned service belongs to the requested user.
            assertTrue(userAPage.getContent().stream()
                    .allMatch(s -> s.name().startsWith("UserA-")));

            assertTrue(userBPage.getContent().stream()
                    .allMatch(s -> s.name().startsWith("UserB-")));

            // Assert: the two result sets contain different service IDs.
            List<UUID> userAServiceIds = userAPage.getContent().stream()
                    .map(ServiceResponse::id)
                    .toList();

            List<UUID> userBServiceIds = userBPage.getContent().stream()
                    .map(ServiceResponse::id)
                    .toList();

            assertTrue(userAServiceIds.stream()
                    .noneMatch(userBServiceIds::contains));

        } finally {
            // Delete dependent records before deleting User B.
            jdbcTemplate.update(
                    "DELETE FROM services WHERE user_id = ?", userBId
            );

            jdbcTemplate.update(
                    "DELETE FROM users WHERE id = ?", userBId
            );
        }
    }

}