
package com.pulse.api.monitorService.service;

import com.pulse.api.monitorService.entity.MonitoredService;
import com.pulse.api.monitorService.repository.ServiceRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@ActiveProfiles("test")
class OptimisticLockingDatabaseTest {

    @Autowired
    private ServiceRepository serviceRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private PlatformTransactionManager transactionManager;

    private UUID userId;
    private UUID serviceId;

    @BeforeEach
    void setUp() {
        userId = UUID.randomUUID();
        serviceId = UUID.randomUUID();

        Instant now = Instant.now();
        Timestamp timestamp = Timestamp.from(now);

        jdbcTemplate.update("""
            INSERT INTO users (
                id, email, password_hash, created_at, updated_at
            )
            VALUES (?, ?, ?, ?, ?)
            """,
                userId,
                "lock-test-" + userId + "@example.com",
                "test-password-hash",
                timestamp,
                timestamp
        );

        TransactionTemplate transaction =
                new TransactionTemplate(transactionManager);

        transaction.execute(status -> {
            MonitoredService service = new MonitoredService(
                    serviceId,
                    userId,
                    "Lock Test Service",
                    "https://example.com",
                    60,
                    5000,
                    "UNKNOWN",
                    now,
                    now
            );

            serviceRepository.saveAndFlush(service);
            return null;
        });
    }

    @AfterEach
    void cleanUp() {
        jdbcTemplate.update(
                "DELETE FROM services WHERE id = ?", serviceId);

        jdbcTemplate.update(
                "DELETE FROM users WHERE id = ?", userId);
    }

    @Test
    void staleUpdateIsRejected(){

        // Transaction A reads its copy of the service.
        MonitoredService serviceA = loadInNewTransaction();

        // Transaction B independently reads the same version.
        MonitoredService serviceB = loadInNewTransaction();

        // Both transactions should have read the same initial version.
        assertEquals(serviceA.getVersion(), serviceB.getVersion());

        // Transaction A updates and commits first.
        TransactionTemplate transactionA =
                new TransactionTemplate(transactionManager);

        transactionA.execute(status -> {
            MonitoredService managed =
                    serviceRepository.findById(serviceId).orElseThrow();

            managed.updateTimeout(3000);

            serviceRepository.flush();

            return null;
        });

        TransactionTemplate versionCheck =
                new TransactionTemplate(transactionManager);

        versionCheck.execute(status -> {
            MonitoredService current =
                    serviceRepository.findById(serviceId).orElseThrow();

            assertTrue(current.getVersion() > serviceB.getVersion());

            return null;
        });

        // Transaction B attempts to save its stale copy.
        TransactionTemplate transactionB =
                new TransactionTemplate(transactionManager);

        transactionB.setPropagationBehavior(
                TransactionDefinition.PROPAGATION_REQUIRES_NEW);

        // B's entity still carries the old version.
        serviceB.updateTimeout(8000);

        assertThrows(
                ObjectOptimisticLockingFailureException.class,
                () -> transactionB.execute(status -> {
                    serviceRepository.saveAndFlush(serviceB);
                    return null;
                })
        );

        // Verify that A's committed update remains intact.
        TransactionTemplate verification =
                new TransactionTemplate(transactionManager);

        verification.execute(status -> {
            MonitoredService finalService =
                    serviceRepository.findById(serviceId).orElseThrow();

            assertEquals(3000, finalService.getTimeoutMs());
            assertTrue(finalService.getVersion() > serviceA.getVersion());

            return null;
        });
    }

    private MonitoredService loadInNewTransaction() {
        TransactionTemplate transaction =
                new TransactionTemplate(transactionManager);

        transaction.setPropagationBehavior(
                TransactionDefinition.PROPAGATION_REQUIRES_NEW);

        return transaction.execute(status ->
                serviceRepository.findById(serviceId).orElseThrow()
        );
    }
}
