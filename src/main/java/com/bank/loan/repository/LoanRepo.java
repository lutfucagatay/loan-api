package com.bank.loan.repository;

import com.bank.loan.model.Loan;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface LoanRepo extends JpaRepository<Loan, Long> {
    List<Loan> findByCustomerId(Long customerId);
}
