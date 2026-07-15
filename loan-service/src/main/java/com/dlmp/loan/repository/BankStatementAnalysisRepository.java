package com.dlmp.loan.repository;

import com.dlmp.loan.domain.entity.BankStatementAnalysis;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface BankStatementAnalysisRepository extends JpaRepository<BankStatementAnalysis, String> {
    Optional<BankStatementAnalysis> findFirstByUserIdAndStatusOrderByCreatedAtDesc(String userId, String status);
}
