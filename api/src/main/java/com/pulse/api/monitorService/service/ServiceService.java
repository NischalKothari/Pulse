package com.pulse.api.monitorService.service;

import com.pulse.api.monitorService.dto.CreateServiceRequest;
import com.pulse.api.monitorService.dto.ServiceResponse;

import java.util.List;
import java.util.UUID;

public interface ServiceService {

    ServiceResponse createService(
            CreateServiceRequest request,
            UUID userId
    );

    List<ServiceResponse> getMyServices(UUID userId);

    ServiceResponse getServiceById(UUID serviceId, UUID userId);

    boolean deleteService(UUID serviceId, UUID userId);
}