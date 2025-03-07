package com.bank.loan;

import org.springframework.boot.SpringApplication;

public class TestLoanApiApplication {

	public static void main(String[] args) {
		SpringApplication.from(LoanApiApplication::main).with(TestcontainersConfiguration.class).run(args);
	}

}
