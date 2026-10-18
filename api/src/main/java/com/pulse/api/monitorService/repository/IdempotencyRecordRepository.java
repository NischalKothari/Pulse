package com.pulse.api.monitorService.repository;

import com.pulse.api.monitorService.entity.IdempotencyRecord;
import com.pulse.api.monitorService.entity.IdempotencyRecordId;
import org.springframework.data.jpa.repository.JpaRepository;

import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

public interface IdempotencyRecordRepository extends JpaRepository<IdempotencyRecord, IdempotencyRecordId> {

    @Modifying
    @Transactional
    @Query(value = """
        INSERT INTO idempotency_records (
            user_id,
            operation,
            idempotency_key,
            request_hash
        )
        VALUES (
            :userId,
            :operation,
            :idempotencyKey,
            :requestHash
        )
        ON CONFLICT (user_id, operation, idempotency_key)
        DO NOTHING
        """, nativeQuery = true)
    int claimKey(
            @Param("userId") UUID userId,
            @Param("operation") String operation,
            @Param("idempotencyKey") String idempotencyKey,
            @Param("requestHash") String requestHash
    );

}