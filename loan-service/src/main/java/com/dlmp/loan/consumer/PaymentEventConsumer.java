package com.dlmp.loan.consumer;

import com.dlmp.common.event.PaymentEvent;
import com.dlmp.loan.service.command.LoanRepaymentService;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Component;

/**
 * Consumes payment events so loan state (EMI schedule, outstanding principal,
 * closure) reflects repayments. Errors are re-thrown so the container error
 * handler retries with backoff before skipping.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class PaymentEventConsumer {

    private final LoanRepaymentService repaymentService;
    private final ObjectMapper objectMapper;

    @KafkaListener(topics = "dlmp.payment.events", groupId = "loan-service-group")
    public void onPaymentEvent(@Payload String payload,
                               @Header(KafkaHeaders.OFFSET) long offset) {
        try {
            PaymentEvent event = objectMapper.readValue(payload, PaymentEvent.class);
            if ("PAYMENT_COMPLETED".equals(event.getEventType())) {
                repaymentService.applyPayment(event);
            }
        } catch (Exception e) {
            log.error("[KAFKA] Failed payment event at offset={}: {}", offset, e.getMessage());
            throw new RuntimeException(e);
        }
    }
}
