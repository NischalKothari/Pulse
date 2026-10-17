package com.pulse.api.monitorService.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record CreateServiceRequest(

        @NotBlank(message = "Service name is required")
        @Size(max = 100, message = "Service name cannot exceed 100 characters")
        String name,

        @NotBlank(message = "URL is required")
        @Size(max = 2048, message = "URL cannot exceed 2048 characters")
        @Pattern(
                regexp = "(?i)^https?://.+$",
                message = "URL must start with http:// or https://"
        )
        String url,

        @NotNull(message = "Check interval is required")
        @Min(value = 15, message = "Check interval must be at least 15 seconds")
        @Max(value = 86400, message = "Check interval cannot exceed 24 hours")
        Integer checkIntervalSeconds,

        @NotNull(message = "Timeout is required")
        @Min(value = 500, message = "Timeout must be at least 500 milliseconds")
        @Max(value = 30000, message = "Timeout cannot exceed 30 seconds")
        Integer timeoutMs

) {
}