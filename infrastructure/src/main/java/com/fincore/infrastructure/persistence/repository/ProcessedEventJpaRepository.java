package com.fincore.infrastructure.persistence.repository;

import com.fincore.infrastructure.persistence.entity.ProcessedEventEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.UUID;

public interface ProcessedEventJpaRepository extends JpaRepository<ProcessedEventEntity, UUID> {
    @Modifying
    @Query(
        value = """
            INSERT INTO processed_events (event_id, processed_at)
            VALUES (:eventId, :processedAt)
            ON CONFLICT (event_id) DO NOTHING
            """,
        nativeQuery = true
    )
    int insert(@Param("eventId") UUID eventId, @Param("processedAt") Instant processedAt);
}
