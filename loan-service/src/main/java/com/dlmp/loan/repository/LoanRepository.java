package com.dlmp.loan.repository;

import com.dlmp.loan.domain.entity.Loan;
import com.dlmp.loan.domain.enums.LoanStatus;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.util.Optional;

@Repository
public interface LoanRepository extends JpaRepository<Loan, String> {

    Page<Loan> findByUserId(String userId, Pageable pageable);
    Page<Loan> findByStatus(LoanStatus status, Pageable pageable);
    long countByStatus(LoanStatus status);
    Optional<Loan> findByLoanNumber(String loanNumber);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT l FROM Loan l WHERE l.id = :id")
    Optional<Loan> findByIdWithPessimisticLock(String id);

    @Query("SELECT COALESCE(SUM(l.sanctionedAmount), 0) FROM Loan l WHERE l.status IN ('ACTIVE','CLOSED')")
    BigDecimal sumDisbursedPrincipal();

    @Query("SELECT COALESCE(SUM(l.outstandingPrincipal), 0) FROM Loan l WHERE l.status = 'ACTIVE'")
    BigDecimal sumOutstandingPrincipal();
}
