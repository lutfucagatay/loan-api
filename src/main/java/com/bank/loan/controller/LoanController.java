package com.bank.loan.controller;

import com.bank.loan.dto.LoanRequest;
import com.bank.loan.dto.PaymentResult;
import com.bank.loan.model.Loan;
import com.bank.loan.model.LoanInstallment;
import com.bank.loan.service.LoanService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.List;

@RestController
@RequestMapping("/loans")
@RequiredArgsConstructor
public class LoanController {
    private final LoanService loanService;

    @PostMapping
    @PreAuthorize("hasRole('ADMIN') or @securityService.isOwnCustomer(#request.customerId)")
    public ResponseEntity<Loan> createLoan(@RequestBody LoanRequest request) {
        return ResponseEntity.ok(loanService.createLoan(request));
    }

    @GetMapping("/customer/{customerId}")
    @PreAuthorize("hasRole('ADMIN') or @securityService.isOwnCustomer(#customerId)")
    public ResponseEntity<List<Loan>> getLoansByCustomer(@PathVariable Long customerId) {
        return ResponseEntity.ok(loanService.getLoansByCustomerId(customerId));
    }

    @GetMapping
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<List<Loan>> getAllLoans() {
        return ResponseEntity.ok(loanService.getAllLoans());
    }

    @PostMapping("/{loanId}/pay")
    @PreAuthorize("hasRole('ADMIN') or @securityService.hasAccessToLoan(#loanId)")
    public ResponseEntity<PaymentResult> payLoan(@PathVariable Long loanId, @RequestParam BigDecimal amount) {
        return ResponseEntity.ok(loanService.processPayment(loanId, amount));
    }

    @GetMapping("/{loanId}/installments")
    @PreAuthorize("hasRole('ADMIN') or @securityService.hasAccessToLoan(#loanId)")
    public ResponseEntity<List<LoanInstallment>> getInstallments(@PathVariable Long loanId) {
        return ResponseEntity.ok(loanService.getInstallmentsByLoanId(loanId));
    }
}
