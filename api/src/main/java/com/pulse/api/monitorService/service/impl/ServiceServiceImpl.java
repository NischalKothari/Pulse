package com.pulse.api.monitorService.service.impl;

import com.pulse.api.exception.ResourceNotFoundException;
import com.pulse.api.monitorService.dto.CreateServiceRequest;
import com.pulse.api.monitorService.dto.ServiceResponse;
import com.pulse.api.monitorService.entity.MonitoredService;
import com.pulse.api.monitorService.repository.ServiceRepository;
import com.pulse.api.monitorService.service.ServiceService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class ServiceServiceImpl implements ServiceService {

    private final ServiceRepository serviceRepository;

    @Override
    @Transactional
    public ServiceResponse createService(CreateServiceRequest request, UUID userId) {
        Instant now = Instant.now();

        MonitoredService service = new MonitoredService(
                UUID.randomUUID(),
                userId,
                request.name().trim(),
                request.url().trim(),
                request.checkIntervalSeconds(),
                request.timeoutMs(),
                "UNKNOWN",
                now,
                now
        );

        MonitoredService savedService = serviceRepository.save(service);

        return mapToResponse(savedService);
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
