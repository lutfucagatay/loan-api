package com.bank.loan.dto;

import java.math.BigDecimal;

public record PaymentResult(
        int paidInstallments,
        BigDecimal totalPaid,
        boolean isLoanPaid
) {
}
