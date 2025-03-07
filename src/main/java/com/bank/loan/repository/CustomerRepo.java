package com.bank.loan.repository;

import com.bank.loan.model.Customer;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface CustomerRepo extends JpaRepository<Customer, Long> {
    Optional<Customer> findByUsername(String username);

    @Query("SELECT CASE WHEN COUNT(c) > 0 THEN true ELSE false END " +
            "FROM Customer c JOIN c.loans l " +
            "WHERE c.id = :customerId AND l.id = :loanId")
    boolean existsByLoans_Id(
            @Param("customerId") Long customerId,
            @Param("loanId") Long loanId
    );
}
