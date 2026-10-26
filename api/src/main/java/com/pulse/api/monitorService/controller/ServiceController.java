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

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/services")
public class ServiceController {
    private final ServiceService serviceService;


    @PostMapping
    public ResponseEntity<ServiceResponse> createService(
            @Valid @RequestBody CreateServiceRequest request,
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
            @RequestParam("page") int pageNumber,
            @RequestParam("size") int pageSize
    ) {
        UUID userId = (UUID) authentication.getPrincipal();

        return ResponseEntity.ok(
                serviceService.getMyServices(userId, pageNumber , pageSize)
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