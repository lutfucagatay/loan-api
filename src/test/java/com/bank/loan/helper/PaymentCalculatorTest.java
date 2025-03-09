package com.bank.loan.helper;

import com.bank.loan.dto.PaymentResult;
import com.bank.loan.model.LoanInstallment;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.*;

class PaymentCalculatorTest {

    private PaymentCalculator paymentCalculator;
    private BigDecimal initialPaymentAmount;

    @BeforeEach
    void setUp() {
        initialPaymentAmount = new BigDecimal("1000.00");
        paymentCalculator = new PaymentCalculator(initialPaymentAmount);
    }

    @Test
    void testHasRemainingFunds_initialAmount() {
        assertTrue(paymentCalculator.hasRemainingFunds());
    }

    @Test
    void testHasRemainingFunds_noRemainingFunds() {
        LoanInstallment installment = new LoanInstallment();
        installment.setId(1L);
        installment.setAmount(initialPaymentAmount);
        installment.setDueDate(LocalDate.now());

        paymentCalculator.processInstallment(installment, LocalDate.now());
        assertFalse(paymentCalculator.hasRemainingFunds());
    }

    @Test
    void testProcessInstallment_sufficientFunds() {
        LoanInstallment installment = new LoanInstallment();
        installment.setId(1L);
        installment.setAmount(new BigDecimal(500).setScale(2, RoundingMode.HALF_UP));
        installment.setDueDate(LocalDate.now());

        paymentCalculator.processInstallment(installment, LocalDate.now());

        assertTrue(installment.getIsPaid());
        assertEquals(new BigDecimal(500).setScale(2, RoundingMode.HALF_UP), installment.getPaidAmount().setScale(2, RoundingMode.HALF_UP));
        assertEquals(LocalDate.now(), installment.getPaymentDate());
        assertEquals(new BigDecimal(500).setScale(2, RoundingMode.HALF_UP), paymentCalculator.getResult().remainingFunds().setScale(2, RoundingMode.HALF_UP));
        assertEquals(1, paymentCalculator.getResult().paidInstallments());
        assertEquals(new BigDecimal(500).setScale(2, RoundingMode.HALF_UP), paymentCalculator.getResult().totalPaid().setScale(2, RoundingMode.HALF_UP));
    }

    @Test
    void testProcessInstallment_insufficientFunds() {
        LoanInstallment installment = new LoanInstallment();
        installment.setId(1L);
        installment.setAmount(new BigDecimal("1500").setScale(2, RoundingMode.HALF_UP));
        installment.setDueDate(LocalDate.now());

        paymentCalculator.processInstallment(installment, LocalDate.now());

        assertFalse(installment.getIsPaid());
        assertNull(installment.getPaymentDate());
        assertEquals(initialPaymentAmount, paymentCalculator.getResult().remainingFunds());
        assertEquals(0, paymentCalculator.getResult().paidInstallments());
        assertEquals(BigDecimal.ZERO, paymentCalculator.getResult().totalPaid());
    }

    @Test
    void testCalculateEffectivePayment_earlyPayment() {
        LoanInstallment installment = new LoanInstallment();
        installment.setId(1L);
        installment.setAmount(new BigDecimal("1000").setScale(2, RoundingMode.HALF_UP));
        LocalDate dueDate = LocalDate.now().plusDays(10);
        installment.setDueDate(dueDate);

        BigDecimal effectivePayment = paymentCalculator.calculateEffectivePayment(installment, LocalDate.now());
        assertTrue(effectivePayment.compareTo(installment.getAmount()) < 0);
    }

    @Test
    void testCalculateEffectivePayment_latePayment() {
        LoanInstallment installment = new LoanInstallment();
        installment.setId(1L);
        installment.setAmount(new BigDecimal("1000").setScale(2, RoundingMode.HALF_UP));
        LocalDate dueDate = LocalDate.now().minusDays(10);
        installment.setDueDate(dueDate);

        BigDecimal effectivePayment = paymentCalculator.calculateEffectivePayment(installment, LocalDate.now());
        assertTrue(effectivePayment.compareTo(installment.getAmount()) > 0);
    }

    @Test
    void testCalculateEffectivePayment_onTimePayment() {
        LoanInstallment installment = new LoanInstallment();
        installment.setId(1L);
        installment.setAmount(new BigDecimal("1000").setScale(2, RoundingMode.HALF_UP));
        LocalDate dueDate = LocalDate.now();
        installment.setDueDate(dueDate);

        BigDecimal effectivePayment = paymentCalculator.calculateEffectivePayment(installment, LocalDate.now());
        assertEquals(installment.getAmount().setScale(2, RoundingMode.HALF_UP), effectivePayment.setScale(2, RoundingMode.HALF_UP));
    }

    @Test
    void testGetResult_allPaid() {
        LoanInstallment installment = new LoanInstallment();
        installment.setId(1L);
        installment.setAmount(initialPaymentAmount);
        installment.setDueDate(LocalDate.now());

        paymentCalculator.processInstallment(installment, LocalDate.now());
        PaymentResult result = paymentCalculator.getResult();

        assertEquals(1, result.paidInstallments());
        assertEquals(initialPaymentAmount.setScale(2, RoundingMode.HALF_UP), result.totalPaid().setScale(2, RoundingMode.HALF_UP));
        assertTrue(result.isLoanPaid());
        assertEquals(BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP), result.remainingFunds().setScale(2, RoundingMode.HALF_UP));
    }

    @Test
    void testGetResult_partialPaid() {
        LoanInstallment installment = new LoanInstallment();
        installment.setId(1L);
        installment.setAmount(new BigDecimal("500").setScale(2, RoundingMode.HALF_UP));
        installment.setDueDate(LocalDate.now());

        paymentCalculator.processInstallment(installment, LocalDate.now());
        PaymentResult result = paymentCalculator.getResult();

        assertEquals(1, result.paidInstallments());
        assertEquals(new BigDecimal("500").setScale(2, RoundingMode.HALF_UP), result.totalPaid().setScale(2, RoundingMode.HALF_UP));
        assertFalse(result.isLoanPaid());
        assertEquals(new BigDecimal("500").setScale(2, RoundingMode.HALF_UP), result.remainingFunds().setScale(2, RoundingMode.HALF_UP));
    }

    @Test
    void testMultipleInstallments() {
        LoanInstallment installment1 = new LoanInstallment();
        installment1.setId(1L);
        installment1.setAmount(new BigDecimal("500").setScale(2, RoundingMode.HALF_UP));
        installment1.setDueDate(LocalDate.now());

        LoanInstallment installment2 = new LoanInstallment();
        installment2.setId(2L);
        installment2.setAmount(new BigDecimal("250").setScale(2, RoundingMode.HALF_UP));
        installment2.setDueDate(LocalDate.now().plusDays(1));

        paymentCalculator.processInstallment(installment1, LocalDate.now());
        paymentCalculator.processInstallment(installment2, LocalDate.now().plusDays(1));

        PaymentResult result = paymentCalculator.getResult();

        assertEquals(2, result.paidInstallments());
        assertEquals(new BigDecimal("750").setScale(2, RoundingMode.HALF_UP), result.totalPaid().setScale(2, RoundingMode.HALF_UP));
        assertFalse(result.isLoanPaid());
        assertEquals(new BigDecimal("250").setScale(2, RoundingMode.HALF_UP), result.remainingFunds().setScale(2, RoundingMode.HALF_UP));
    }
}