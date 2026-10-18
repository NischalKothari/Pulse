package com.pulse.api.monitorService.service.impl;

import com.pulse.api.exception.IdempotencyKeyConflictException;
import com.pulse.api.exception.ResourceNotFoundException;
import com.pulse.api.monitorService.dto.CreateServiceRequest;
import com.pulse.api.monitorService.dto.ServiceResponse;
import com.pulse.api.monitorService.entity.IdempotencyOperation;
import com.pulse.api.monitorService.entity.IdempotencyRecord;
import com.pulse.api.monitorService.entity.IdempotencyRecordId;
import com.pulse.api.monitorService.entity.MonitoredService;
import com.pulse.api.monitorService.repository.IdempotencyRecordRepository;
import com.pulse.api.monitorService.repository.ServiceRepository;
import com.pulse.api.monitorService.service.ServiceService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class ServiceServiceImpl implements ServiceService {

    private final ServiceRepository serviceRepository;
    private final IdempotencyRecordRepository idempotencyRecordRepository;
    private final RequestHashService requestHashService;
    private final ObjectMapper objectMapper;


    @Override
    @Transactional
    public ServiceResponse createService(CreateServiceRequest request, UUID userId, String idempotencyKey ) {
        // 1. Normalize the request before hashing and saving.
        CreateServiceRequest normalizedRequest = new CreateServiceRequest(
                request.name().trim(),
                request.url().trim(),
                request.checkIntervalSeconds(),
                request.timeoutMs()
        );

        // 2. Calculate the fingerprint of the normalized payload.
        String requestHash =
                requestHashService.calculateHash(normalizedRequest);

        IdempotencyOperation operation =
                IdempotencyOperation.CREATE_SERVICE;

        String key = idempotencyKey.trim();

        // 3. Build the composite primary key.
        IdempotencyRecordId recordId = new IdempotencyRecordId(
                userId,
                operation,
                key
        );

        // 4. Atomically claim the key using PostgreSQL.
        int claimed = idempotencyRecordRepository.claimKey(
                userId,
                operation.name(),
                key,
                requestHash
        );

        // 5. If we did not claim it, this key already exists.
        if (claimed == 0) {
            IdempotencyRecord existing = idempotencyRecordRepository
                    .findById(recordId)
                    .orElseThrow(() -> new IllegalStateException(
                            "Idempotency record missing after key conflict"
                    ));

            // Same key cannot be reused with a different payload.
            if (!existing.getRequestHash().equals(requestHash)) {
                throw new IdempotencyKeyConflictException(
                        "Idempotency key was already used with a different request"
                );
            }

            // A completed operation must have a saved response.
            if (existing.getResponseStatus() == null
                    || existing.getResponseBody() == null) {
                throw new IllegalStateException(
                        "Idempotency record has no completed response"
                );
            }

            // 6. Replay the original response body.
            try {
                return objectMapper.readValue(
                        existing.getResponseBody(),
                        ServiceResponse.class
                );
            } catch (JacksonException e) {
                throw new IllegalStateException(
                        "Failed to deserialize stored service response", e
                );
            }
        }

        // 7. We claimed a new key. Create the service.
        Instant now = Instant.now();

        MonitoredService service = new MonitoredService(
                UUID.randomUUID(),
                userId,
                normalizedRequest.name(),
                normalizedRequest.url(),
                normalizedRequest.checkIntervalSeconds(),
                normalizedRequest.timeoutMs(),
                "UNKNOWN",
                now,
                now
        );

        MonitoredService savedService = serviceRepository.save(service);

        ServiceResponse response = mapToResponse(savedService);

        // 8. Save the original response against the claimed key.
        IdempotencyRecord record = idempotencyRecordRepository
                .findById(recordId)
                .orElseThrow(() -> new IllegalStateException(
                        "Claimed idempotency record was not found"
                ));

        try {
            record.setResponseStatus(201);
            record.setResponseBody(
                    objectMapper.writeValueAsString(response)
            );

            idempotencyRecordRepository.save(record);
        } catch (JacksonException e) {
            throw new IllegalStateException(
                    "Failed to serialize service response", e
            );
        }

        // 9. Transaction commits both database changes together.
        return response;
    }

    private ServiceResponse mapToResponse(MonitoredService service){
        return new ServiceResponse(
                service.getId(),
                service.getName(),
                service.getUrl(),
                service.getCheckIntervalSeconds(),
                service.getTimeoutMs(),
                service.getStatus(),
                service.getCreatedAt(),
                service.getUpdatedAt()
        );
    }

    @Transactional
    @Override
    public List<ServiceResponse> getMyServices(UUID userId) {
        return serviceRepository.findAllByUserId(userId)
                .stream()
                .map(this::mapToResponse)
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public ServiceResponse getServiceById(UUID serviceId, UUID userId) {
        MonitoredService service = serviceRepository
                .findByIdAndUserId(serviceId,userId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Service not found"
                ));
        return mapToResponse(service);
    }

    @Transactional
    @Override
    public boolean deleteService(UUID serviceId, UUID userId) {
        Optional<MonitoredService> service =
                serviceRepository.findByIdAndUserId(serviceId, userId);

        if (service.isEmpty()) {
            return false;
        }

        serviceRepository.delete(service.get());
        return true;
    }
}
