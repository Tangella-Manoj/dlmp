package com.dlmp.common.event;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

import java.time.Instant;
import java.util.UUID;

@Data
@SuperBuilder
@NoArgsConstructor
@AllArgsConstructor
public abstract class DomainEvent {
    private String eventId;
    private String eventType;
    private String aggregateId;
    private String aggregateType;
    private Instant occurredAt;
    private String traceId;

    protected static void init(DomainEvent e, String aggregateId, String aggregateType, String traceId) {
        e.setEventId(UUID.randomUUID().toString());
        e.setAggregateId(aggregateId);
        e.setAggregateType(aggregateType);
        e.setOccurredAt(Instant.now());
        e.setTraceId(traceId != null ? traceId : UUID.randomUUID().toString());
    }
}
