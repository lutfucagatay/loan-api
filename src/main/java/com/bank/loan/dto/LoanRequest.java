package com.bank.loan.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.validation.annotation.Validated;

import java.math.BigDecimal;

@Data
@Validated
@RequiredArgsConstructor
@AllArgsConstructor
@Builder
public class LoanRequest {
    @NotNull
    private Long customerId;
    @PositiveOrZero(message = "Amount must be non-negative")
    private BigDecimal amount;
    private BigDecimal interestRate;
    private Integer installments;
}
