package com.pulse.api.monitorService.service;

import com.pulse.api.monitorService.dto.CreateServiceRequest;
import com.pulse.api.monitorService.dto.ServiceResponse;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.List;
import java.util.UUID;

public interface ServiceService {

    ServiceResponse createService(
            CreateServiceRequest request,
            UUID userId,
            String idempotencyKey
    );

    Page<ServiceResponse> getMyServices(UUID userId, int pageNumber, int pageSize);

    ServiceResponse getServiceById(UUID serviceId, UUID userId);

    boolean deleteService(UUID serviceId, UUID userId);
}