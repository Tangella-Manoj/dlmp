package com.dlmp.payment.repository;

import com.dlmp.payment.domain.entity.PaymentOutbox;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface PaymentOutboxRepository extends JpaRepository<PaymentOutbox, String> {

    @Query("SELECT o FROM PaymentOutbox o WHERE o.status = 'PENDING' AND o.retryCount < 5 ORDER BY o.createdAt ASC LIMIT :limit")
    List<PaymentOutbox> findPendingEvents(int limit);

    long countByStatus(String status);
}
