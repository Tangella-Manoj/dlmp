package com.dlmp.payment.repository;

import com.dlmp.payment.domain.entity.Payment;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface PaymentRepository extends JpaRepository<Payment, String> {
    Optional<Payment> findByPaymentReference(String reference);
    Optional<Payment> findByIdempotencyKey(String key);
    Page<Payment> findByLoanId(String loanId, Pageable pageable);
    Page<Payment> findByUserId(String userId, Pageable pageable);
}
