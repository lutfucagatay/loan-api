package com.bank.loan.service;

import com.bank.loan.config.LoanConfig;
import com.bank.loan.dto.LoanRequest;
import com.bank.loan.dto.PaymentResult;
import com.bank.loan.exception.*;
import com.bank.loan.helper.PaymentCalculator;
import com.bank.loan.model.Customer;
import com.bank.loan.model.Loan;
import com.bank.loan.model.LoanInstallment;
import com.bank.loan.repository.CustomerRepo;
import com.bank.loan.repository.LoanInstallmentRepo;
import com.bank.loan.repository.LoanRepo;
import com.bank.loan.security.SecurityService;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

@Service
@RequiredArgsConstructor
@Slf4j
public class LoanService {
    private final LoanRepo loanRepo;
    private final CustomerRepo customerRepo;
    private final LoanInstallmentRepo loanInstallmentRepo;
    private final SecurityService securityService;
    private final LoanConfig loanConfig;

    @Transactional
    public Loan createLoan(LoanRequest request) {
        log.debug("LoanRequest is received: {}", request);
        if (!securityService.isAdmin()) {
            Long currentCustomerId = securityService.getCurrentCustomerId();
            log.debug("Non-admin user creating loan. Overriding customerId from {} to {}",
                    request.getCustomerId(), currentCustomerId);
            request.setCustomerId(currentCustomerId);
        } else {
            log.debug("Admin user creating loan for customer ID: {}", request.getCustomerId());
        }

        try {
            validateLoanRequest(request);
            Customer customer = getCustomer(request.getCustomerId());

            BigDecimal totalAmount = calculateTotalAmount(request.getAmount(), request.getInterestRate());
            log.debug("Calculated total loan amount: {} (principal: {}, interest rate: {})",
                    totalAmount, request.getAmount(), request.getInterestRate());
            validateCreditLimit(customer, totalAmount);
            log.debug("Credit limit validation passed. Current used: {}, limit: {}, loan amount: {}",
                    customer.getUsedCreditLimit(), customer.getCreditLimit(), totalAmount);

            Loan loan = buildAndSaveLoan(request, customer, totalAmount);
            log.info("Loan created successfully: id={}, amount={}, installments={}",
                    loan.getId(), loan.getLoanAmount(), loan.getNumberOfInstallments());
            createInstallments(loan);
            log.debug("Created {} installments for loan id={}", loan.getNumberOfInstallments(), loan.getId());

            updateCustomerCreditLimit(customer, totalAmount);
            log.info("Updated customer credit limit: customerId={}, newUsedLimit={}",
                    customer.getId(), customer.getUsedCreditLimit());


            return loan;
        } catch (Exception e) {
            log.error("Loan creation failed: {}", e.getMessage(), e);
            throw e;
        }
    }

    @Transactional
    public PaymentResult processPayment(Long loanId, BigDecimal paymentAmount) {
        log.info("Processing payment: loanId={}, amount={}", loanId, paymentAmount);

        try {
            Loan loan = getLoan(loanId);
            log.debug("Loan found: id={}, customer={}, amount={}, isPaid={}",
                    loan.getId(), loan.getCustomer().getId(), loan.getLoanAmount(), loan.isPaid());
            validatePaymentAuthorization(loan);
            log.debug("Payment authorization validated for loan id={}", loanId);

            List<LoanInstallment> installments = getPendingInstallments(loanId);
            log.debug("Found {} pending installments for loan id={}", installments.size(), loanId);
            if (installments.isEmpty()) {
                log.info("No pending installments found for loan id={}", loanId);
                throw new NoInstallmentsDueException("No pending installments within payment window");
            }
            PaymentCalculator calculator = new PaymentCalculator(paymentAmount);

            installments.forEach(installment -> {
                if (calculator.hasRemainingFunds()) {
                    calculator.processInstallment(installment, LocalDate.now());
                }
            });
            loanInstallmentRepo.saveAll(installments);
            log.debug("Saved {} updated installments", installments.size());
            updateLoanStatus(loan);

            PaymentResult paymentResult = calculator.getResult();
            log.info("Payment processed: loanId={}, paidInstallments={}, totalPaid={}, remainingFunds={}",
                    loanId, paymentResult.paidInstallments(), paymentResult.totalPaid(), paymentResult.remainingFunds());
            return paymentResult;
        } catch (Exception e) {
            log.error("Payment processing failed for loan id: {}, message: {}", loanId, e.getMessage(), e);
            throw e;
        }
    }

    public List<Loan> getLoansByCustomerId(Long customerId) {
        log.info("Retrieving loans for customer id={}", customerId);
        if (!securityService.isAdmin() && !securityService.isOwnCustomer(customerId)) {
            log.warn("Unauthorized access: User attempting to access loans for customer id={}", customerId);
            throw new UnauthorizedAccessException();
        }
        List<Loan> loans = loanRepo.findByCustomerId(customerId);
        log.debug("Found {} loans for customer id={}", loans.size(), customerId);
        return loans;
    }

    private void validateLoanRequest(LoanRequest request) {
        log.debug("Validating loan request: {}", request);
        validateInstallments(request.getInstallments());
        validateInterestRate(request.getInterestRate());
        validateCustomerExists(request.getCustomerId());
        log.debug("Loan request validation passed");
    }

    private void validateCustomerExists(Long customerId) {
        log.debug("Checking if customer exists: id={}", customerId);
        if (!customerRepo.existsById(customerId)) {
            log.warn("Customer not found: id={}", customerId);
            throw new CustomerNotFoundException(String.valueOf(customerId));
        }
    }

    private void validateInstallments(Integer installments) {
        log.debug("Validating installments: {}", installments);
        if (!loanConfig.getInstallmentsAllowed().contains(installments)) {
            log.warn("Invalid installments value: {}. Allowed values: {}",
                    installments, loanConfig.getInstallmentsAllowed());
            throw new InvalidInstallmentException(
                    "Allowed installments: " + loanConfig.getInstallmentsAllowed()
            );
        }
    }

    private void validateInterestRate(BigDecimal rate) {
        log.debug("Validating interest rate: {}", rate);
        if (rate.compareTo(loanConfig.getInterestMin()) < 0 ||
                rate.compareTo(loanConfig.getInterestMax()) > 0) {
            log.warn("Invalid interest rate: {}. Allowed range: {} to {}",
                    rate, loanConfig.getInterestMin(), loanConfig.getInterestMax());
            throw new InvalidInterestRateException(
                    String.format("Interest rate must be between %s and %s",
                            loanConfig.getInterestMin(),
                            loanConfig.getInterestMax())
            );
        }
    }

    private Customer getCustomer(Long customerId) {
        log.debug("Retrieving customer: id={}", customerId);
        return customerRepo.findById(customerId)
                .orElseThrow(() -> {
                    log.warn("Customer not found: id={}", customerId);
                    return new CustomerNotFoundException(customerId.toString());
                });
    }

    private Loan getLoan(Long loanId) {
        log.debug("Retrieving loan: id={}", loanId);
        return loanRepo.findById(loanId)
                .orElseThrow(() -> {
                    log.warn("Loan not found: id={}", loanId);
                    return new LoanNotFoundException(loanId);
                });
    }

    private BigDecimal calculateTotalAmount(BigDecimal principal, BigDecimal interestRate) {
        log.debug("Calculating total amount: principal={}, interestRate={}", principal, interestRate);
        BigDecimal totalAmount = principal.multiply(BigDecimal.ONE.add(interestRate))
                .setScale(2, RoundingMode.HALF_UP);
        log.debug("Calculated total amount: {}", totalAmount);
        return totalAmount;
    }

    private void validateCreditLimit(Customer customer, BigDecimal loanAmount) {
        log.debug("Validating credit limit: customerId={}, creditLimit={}, usedCredit={}, requestedAmount={}",
                customer.getId(), customer.getCreditLimit(), customer.getUsedCreditLimit(), loanAmount);
        BigDecimal newUsedCredit = customer.getUsedCreditLimit().add(loanAmount);
        if (newUsedCredit.compareTo(customer.getCreditLimit()) > 0) {
            log.warn("Credit limit exceeded: customerId={}, creditLimit={}, newUsedCredit={}",
                    customer.getId(), customer.getCreditLimit(), newUsedCredit);
            throw new CreditLimitExceededException("Credit limit exceeded.");
        }
    }

    private Loan buildAndSaveLoan(LoanRequest request, Customer customer, BigDecimal totalAmount) {
        log.debug("Building and saving loan: customerId={}, amount={}, installments={}",
                customer.getId(), totalAmount, request.getInstallments());
        Loan loan = loanRepo.save(Loan.builder()
                .customer(customer)
                .loanAmount(totalAmount)
                .numberOfInstallments(request.getInstallments())
                .createDate(LocalDate.now())
                .isPaid(false)
                .build());
        log.debug("Loan saved: id={}", loan.getId());
        return loan;
    }

    private void createInstallments(Loan loan) {
        log.debug("Creating installments for loan id={}", loan.getId());
        List<LoanInstallment> installments = new ArrayList<>();
        int numberOfInstallments = loan.getNumberOfInstallments();
        BigDecimal totalAmount = loan.getLoanAmount();

        BigDecimal baseInstallment = totalAmount
                .divide(BigDecimal.valueOf(numberOfInstallments), 2, RoundingMode.HALF_UP);

        BigDecimal sum = baseInstallment.multiply(BigDecimal.valueOf(numberOfInstallments - 1));
        BigDecimal lastInstallment = totalAmount.subtract(sum);

        LocalDate dueDate = LocalDate.now()
                .plusMonths(1)
                .withDayOfMonth(1);

        log.debug("Installment calculation: baseInstallment={}, lastInstallment={}, firstDueDate={}",
                baseInstallment, lastInstallment, dueDate);

        IntStream.range(0, numberOfInstallments).forEachOrdered(installmentIndex -> {
            BigDecimal amount = (installmentIndex == numberOfInstallments - 1)
                    ? lastInstallment
                    : baseInstallment;

            installments.add(LoanInstallment.builder()
                    .loan(loan)
                    .amount(amount)
                    .dueDate(dueDate.plusMonths(installmentIndex))
                    .isPaid(false)
                    .paidAmount(BigDecimal.ZERO)
                    .build());
        });

        loanInstallmentRepo.saveAll(installments);
        log.debug("Created and saved {} installments for loan id={}", installments.size(), loan.getId());
    }

    private void updateCustomerCreditLimit(Customer customer, BigDecimal loanAmount) {
        log.debug("Updating customer credit limit: id={}, currentUsed={}, loanAmount={}",
                customer.getId(), customer.getUsedCreditLimit(), loanAmount);
        customer.setUsedCreditLimit(customer.getUsedCreditLimit().add(loanAmount));
        customerRepo.save(customer);
        log.debug("Updated customer credit limit: id={}, newUsed={}",
                customer.getId(), customer.getUsedCreditLimit());
    }

    private List<LoanInstallment> getPendingInstallments(Long loanId) {
        log.debug("Retrieving pending installments for loan id={}", loanId);
        List<LoanInstallment> allInstallments = loanInstallmentRepo.findByLoanIdOrderByDueDateAsc(loanId);
        log.debug("Found {} total installments for loan id={}", allInstallments.size(), loanId);
        List<LoanInstallment> pendingInstallments = allInstallments.stream()
                .filter(installment -> !installment.getIsPaid())
                .filter(this::isWithinPaymentWindow)
                .collect(Collectors.toList());
        log.debug("Filtered to {} pending installments within payment window", pendingInstallments.size());
        return pendingInstallments;
    }

    private boolean isWithinPaymentWindow(LoanInstallment installment) {
        YearMonth currentYearMonth = YearMonth.from(LocalDate.now());
        YearMonth cutoffYearMonth = currentYearMonth.plusMonths(loanConfig.getMaxAllowedDueMonthCount());
        LocalDate cutoffDate = cutoffYearMonth.atDay(loanConfig.getDayOfPayment()); // First day of the 4th month
        boolean isWithin = installment.getDueDate().isBefore(cutoffDate);
        log.debug("Checking payment window for installment id={}: dueDate={}, cutoffDate={}, isWithin={}",
                installment.getId(), installment.getDueDate(), cutoffDate, isWithin);
        return isWithin;
    }

    private void validatePaymentAuthorization(Loan loan) {
        log.debug("Validating payment authorization for loan id={}, customer id={}",
                loan.getId(), loan.getCustomer().getId());
        if (!securityService.isAdmin() &&
                !securityService.isOwnCustomer(loan.getCustomer().getId())) {
            log.warn("Unauthorized payment attempt for loan id={}", loan.getId());
            throw new UnauthorizedPaymentException();
        }
    }

    private void updateLoanStatus(Loan loan) {
        log.debug("Updating loan status for loan id={}", loan.getId());
        List<LoanInstallment> installments = loanInstallmentRepo.findByLoanId(loan.getId());
        boolean allPaid = installments.stream().allMatch(LoanInstallment::getIsPaid);

        log.debug("Loan id={} status check: total installments={}, allPaid={}",
                loan.getId(), installments.size(), allPaid);
        if (allPaid) {
            log.info("All installments paid for loan id={}. Marking loan as paid.", loan.getId());
            loan.markAsPaid();
            Customer customer = loan.getCustomer();
            BigDecimal oldUsedCredit = customer.getUsedCreditLimit();
            customer.setUsedCreditLimit(
                    oldUsedCredit.subtract(loan.getLoanAmount())
            );
            log.info("Reducing customer (id={}) used credit from {} to {}",
                    customer.getId(), oldUsedCredit, customer.getUsedCreditLimit());
            customerRepo.save(customer);
            loanRepo.save(loan);
            log.info("Loan id={} marked as paid, customer credit limit updated", loan.getId());
        }
    }

    public List<Loan> getAllLoans() {
        log.info("Retrieving all loans");
        if (!securityService.isAdmin()) {
            log.warn("Unauthorized attempt to retrieve all loans by non-admin user");
            throw new UnauthorizedAccessException();
        }
        List<Loan> loans = loanRepo.findAll();
        log.debug("Retrieved {} loans", loans.size());
        return loans;
    }

    public List<LoanInstallment> getInstallmentsByLoanId(Long loanId) {
        log.info("Retrieving installments for loan id={}", loanId);
        List<LoanInstallment> installments = loanInstallmentRepo.findByLoanIdOrderByDueDateAsc(loanId);
        log.debug("Retrieved {} installments for loan id={}", installments.size(), loanId);
        return installments;
    }
}