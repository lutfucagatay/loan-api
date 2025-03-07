package com.bank.loan.model;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;

@Entity
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class LoanInstallment {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne
    private Loan loan;
    private BigDecimal amount;
    private BigDecimal paidAmount = BigDecimal.valueOf(0.0);
    private LocalDate dueDate;
    private LocalDate paymentDate;
    private Boolean isPaid = false;
}
