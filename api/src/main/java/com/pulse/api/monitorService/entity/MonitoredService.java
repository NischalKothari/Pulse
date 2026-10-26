package com.pulse.api.monitorService.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "services")
@NoArgsConstructor
@Getter
public class MonitoredService {

    @Id
    private UUID id;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Column(nullable = false, length = 100)
    private String name;

    @Column(nullable = false)
    private String url;

    @Column(name = "check_interval_seconds", nullable = false)
    private Integer checkIntervalSeconds;

    @Column(name = "timeout_ms", nullable = false)
    private Integer timeoutMs;

    @Column(nullable = false, length = 20)
    private String status;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Version
    @Column(nullable = false)
    private Long version;

    public MonitoredService(
            UUID id,
            UUID userId,
            String name,
            String url,
            Integer checkIntervalSeconds,
            Integer timeoutMs,
            String status,
            Instant createdAt,
            Instant updatedAt
    ) {
        this.id = id;
        this.userId = userId;
        this.name = name;
        this.url = url;
        this.checkIntervalSeconds = checkIntervalSeconds;
        this.timeoutMs = timeoutMs;
        this.status = status;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
    }

    public void updateTimeout(Integer timeoutMs) {
        this.timeoutMs = timeoutMs;
    }
}
