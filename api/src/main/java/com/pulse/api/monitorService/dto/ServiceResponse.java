package com.pulse.api.monitorService.dto;

import java.time.Instant;
import java.util.UUID;

public record ServiceResponse(
        UUID id,
        String name,
        String url,
        Integer checkIntervalSeconds,
        Integer timeoutMs,
        String status,
        Instant createdAt,
        Instant updatedAt
) {
}