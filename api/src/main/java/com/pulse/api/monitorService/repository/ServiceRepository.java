package com.pulse.api.monitorService.repository;

import com.pulse.api.monitorService.entity.MonitoredService;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ServiceRepository extends JpaRepository<MonitoredService, UUID> {
    Optional<MonitoredService> findByIdAndUserId(UUID id, UUID userId);
    Page<MonitoredService> findAllByUserId(UUID userId, Pageable pageable);
}