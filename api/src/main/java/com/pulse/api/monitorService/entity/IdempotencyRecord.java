
package com.pulse.api.monitorService.entity;

import jakarta.persistence.Column;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "idempotency_records")
@Getter
@Setter
@NoArgsConstructor
public class IdempotencyRecord {

    @EmbeddedId
    private IdempotencyRecordId id;

    @Column(name = "request_hash", nullable = false, length = 64)
    private String requestHash;

    @Column(name = "response_status")
    private Integer responseStatus;

    @Column(name = "response_body", columnDefinition = "TEXT")
    private String responseBody;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    public IdempotencyRecord(
            UUID userId,
            IdempotencyOperation operation,
            String idempotencyKey,
            String requestHash,
            Instant createdAt) {

        this.id = new IdempotencyRecordId(
                userId,
                operation,
                idempotencyKey
        );

        this.requestHash = requestHash;
        this.createdAt = createdAt;
    }
}
