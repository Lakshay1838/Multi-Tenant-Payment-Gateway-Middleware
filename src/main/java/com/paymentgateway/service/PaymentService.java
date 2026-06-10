package com.paymentgateway.service;

import org.springframework.stereotype.Service;

import com.paymentgateway.dto.GatewayResponseDto;
import com.paymentgateway.dto.PaymentRequestDto;
import com.paymentgateway.strategy.PaymentProcessorStrategy;
import com.paymentgateway.strategy.PaymentStrategyFactory;

import lombok.extern.slf4j.Slf4j;

@Service
@Slf4j
public class PaymentService {

    private final PaymentStrategyFactory strategyFactory;
    private final IdempotencyService idempotencyService;

    public PaymentService(PaymentStrategyFactory strategyFactory, IdempotencyService idempotencyService) {
        this.strategyFactory = strategyFactory;
        this.idempotencyService = idempotencyService;
    }

    public GatewayResponseDto processPayment(PaymentRequestDto requestDto, String idempotencyKey) {
        log.info("Processing payment for merchant {} with provider {} and idempotency key {}",
                requestDto.getMerchantId(), requestDto.getTargetProvider(), idempotencyKey);

        PaymentProcessorStrategy strategy = strategyFactory.resolve(requestDto.getTargetProvider());
        GatewayResponseDto response = strategy.processPayment(requestDto);
        idempotencyService.cacheResponse(idempotencyKey, requestDto.getMerchantId(), response);

        return response;
    }
}
