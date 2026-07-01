package com.dlmp.loan.domain.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;
import org.hibernate.annotations.UuidGenerator;

import java.time.LocalDateTime;

/**
 * Transactional Outbox: event record written in same DB transaction as business data.
 * A relay job reads PENDING events and publishes to Kafka (at-least-once).
 */
@Entity
@Table(name = "outbox_events", indexes = {
    @Index(name = "idx_outbox_status",  columnList = "status"),
    @Index(name = "idx_outbox_created", columnList = "created_at")
})
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(of = "id")
public class OutboxEvent {

    @Id
    @UuidGenerator
    @Column(name = "id", length = 36, updatable = false, nullable = false)
    private String id;

    @Column(name = "aggregate_type", nullable = false, length = 30)
    private String aggregateType;

    @Column(name = "aggregate_id", nullable = false, length = 36)
    private String aggregateId;

    @Column(name = "event_type", nullable = false, length = 100)
    private String eventType;

    @Column(name = "kafka_topic", nullable = false, length = 100)
    private String kafkaTopic;

    @Column(name = "payload", nullable = false, columnDefinition = "TEXT")
    private String payload;

    @Column(name = "status", nullable = false, length = 20)
    @Builder.Default
    private String status = "PENDING";

    @Column(name = "retry_count")
    @Builder.Default
    private int retryCount = 0;

    @Column(name = "max_retries")
    @Builder.Default
    private int maxRetries = 3;

    @Column(name = "last_error", length = 500)
    private String lastError;

    @Column(name = "published_at")
    private LocalDateTime publishedAt;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    public boolean canRetry() {
        return retryCount < maxRetries && !"PUBLISHED".equals(status) && !"DEAD_LETTER".equals(status);
    }

    public void markPublished() {
        this.status = "PUBLISHED";
        this.publishedAt = LocalDateTime.now();
    }

    public void recordFailure(String error) {
        this.retryCount++;
        this.lastError = error != null && error.length() > 500 ? error.substring(0, 500) : error;
        this.status = (retryCount >= maxRetries) ? "DEAD_LETTER" : "FAILED";
    }
}
