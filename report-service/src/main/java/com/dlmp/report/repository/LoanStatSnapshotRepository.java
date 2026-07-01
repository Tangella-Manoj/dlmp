package com.dlmp.report.repository;

import com.dlmp.report.domain.entity.LoanStatSnapshot;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.util.Optional;

@Repository
public interface LoanStatSnapshotRepository extends JpaRepository<LoanStatSnapshot, String> {
    Optional<LoanStatSnapshot> findByLoanId(String loanId);
    Page<LoanStatSnapshot> findByCurrentStatus(String status, Pageable pageable);
    long countByCurrentStatus(String status);

    @Query("SELECT COALESCE(SUM(s.disbursedAmount),0) FROM LoanStatSnapshot s WHERE s.currentStatus IN ('ACTIVE','CLOSED')")
    BigDecimal totalDisbursed();

    @Query("SELECT COALESCE(SUM(s.totalPaidAmount),0) FROM LoanStatSnapshot s")
    BigDecimal totalRecovered();
}
