package com.bank.loan.repository;

import com.bank.loan.model.LoanInstallment;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface LoanInstallmentRepo extends JpaRepository<LoanInstallment, Long> {
    List<LoanInstallment> findByLoanIdOrderByDueDateAsc(Long loanId);

    List<LoanInstallment> findByLoanId(Long id);
}
