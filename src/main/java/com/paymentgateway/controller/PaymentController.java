package com.paymentgateway.controller;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.paymentgateway.dto.GatewayResponseDto;
import com.paymentgateway.dto.PaymentRequestDto;
import com.paymentgateway.service.PaymentService;

import lombok.extern.slf4j.Slf4j;

@RestController
@RequestMapping("/api/v1")
@Slf4j
public class PaymentController {

    private final PaymentService paymentService;

    public PaymentController(PaymentService paymentService) {
        this.paymentService = paymentService;
    }

    @PostMapping("/payments")
    @ResponseStatus(HttpStatus.OK)
    public ResponseEntity<GatewayResponseDto> processPayment(
            @Valid @RequestBody PaymentRequestDto requestDto,
            HttpServletRequest request) {
        String idempotencyKey = (String) request.getAttribute("IDEMPOTENCY_KEY");
        log.info("Received payment API request for merchant {}", requestDto.getMerchantId());

        GatewayResponseDto response = paymentService.processPayment(requestDto, idempotencyKey);
        return ResponseEntity.ok(response);
    }
}
