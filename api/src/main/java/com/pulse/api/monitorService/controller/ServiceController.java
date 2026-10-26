package com.pulse.api.monitorService.controller;

import com.pulse.api.exception.ResourceNotFoundException;
import com.pulse.api.monitorService.dto.CreateServiceRequest;
import com.pulse.api.monitorService.dto.ServiceResponse;
import com.pulse.api.monitorService.service.ServiceService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.net.URI;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.enums.ParameterIn;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/services")
public class ServiceController {
    private final ServiceService serviceService;

    @Operation(
            summary = "Create a monitored service",
            description = """
        Registers an HTTP endpoint for monitoring.
        Requires a unique Idempotency-Key for the operation.
        Repeating the same request with the same key returns
        the original result.
        """
    )
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Service created"),
            @ApiResponse(responseCode = "400", description = "Invalid request"),
            @ApiResponse(responseCode = "401", description = "Authentication required"),
            @ApiResponse(responseCode = "409", description = "Idempotency key conflict")
    })
    @PostMapping
    public ResponseEntity<ServiceResponse> createService(
            @Valid @RequestBody CreateServiceRequest request,
            @Parameter(
                    name = "Idempotency-Key",
                    description = "Unique key for safely retrying this creation request",
                    required = true,
                    in = ParameterIn.HEADER
            )
            @RequestHeader(value = "Idempotency-Key", required = false)
            String idempotencyKey,
            Authentication authentication
    ) {
        if (idempotencyKey == null || idempotencyKey.isBlank()) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Idempotency-Key header is required and must not be blank"
            );
        }

        if (idempotencyKey.length() > 255) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Idempotency-Key must not exceed 255 characters"
            );
        }

        UUID userId = (UUID) authentication.getPrincipal();

        ServiceResponse response = serviceService.createService(
                request, userId, idempotencyKey
        );

        URI location = URI.create("/api/v1/services/" + response.id());

        return ResponseEntity.created(location).body(response);
    }



    @GetMapping
    public ResponseEntity<Page<ServiceResponse>> getMyServices(
            Authentication authentication,
            @RequestParam(name = "page", defaultValue = "0") int pageNumber,
            @RequestParam(name = "size", defaultValue = "20") int pageSize
    ) {
        if (pageNumber < 0) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Page number must be greater than or equal to 0"
            );
        }

        if (pageSize < 1 || pageSize > 100) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Page size must be between 1 and 100"
            );
        }

        UUID userId = (UUID) authentication.getPrincipal();

        return ResponseEntity.ok(
                serviceService.getMyServices(userId, pageNumber, pageSize)
        );
    }


    @GetMapping("/{serviceId}")
    public ResponseEntity<ServiceResponse> getServiceById(
            @PathVariable UUID serviceId,
            Authentication authentication
    ) {
        UUID userId = (UUID) authentication.getPrincipal();

        ServiceResponse response = serviceService.getServiceById(serviceId, userId);
        return ResponseEntity.ok(response);
    }

    @DeleteMapping("/{serviceId}")
    public ResponseEntity<Map<String, String>> deleteServiceById(
            @PathVariable UUID serviceId,
            Authentication authentication
    ){
        UUID userId = (UUID) authentication.getPrincipal();

        boolean deleted = serviceService.deleteService(serviceId, userId);

        if (!deleted) {
            throw new ResourceNotFoundException("Service not found");
        }

        return ResponseEntity.ok(
                Map.of("message", "Service deleted successfully")
        );
    }
}