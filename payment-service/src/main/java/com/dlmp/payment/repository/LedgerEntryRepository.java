package com.dlmp.payment.repository;

import com.dlmp.payment.domain.entity.LedgerEntry;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.util.List;

@Repository
public interface LedgerEntryRepository extends JpaRepository<LedgerEntry, String> {
    List<LedgerEntry> findByPaymentIdOrderByCreatedAt(String paymentId);
    List<LedgerEntry> findByLoanIdOrderByCreatedAt(String loanId);

    @Query("SELECT COALESCE(SUM(e.amount),0) FROM LedgerEntry e WHERE e.loanId = :loanId AND e.entryType = 'CREDIT' AND e.accountType = 'LOAN_RECEIVABLE'")
    BigDecimal sumPrincipalRecovered(String loanId);
}
