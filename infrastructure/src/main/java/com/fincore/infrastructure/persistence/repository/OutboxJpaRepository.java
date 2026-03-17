package com.fincore.infrastructure.persistence.repository;

import com.fincore.infrastructure.persistence.entity.OutboxEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.UUID;

public interface OutboxJpaRepository extends JpaRepository<OutboxEntity, UUID> {
    @Query(value = "SELECT * FROM outbox WHERE published_at IS NULL ORDER BY created_at ASC LIMIT :limit FOR UPDATE SKIP LOCKED", nativeQuery = true)
    List<OutboxEntity> findBatchForPublish(@Param("limit") int limit);
}
