package com.bank.loan.model;

import com.fasterxml.jackson.annotation.JsonBackReference;
import com.fasterxml.jackson.annotation.JsonManagedReference;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

@Entity
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Table(name = "loans")  // Explicit table name
public class Loan {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "customer_id", nullable = false)
    @JsonBackReference("customer-loans")
    private Customer customer;

    @Column(name = "loan_amount", precision = 15, scale = 2, nullable = false)
    private BigDecimal loanAmount;  // Changed from totalAmount

    @Column(name = "number_of_installment", nullable = false)  // Match requirement spelling
    private Integer numberOfInstallments;

    @Column(name = "create_date", nullable = false)
    private LocalDate createDate;

    @Column(name = "is_paid", nullable = false)
    private boolean isPaid = false;  // Use primitive boolean

    @OneToMany(mappedBy = "loan", cascade = CascadeType.ALL, orphanRemoval = true)
    @JsonManagedReference("loan-installments")
    @Builder.Default  // Ensure proper initialization
    private List<LoanInstallment> installments = new ArrayList<>();

    // Remove setter for isPaid to ensure proper encapsulation
    public void markAsPaid() {
        this.isPaid = true;
    }
}
