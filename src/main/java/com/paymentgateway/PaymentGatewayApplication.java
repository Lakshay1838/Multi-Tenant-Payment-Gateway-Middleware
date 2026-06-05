package com.paymentgateway;

import com.paymentgateway.repository.IdempotencyRecordRepository;
import com.paymentgateway.repository.TransactionRecordRepository;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;

@SpringBootApplication
public class PaymentGatewayApplication {

    public static void main(String[] args) {
        SpringApplication.run(PaymentGatewayApplication.class, args);
    }

    @Bean
    CommandLineRunner verifyRepositoryCounts(
            TransactionRecordRepository transactionRecordRepository,
            IdempotencyRecordRepository idempotencyRecordRepository) {
        return args -> {
            System.out.println("Transaction count: " + transactionRecordRepository.count());
            System.out.println("Idempotency count: " + idempotencyRecordRepository.count());
        };
    }
}
