package com.dlmp.user.repository;

import com.dlmp.user.domain.entity.OutboxEvent;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface OutboxEventRepository extends JpaRepository<OutboxEvent, String> {

    @Query(value = "SELECT o FROM OutboxEvent o WHERE o.status IN ('PENDING', 'FAILED') " +
                   "AND o.retryCount < o.maxRetries ORDER BY o.createdAt ASC LIMIT :limit")
    List<OutboxEvent> findPendingForPublishing(int limit);

    long countByStatus(String status);
}
