package com.bank.loan.helper;

import com.bank.loan.dto.PaymentResult;
import com.bank.loan.model.LoanInstallment;
import lombok.extern.slf4j.Slf4j;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;

/**
 * Utility class for calculating loan payments and processing installments.
 * Handles the distribution of payment amounts across multiple installments
 * and calculates effective payments based on payment timing.
 */
@Slf4j
public class PaymentCalculator {
    private BigDecimal remainingFunds;
    private int paidInstallments = 0;
    private BigDecimal totalPaid = BigDecimal.ZERO;

    /**
     * Creates a new payment calculator with the given payment amount.
     *
     * @param paymentAmount The total payment amount to distribute across installments
     */
    public PaymentCalculator(BigDecimal paymentAmount) {
        log.debug("Initializing PaymentCalculator with amount: {}", paymentAmount);
        this.remainingFunds = paymentAmount;
    }

    /**
     * Checks if there are remaining funds available for payment.
     *
     * @return true if there are remaining funds, false otherwise
     */
    public boolean hasRemainingFunds() {
        return remainingFunds.compareTo(BigDecimal.ZERO) > 0;
    }

    /**
     * Processes payment for an installment if sufficient funds are available.
     *
     * @param installment The installment to process
     * @param paymentDate The date when the payment is made
     */
    public void processInstallment(LoanInstallment installment, LocalDate paymentDate) {
        log.debug("Processing installment id={}, amount={}, dueDate={}, with remainingFunds={}",
                installment.getId(), installment.getAmount(), installment.getDueDate(), remainingFunds);
        BigDecimal requiredPayment = calculateEffectivePayment(installment, paymentDate);

        log.debug("Calculated effective payment: {} for installment id={}",
                requiredPayment, installment.getId());
        if (remainingFunds.compareTo(requiredPayment) >= 0) {
            remainingFunds = remainingFunds.subtract(requiredPayment);
            totalPaid = totalPaid.add(requiredPayment);
            paidInstallments++;

            installment.setPaidAmount(requiredPayment);
            installment.setPaymentDate(paymentDate);
            installment.setIsPaid(true);
            log.debug("Installment id={} marked as paid. Remaining funds: {}, Total paid: {}",
                    installment.getId(), remainingFunds, totalPaid);
        } else {
            log.debug("Insufficient funds to pay installment id={}. Needed: {}, Available: {}",
                    installment.getId(), requiredPayment, remainingFunds);
        }
    }

    /**
     * Calculates the effective payment amount for an installment based on payment timing.
     * Applies adjustments for early or late payments.
     *
     * @param installment The installment to calculate payment for
     * @param paymentDate The date when the payment is made
     * @return The effective payment amount
     */
    private BigDecimal calculateEffectivePayment(LoanInstallment installment, LocalDate paymentDate) {
        long daysDifference = ChronoUnit.DAYS.between(paymentDate, installment.getDueDate());
        log.debug("Days difference for installment id={}: {} days", installment.getId(), daysDifference);
        BigDecimal adjustment = installment.getAmount()
                .multiply(BigDecimal.valueOf(0.001))
                .multiply(BigDecimal.valueOf(Math.abs(daysDifference)));

        log.debug("Payment adjustment: {}", adjustment);

        BigDecimal effectivePayment = daysDifference > 0
                ? installment.getAmount().subtract(adjustment)
                : installment.getAmount().add(adjustment);

        log.debug("Effective payment amount: {} (original: {}, adjustment: {})",
                effectivePayment, installment.getAmount(), adjustment);

        return effectivePayment;
    }

    /**
     * Gets the result of the payment processing.
     *
     * @return A PaymentResult object containing payment statistics
     */
    public PaymentResult getResult() {
        log.debug("PaymentCalculator result: paidInstallments={}, totalPaid={}, remainingFunds={}",
                paidInstallments, totalPaid, remainingFunds);
        return new PaymentResult(
                paidInstallments,
                totalPaid,
                remainingFunds.equals(BigDecimal.ZERO),
                remainingFunds
        );
    }
}