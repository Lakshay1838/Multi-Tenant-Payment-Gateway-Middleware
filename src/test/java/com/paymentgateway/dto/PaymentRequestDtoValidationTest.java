package com.paymentgateway.dto;

import java.math.BigDecimal;
import java.util.Set;

import org.junit.jupiter.api.Test;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PaymentRequestDtoValidationTest {

    @Test
    void shouldReturnAmountViolationForNegativeAmount() {
        Validator validator = Validation.buildDefaultValidatorFactory().getValidator();

        PaymentRequestDto request = PaymentRequestDto.builder()
                .merchantId("merchant-1")
                .amount(new BigDecimal("-1.00"))
                .currency("USD")
                .paymentMethod("CARD")
                .targetProvider("STRIPE")
                .cardDetails(CardDetailsDto.builder()
                        .holderName("John Doe")
                        .token("tok_123")
                        .build())
                .build();

        Set<ConstraintViolation<PaymentRequestDto>> violations = validator.validate(request);

        assertFalse(violations.isEmpty());
        assertTrue(violations.stream().anyMatch(v -> "amount".equals(v.getPropertyPath().toString())));
    }
}
