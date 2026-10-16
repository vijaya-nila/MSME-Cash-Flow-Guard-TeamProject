package com.msme.cashflowguard;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Entry point. Spring Boot serves the frontend from src/main/resources/static
 * and the REST API under /api/**.
 */
@SpringBootApplication
public class CashFlowGuardApplication {

    public static void main(String[] args) {
        SpringApplication.run(CashFlowGuardApplication.class, args);
    }
}
