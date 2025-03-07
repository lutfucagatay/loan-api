package com.bank.loan.exception;

public class LoanNotFoundException extends RuntimeException {
    public LoanNotFoundException(Long loanId) {
        super("Loan with ID not found: " + loanId);
    }
}
