package com.dlmp.notification.consumer;

import com.dlmp.common.event.LoanEvent;
import com.dlmp.common.event.PaymentEvent;
import com.dlmp.common.event.UserEvent;
import com.dlmp.notification.service.EmailService;
import com.dlmp.notification.service.NotificationPersistenceService;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Component;

/**
 * Event-Driven Notification Consumer.
 * ZERO coupling to other services — only reads from Kafka.
 * If this service is down, events queue in Kafka and process on restart.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class DlmpEventConsumer {

    private final EmailService emailService;
    private final NotificationPersistenceService persistenceService;
    private final ObjectMapper objectMapper;

    @KafkaListener(topics = "dlmp.loan.events", groupId = "notification-service-group")
    public void onLoanEvent(@Payload String payload,
                            @Header(KafkaHeaders.RECEIVED_TOPIC) String topic,
                            @Header(KafkaHeaders.OFFSET) long offset) {
        log.info("[KAFKA] loan event offset={}", offset);
        try {
            LoanEvent event = objectMapper.readValue(payload, LoanEvent.class);
            handleLoan(event);
        } catch (Exception e) {
            log.error("[KAFKA] Failed loan event at offset={}: {}", offset, e.getMessage());
            // re-throw so the DefaultErrorHandler retries with backoff, then skips
            throw new RuntimeException(e);
        }
    }

    @KafkaListener(topics = "dlmp.payment.events", groupId = "notification-service-group")
    public void onPaymentEvent(@Payload String payload,
                               @Header(KafkaHeaders.OFFSET) long offset) {
        try {
            PaymentEvent event = objectMapper.readValue(payload, PaymentEvent.class);
            handlePayment(event);
        } catch (Exception e) {
            log.error("[KAFKA] Failed payment event at offset={}: {}", offset, e.getMessage());
            throw new RuntimeException(e);
        }
    }

    @KafkaListener(topics = "dlmp.user.events", groupId = "notification-service-group")
    public void onUserEvent(@Payload String payload) {
        try {
            UserEvent event = objectMapper.readValue(payload, UserEvent.class);
            if ("USER_REGISTERED".equals(event.getEventType())) {
                persistenceService.save(event.getUserId(), "Welcome to DLMP! 🎉",
                        "Your account has been created. You can now apply for loans and track repayments.", "SYSTEM");
                emailService.sendWelcomeEmail(event.getEmail(), event.getFirstName());
            }
        } catch (Exception e) {
            log.error("[KAFKA] Failed user event: {}", e.getMessage());
            // Re-throw for consistency with onLoanEvent/onPaymentEvent so the
            // configured DefaultErrorHandler retries before giving up, instead of
            // silently losing the welcome notification/email on a transient error.
            throw new RuntimeException(e);
        }
    }

    private void handleLoan(LoanEvent e) {
        switch (e.getEventType()) {
            case "LOAN_APPLICATION_SUBMITTED" -> {
                persistenceService.save(e.getUserId(), "Application Received — " + e.getLoanNumber(),
                        "Your loan application has been received and is under review.", "LOAN");
                emailService.sendLoanApplicationEmail(e);
            }
            case "LOAN_APPROVED" -> {
                persistenceService.save(e.getUserId(), "🎉 Loan Approved — " + e.getLoanNumber(),
                        "Your loan has been approved!", "LOAN");
                emailService.sendLoanApprovedEmail(e);
            }
            case "LOAN_REJECTED" -> {
                persistenceService.save(e.getUserId(), "Application Update — " + e.getLoanNumber(),
                        "Your loan application could not be approved. Reason: " + e.getRejectionReason(), "LOAN");
                emailService.sendLoanRejectedEmail(e);
            }
            case "LOAN_DISBURSED" -> {
                persistenceService.save(e.getUserId(), "💰 Loan Disbursed — " + e.getLoanNumber(),
                        "Your loan of ₹" + e.getPrincipalAmount() + " has been disbursed.", "LOAN");
                emailService.sendLoanDisbursedEmail(e);
            }
            case "LOAN_CLOSED" ->
                persistenceService.save(e.getUserId(), "🎉 Loan Closed — " + e.getLoanNumber(),
                        "Congratulations! Your loan is fully repaid and closed.", "LOAN");
            default -> log.debug("[NOTIF] Ignoring: {}", e.getEventType());
        }
    }

    private void handlePayment(PaymentEvent e) {
        switch (e.getEventType()) {
            case "PAYMENT_COMPLETED" -> {
                persistenceService.save(e.getUserId(), "✅ Payment Received — " + e.getPaymentReference(),
                        "Payment of ₹" + e.getAmount() + " received.", "PAYMENT");
                emailService.sendPaymentConfirmationEmail(e);
            }
            case "PAYMENT_FAILED" ->
                persistenceService.save(e.getUserId(), "⚠️ Payment Failed",
                        "Your payment could not be processed. Please retry.", "PAYMENT");
            default -> log.debug("[NOTIF] Ignoring: {}", e.getEventType());
        }
    }
}
