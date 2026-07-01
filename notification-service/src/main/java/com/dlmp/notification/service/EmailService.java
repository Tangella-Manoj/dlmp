package com.dlmp.notification.service;

import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.retry.annotation.Backoff;
import org.springframework.retry.annotation.Retryable;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
@Slf4j
public class EmailService {

    private final JavaMailSender mailSender;

    @Value("${dlmp.mail.from:noreply@yourdomain.com}")
    private String fromAddress;

    @Value("${dlmp.mail.from-name:DLMP Platform}")
    private String fromName;

    @Async
    @Retryable(retryFor = Exception.class, maxAttempts = 3,
               backoff = @Backoff(delay = 2000, multiplier = 2))
    public void sendHtml(String to, String subject, String htmlBody) {
        try {
            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");
            helper.setFrom(fromAddress, fromName);
            helper.setTo(to);
            helper.setSubject(subject);
            helper.setText(htmlBody, true);
            mailSender.send(message);
            log.info("Email sent | to={} | subject={}", to, subject);
        } catch (MessagingException | java.io.UnsupportedEncodingException ex) {
            log.error("Email delivery failed | to={} | error={}", to, ex.getMessage());
            throw new RuntimeException("Email failed: " + ex.getMessage(), ex);
        }
    }

    // ─── Convenience methods called by DlmpEventConsumer ──────────────────────

    public void sendWelcomeEmail(String email, String firstName) {
        sendHtml(email, "Welcome to DLMP! 🎉", welcomeTemplate(firstName));
    }

    public void sendLoanApplicationEmail(com.dlmp.common.event.LoanEvent event) {
        if (event.getUserEmail() == null) return;
        String name = nameFromEmail(event.getUserEmail());
        String body = loanAppliedTemplate(name, event.getLoanNumber(),
            event.getPrincipalAmount() != null ? event.getPrincipalAmount().toPlainString() : "N/A",
            event.getEmiAmount() != null ? event.getEmiAmount().toPlainString() : "N/A");
        sendHtml(event.getUserEmail(), "Loan Application Received — " + event.getLoanNumber(), body);
    }

    public void sendLoanApprovedEmail(com.dlmp.common.event.LoanEvent event) {
        if (event.getUserEmail() == null) return;
        String name = nameFromEmail(event.getUserEmail());
        String body = loanApprovedTemplate(name, event.getLoanNumber(),
            event.getPrincipalAmount() != null ? event.getPrincipalAmount().toPlainString() : "N/A");
        sendHtml(event.getUserEmail(), "🎊 Loan Approved — " + event.getLoanNumber(), body);
    }

    public void sendLoanRejectedEmail(com.dlmp.common.event.LoanEvent event) {
        if (event.getUserEmail() == null) return;
        String name = nameFromEmail(event.getUserEmail());
        String reason = event.getRejectionReason() != null ? event.getRejectionReason() : "Please contact support";
        String body = loanRejectedTemplate(name, event.getLoanNumber(), reason);
        sendHtml(event.getUserEmail(), "Loan Application Update — " + event.getLoanNumber(), body);
    }

    public void sendLoanDisbursedEmail(com.dlmp.common.event.LoanEvent event) {
        if (event.getUserEmail() == null) return;
        String name = nameFromEmail(event.getUserEmail());
        String body = loanDisbursedTemplate(name, event.getLoanNumber(),
            event.getPrincipalAmount() != null ? event.getPrincipalAmount().toPlainString() : "N/A",
            "As per your repayment schedule");
        sendHtml(event.getUserEmail(), "Loan Disbursed — " + event.getLoanNumber(), body);
    }

    public void sendPaymentConfirmationEmail(com.dlmp.common.event.PaymentEvent event) {
        if (event.getUserEmail() == null) return;
        String name = nameFromEmail(event.getUserEmail());
        String body = paymentReceivedTemplate(name,
            event.getPaymentReference() != null ? event.getPaymentReference() : "N/A",
            event.getAmount() != null ? event.getAmount().toPlainString() : "N/A",
            event.getLoanId() != null ? event.getLoanId() : "N/A");
        sendHtml(event.getUserEmail(), "Payment Received — " + event.getPaymentReference(), body);
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
            <br/>
            <a href="https://yourdomain.com/login" style="background:#1a56db;color:#fff;padding:12px 24px;border-radius:6px;text-decoration:none;font-weight:bold;">
              Login to Dashboard
            </a>
            """.formatted(firstName));
    }

    public String loanAppliedTemplate(String firstName, String loanNumber, String amount, String emi) {
        return html("""
            <h2>Loan Application Received ✅</h2>
            <p>Hi <strong>%s</strong>, your loan application has been submitted successfully.</p>
            <table style="border-collapse:collapse;width:100%%">
              <tr><td style="padding:8px;border:1px solid #ddd"><strong>Loan Number</strong></td><td style="padding:8px;border:1px solid #ddd">%s</td></tr>
              <tr><td style="padding:8px;border:1px solid #ddd"><strong>Amount Applied</strong></td><td style="padding:8px;border:1px solid #ddd">₹%s</td></tr>
              <tr><td style="padding:8px;border:1px solid #ddd"><strong>Estimated EMI</strong></td><td style="padding:8px;border:1px solid #ddd">₹%s/month</td></tr>
              <tr><td style="padding:8px;border:1px solid #ddd"><strong>Status</strong></td><td style="padding:8px;border:1px solid #ddd">Under Review</td></tr>
            </table>
            <p style="margin-top:16px">Our team will review your application within <strong>24–48 hours</strong>.</p>
            """.formatted(firstName, loanNumber, amount, emi));
    }

    public String loanApprovedTemplate(String firstName, String loanNumber, String amount) {
        return html("""
            <h2 style="color:#16a34a">Loan Approved! 🎊</h2>
            <p>Hi <strong>%s</strong>, congratulations! Your loan <strong>%s</strong> for
            <strong>₹%s</strong> has been approved.</p>
            <p>Disbursement will be initiated within <strong>1 business day</strong>.</p>
            """.formatted(firstName, loanNumber, amount));
    }

    public String loanRejectedTemplate(String firstName, String loanNumber, String reason) {
        return html("""
            <h2 style="color:#dc2626">Loan Application Update</h2>
            <p>Hi <strong>%s</strong>, we regret to inform you that your loan application
            <strong>%s</strong> could not be approved at this time.</p>
            <p><strong>Reason:</strong> %s</p>
            <p>You may re-apply after 90 days or contact our support team for more information.</p>
            """.formatted(firstName, loanNumber, reason));
    }

    public String loanDisbursedTemplate(String firstName, String loanNumber, String amount, String emiDate) {
        return html("""
            <h2 style="color:#1a56db">Loan Disbursed! 💰</h2>
            <p>Hi <strong>%s</strong>, your loan <strong>%s</strong> of <strong>₹%s</strong>
            has been disbursed to your registered bank account.</p>
            <p>Your first EMI is due on <strong>%s</strong>.</p>
            """.formatted(firstName, loanNumber, amount, emiDate));
    }

    public String paymentReceivedTemplate(String firstName, String payRef, String amount, String loanNumber) {
        return html("""
            <h2 style="color:#16a34a">Payment Received ✅</h2>
            <p>Hi <strong>%s</strong>, we have received your payment.</p>
            <table style="border-collapse:collapse;width:100%%">
              <tr><td style="padding:8px;border:1px solid #ddd"><strong>Payment Reference</strong></td><td style="padding:8px;border:1px solid #ddd">%s</td></tr>
              <tr><td style="padding:8px;border:1px solid #ddd"><strong>Amount Paid</strong></td><td style="padding:8px;border:1px solid #ddd">₹%s</td></tr>
              <tr><td style="padding:8px;border:1px solid #ddd"><strong>Loan</strong></td><td style="padding:8px;border:1px solid #ddd">%s</td></tr>
            </table>
            """.formatted(firstName, payRef, amount, loanNumber));
    }

    private String html(String body) {
        return """
            <!DOCTYPE html>
            <html>
            <body style="font-family:Arial,sans-serif;max-width:600px;margin:auto;padding:24px;color:#1f2937">
              <div style="background:#1a56db;padding:20px;border-radius:8px 8px 0 0;text-align:center">
                <h1 style="color:#ffffff;margin:0;font-size:22px">DLMP Platform</h1>
              </div>
              <div style="background:#f9fafb;padding:24px;border:1px solid #e5e7eb;border-top:none;border-radius:0 0 8px 8px">
                %s
              </div>
            </body>
            </html>
            """.formatted(body);
    }
}
