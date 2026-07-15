package com.dlmp.notification.service;

import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

/**
 * Email service — all failures are non-fatal (logged only).
 * Enable via MAIL_ENABLED=true + MAIL_PASSWORD env var.
 * Recommended provider: Brevo (300 emails/day free, no credit card).
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class EmailService {

    private final JavaMailSender mailSender;

    @Value("${dlmp.mail.from:noreply@dlmp.com}")
    private String fromAddress;

    @Value("${dlmp.mail.from-name:DLMP Platform}")
    private String fromName;

    @Value("${dlmp.mail.enabled:false}")
    private boolean mailEnabled;

    // ─── Core sender — NEVER throws, failures are logged only ─────────────────
    // @Async lives on the public sendXxxEmail entry points below (not here):
    // Spring's async proxy only intercepts external calls, and those methods
    // call this one from within the same bean (self-invocation bypasses the proxy).

    public void sendHtml(String to, String subject, String htmlBody) {
        if (!mailEnabled) {
            log.debug("[EMAIL DISABLED] Would send to={} subject={}", to, subject);
            return;
        }
        if (to == null || to.isBlank()) {
            log.warn("[EMAIL] Skipping — recipient is empty");
            return;
        }
        try {
            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");
            helper.setFrom(fromAddress, fromName);
            helper.setTo(to);
            helper.setSubject(subject);
            helper.setText(htmlBody, true);
            mailSender.send(message);
            log.info("[EMAIL] Sent | to={} | subject={}", to, subject);
        } catch (MessagingException | java.io.UnsupportedEncodingException ex) {
            log.error("[EMAIL] Delivery failed | to={} | subject={} | error={}", to, subject, ex.getMessage());
            // intentionally NOT rethrowing — email failure must never crash the service
        } catch (Exception ex) {
            log.error("[EMAIL] Unexpected failure | to={} | error={}", to, ex.getMessage());
        }
    }

    // ─── Convenience methods called by DlmpEventConsumer ──────────────────────
    // @Async here (not on sendHtml): these are the methods external callers
    // invoke through the Spring proxy, so this is where the async boundary
    // actually takes effect.

    @Async("emailExecutor")
    public void sendWelcomeEmail(String email, String firstName) {
        sendHtml(email, "Welcome to DLMP! 🎉", welcomeTemplate(firstName));
    }

    @Async("emailExecutor")
    public void sendOtpEmail(String email, String firstName, String code, String purpose) {
        if (email == null || code == null) return;
        sendHtml(email, "Your DLMP verification code: " + code, otpTemplate(firstName, code, purpose));
    }

    @Async("emailExecutor")
    public void sendLoanApplicationEmail(com.dlmp.common.event.LoanEvent event) {
        if (event.getUserEmail() == null) return;
        sendHtml(event.getUserEmail(),
            "Loan Application Received — " + event.getLoanNumber(),
            loanAppliedTemplate(nameFromEmail(event.getUserEmail()), event.getLoanNumber(),
                str(event.getPrincipalAmount()), str(event.getEmiAmount())));
    }

    @Async("emailExecutor")
    public void sendLoanApprovedEmail(com.dlmp.common.event.LoanEvent event) {
        if (event.getUserEmail() == null) return;
        sendHtml(event.getUserEmail(),
            "Loan Approved — " + event.getLoanNumber(),
            loanApprovedTemplate(nameFromEmail(event.getUserEmail()),
                event.getLoanNumber(), str(event.getPrincipalAmount())));
    }

    @Async("emailExecutor")
    public void sendLoanRejectedEmail(com.dlmp.common.event.LoanEvent event) {
        if (event.getUserEmail() == null) return;
        sendHtml(event.getUserEmail(),
            "Loan Application Update — " + event.getLoanNumber(),
            loanRejectedTemplate(nameFromEmail(event.getUserEmail()), event.getLoanNumber(),
                event.getRejectionReason() != null ? event.getRejectionReason() : "Please contact support"));
    }

    @Async("emailExecutor")
    public void sendLoanDisbursedEmail(com.dlmp.common.event.LoanEvent event) {
        if (event.getUserEmail() == null) return;
        sendHtml(event.getUserEmail(),
            "Loan Disbursed — " + event.getLoanNumber(),
            loanDisbursedTemplate(nameFromEmail(event.getUserEmail()),
                event.getLoanNumber(), str(event.getPrincipalAmount()), "As per your repayment schedule"));
    }

    @Async("emailExecutor")
    public void sendPaymentConfirmationEmail(com.dlmp.common.event.PaymentEvent event) {
        if (event.getUserEmail() == null) return;
        sendHtml(event.getUserEmail(),
            "Payment Received — " + event.getPaymentReference(),
            paymentReceivedTemplate(nameFromEmail(event.getUserEmail()),
                event.getPaymentReference() != null ? event.getPaymentReference() : "N/A",
                str(event.getAmount()),
                event.getLoanId() != null ? event.getLoanId() : "N/A"));
    }

    private String str(Object val) {
        return val != null ? val.toString() : "N/A";
    }

    private String nameFromEmail(String email) {
        if (email == null) return "Customer";
        int at = email.indexOf('@');
        return at > 0 ? email.substring(0, at) : email;
    }

    // ─── HTML Templates ────────────────────────────────────────────────────────

    public String welcomeTemplate(String firstName) {
        return html("""
            <h2>Welcome to DLMP, %s! 🎉</h2>
            <p>Your account has been created successfully.</p>
            <p>You can now apply for loans, track repayments, and manage your financial journey.</p>
            """.formatted(firstName));
    }

    public String otpTemplate(String firstName, String code, String purpose) {
        String purposeLabel = "LIMIT_INCREASE".equals(purpose)
                ? "requesting a credit limit increase"
                : "submitting a loan application";
        return html("""
            <h2>Verification code</h2>
            <p>Hi <strong>%s</strong>, use this code to confirm you're %s:</p>
            <p style="font-size:32px;font-weight:700;letter-spacing:6px;text-align:center;
                      padding:16px;background:#eef2ff;border-radius:8px;color:#1a56db">%s</p>
            <p>This code expires in 5 minutes. If you didn't request this, you can ignore this email.</p>
            """.formatted(firstName, purposeLabel, code));
    }

    public String loanAppliedTemplate(String firstName, String loanNumber, String amount, String emi) {
        return html("""
            <h2>Loan Application Received ✅</h2>
            <p>Hi <strong>%s</strong>, your loan application has been submitted.</p>
            <table style="border-collapse:collapse;width:100%%">
              <tr><td style="padding:8px;border:1px solid #ddd"><strong>Loan</strong></td><td style="padding:8px;border:1px solid #ddd">%s</td></tr>
              <tr><td style="padding:8px;border:1px solid #ddd"><strong>Amount</strong></td><td style="padding:8px;border:1px solid #ddd">₹%s</td></tr>
              <tr><td style="padding:8px;border:1px solid #ddd"><strong>Est. EMI</strong></td><td style="padding:8px;border:1px solid #ddd">₹%s/month</td></tr>
            </table>
            """.formatted(firstName, loanNumber, amount, emi));
    }

    public String loanApprovedTemplate(String firstName, String loanNumber, String amount) {
        return html("""
            <h2 style="color:#16a34a">Loan Approved! 🎊</h2>
            <p>Hi <strong>%s</strong>, your loan <strong>%s</strong> for ₹%s has been approved.</p>
            """.formatted(firstName, loanNumber, amount));
    }

    public String loanRejectedTemplate(String firstName, String loanNumber, String reason) {
        return html("""
            <h2 style="color:#dc2626">Loan Application Update</h2>
            <p>Hi <strong>%s</strong>, your loan <strong>%s</strong> could not be approved.</p>
            <p><strong>Reason:</strong> %s</p>
            """.formatted(firstName, loanNumber, reason));
    }

    public String loanDisbursedTemplate(String firstName, String loanNumber, String amount, String emiDate) {
        return html("""
            <h2 style="color:#1a56db">Loan Disbursed! 💰</h2>
            <p>Hi <strong>%s</strong>, your loan <strong>%s</strong> of ₹%s has been disbursed.</p>
            <p>First EMI due: <strong>%s</strong>.</p>
            """.formatted(firstName, loanNumber, amount, emiDate));
    }

    public String paymentReceivedTemplate(String firstName, String payRef, String amount, String loanNumber) {
        return html("""
            <h2 style="color:#16a34a">Payment Received ✅</h2>
            <p>Hi <strong>%s</strong>, we received your payment of ₹%s (Ref: %s, Loan: %s).</p>
            """.formatted(firstName, amount, payRef, loanNumber));
    }

    private String html(String body) {
        return """
            <!DOCTYPE html><html><body style="font-family:Arial,sans-serif;max-width:600px;margin:auto;padding:24px">
              <div style="background:#1a56db;padding:20px;border-radius:8px 8px 0 0;text-align:center">
                <h1 style="color:#fff;margin:0">DLMP Platform</h1>
              </div>
              <div style="background:#f9fafb;padding:24px;border:1px solid #e5e7eb;border-radius:0 0 8px 8px">%s</div>
            </body></html>
            """.formatted(body);
    }
}
