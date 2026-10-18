package com.pulse.api.monitorService.service.impl;

import com.pulse.api.exception.ResourceNotFoundException;
import com.pulse.api.monitorService.dto.CreateServiceRequest;
import com.pulse.api.monitorService.dto.ServiceResponse;
import com.pulse.api.monitorService.entity.MonitoredService;
import com.pulse.api.monitorService.repository.ServiceRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.pulse.api.monitorService.entity.IdempotencyOperation;
import com.pulse.api.monitorService.entity.IdempotencyRecord;
import com.pulse.api.monitorService.entity.IdempotencyRecordId;
import com.pulse.api.monitorService.repository.IdempotencyRecordRepository;
import com.pulse.api.monitorService.service.impl.RequestHashService;
import tools.jackson.databind.ObjectMapper;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class ServiceServiceImplTest {

    @Mock
    private ServiceRepository serviceRepository;

    @InjectMocks
    private ServiceServiceImpl serviceService;

    @Mock
    private IdempotencyRecordRepository idempotencyRecordRepository;

    @Mock
    private RequestHashService requestHashService;

    @Mock
    private ObjectMapper objectMapper;

    private UUID userId;
    private UUID serviceId;
    private MonitoredService monitoredService;

    @BeforeEach
    void setup(){
        userId = UUID.randomUUID();
        serviceId = UUID.randomUUID();
        Instant now = Instant.parse("2026-01-01T10:00:00Z");
        monitoredService = new MonitoredService(
                serviceId,
                userId,
                "Google",
                "https://google.com",
                60,
                5000,
                "UNKNOWN",
                now,
                now
        );
    }

    @Test
    void createService_shouldSaveAndReturnResponse() throws Exception {
        CreateServiceRequest request = new CreateServiceRequest(
                "  Google  ",
                "  https://google.com  ",
                60,
                5000
        );

        when(requestHashService.calculateHash(any(CreateServiceRequest.class)))
                .thenReturn("test-hash");

        when(idempotencyRecordRepository.claimKey(
                eq(userId),
                eq(IdempotencyOperation.CREATE_SERVICE.name()),
                eq("test-key-123"),
                eq("test-hash")
        )).thenReturn(1);

        when(serviceRepository.save(any(MonitoredService.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        when(objectMapper.writeValueAsString(any(ServiceResponse.class)))
                .thenReturn("{\"name\":\"Google\"}");

        when(idempotencyRecordRepository.findById(any(IdempotencyRecordId.class)))
                .thenAnswer(invocation -> {
                    IdempotencyRecordId id = invocation.getArgument(0);

                    return Optional.of(new IdempotencyRecord(
                            id.getUserId(),
                            IdempotencyOperation.CREATE_SERVICE,
                            id.getIdempotencyKey(),
                            "test-hash",
                            Instant.parse("2026-01-01T10:00:00Z")
                    ));
                });

        ServiceResponse response = serviceService.createService(
                request, userId, "test-key-123"
        );

        ArgumentCaptor<MonitoredService> captor =
                ArgumentCaptor.forClass(MonitoredService.class);

        verify(serviceRepository).save(captor.capture());

        MonitoredService saved = captor.getValue();

        assertThat(saved.getId()).isNotNull();
        assertThat(saved.getUserId()).isEqualTo(userId);
        assertThat(saved.getName()).isEqualTo("Google");
        assertThat(saved.getUrl()).isEqualTo("https://google.com");
        assertThat(saved.getStatus()).isEqualTo("UNKNOWN");
        assertThat(saved.getCreatedAt()).isEqualTo(saved.getUpdatedAt());

        assertThat(response.id()).isEqualTo(saved.getId());
        assertThat(response.name()).isEqualTo("Google");
        assertThat(response.url()).isEqualTo("https://google.com");
        assertThat(response.status()).isEqualTo("UNKNOWN");

        verify(idempotencyRecordRepository).save(any(IdempotencyRecord.class));
    }

    @Test
    void getMyServices_shouldReturnOnlyRepositoryResults() {
        when(serviceRepository.findAllByUserId(userId))
                .thenReturn(List.of(monitoredService));

        List<ServiceResponse> responses =
                serviceService.getMyServices(userId);

        assertThat(responses).hasSize(1);
        assertThat(responses.get(0).id()).isEqualTo(serviceId);
        assertThat(responses.get(0).name()).isEqualTo("Google");

        verify(serviceRepository).findAllByUserId(userId);
    }

    @Test
    void getMyServices_whenNoServices_shouldReturnEmptyList() {
        when(serviceRepository.findAllByUserId(userId))
                .thenReturn(List.of());

        List<ServiceResponse> responses =
                serviceService.getMyServices(userId);

        assertThat(responses).isEmpty();
    }

    @Test
    void getServiceById_whenOwnedServiceExists_shouldReturnResponse() {
        when(serviceRepository.findByIdAndUserId(serviceId, userId))
                .thenReturn(Optional.of(monitoredService));

        ServiceResponse response =
                serviceService.getServiceById(serviceId, userId);

        assertThat(response.id()).isEqualTo(serviceId);
        assertThat(response.name()).isEqualTo("Google");

        verify(serviceRepository).findByIdAndUserId(serviceId, userId);
    }

    @Test
    void getServiceById_whenServiceNotFound_shouldThrowException() {
        when(serviceRepository.findByIdAndUserId(serviceId, userId))
                .thenReturn(Optional.empty());

        assertThatThrownBy(
                () -> serviceService.getServiceById(serviceId, userId)
        )
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessage("Service not found");
    }

    @Test
    void deleteService_whenOwnedServiceExists_shouldDeleteAndReturnTrue() {
        when(serviceRepository.findByIdAndUserId(serviceId, userId))
                .thenReturn(Optional.of(monitoredService));

        boolean deleted =
                serviceService.deleteService(serviceId, userId);

        assertThat(deleted).isTrue();
        verify(serviceRepository).delete(monitoredService);
    }

    @Test
    void deleteService_whenServiceNotFound_shouldReturnFalse() {
        when(serviceRepository.findByIdAndUserId(serviceId, userId))
                .thenReturn(Optional.empty());

        boolean deleted =
                serviceService.deleteService(serviceId, userId);

        assertThat(deleted).isFalse();
        verify(serviceRepository, never()).delete(any(MonitoredService.class));
    }
}
