package com.dlmp.loan.repository;

import com.dlmp.loan.domain.entity.EmiSchedule;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface EmiScheduleRepository extends JpaRepository<EmiSchedule, String> {

    List<EmiSchedule> findByLoanIdOrderByInstallmentNumber(String loanId);

    @Query("SELECT e FROM EmiSchedule e WHERE e.loan.id = :loanId AND e.status = 'PENDING' AND e.dueDate < CURRENT_DATE ORDER BY e.dueDate")
    List<EmiSchedule> findOverdueByLoanId(String loanId);

    @Query("SELECT e FROM EmiSchedule e WHERE e.loan.id = :loanId AND e.installmentNumber = :num")
    Optional<EmiSchedule> findByLoanIdAndInstallmentNumber(String loanId, int num);

    long countByLoanIdAndStatus(String loanId, String status);

    @Query("SELECT e FROM EmiSchedule e WHERE e.status = 'PENDING' AND e.dueDate = CURRENT_DATE")
    List<EmiSchedule> findEmisDueToday();
}
