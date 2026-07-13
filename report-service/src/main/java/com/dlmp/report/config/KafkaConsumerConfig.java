package com.dlmp.report.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.listener.DefaultErrorHandler;
import org.springframework.util.backoff.FixedBackOff;

@Configuration
@Slf4j
public class KafkaConsumerConfig {

    /**
     * Retry a failed record 4 times with a 2s pause, then log and skip so a
     * poison message cannot wedge the partition.
     */
    @Bean
    public DefaultErrorHandler kafkaErrorHandler() {
        DefaultErrorHandler handler = new DefaultErrorHandler(
                (record, ex) -> log.error("[KAFKA] Giving up on record topic={} offset={} after retries: {}",
                        record.topic(), record.offset(), ex.getMessage()),
                new FixedBackOff(2000L, 4L));
        handler.setCommitRecovered(true);
        return handler;
    }
}
