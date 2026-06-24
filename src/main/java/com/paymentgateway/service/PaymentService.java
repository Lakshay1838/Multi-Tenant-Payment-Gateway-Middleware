package com.paymentgateway.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.paymentgateway.dto.GatewayResponseDto;
import com.paymentgateway.dto.PaymentRequestDto;
import com.paymentgateway.exception.PaymentProcessingException;
import com.paymentgateway.strategy.PaymentProcessorStrategy;
import com.paymentgateway.strategy.PaymentStrategyFactory;

import lombok.extern.slf4j.Slf4j;

import org.springframework.stereotype.Service;

@Service
@Slf4j
public class PaymentService {

    private final PaymentStrategyFactory strategyFactory;
    private final IdempotencyService idempotencyService;
    private final ObjectMapper objectMapper;

    public PaymentService(PaymentStrategyFactory strategyFactory,
                          IdempotencyService idempotencyService,
                          ObjectMapper objectMapper) {
        this.strategyFactory = strategyFactory;
        this.idempotencyService = idempotencyService;
        this.objectMapper = objectMapper;
    }

    public GatewayResponseDto processPayment(PaymentRequestDto requestDto, String idempotencyKey) {
        log.info("Processing payment for merchant {} with provider {} and idempotency key {}",
                requestDto.getMerchantId(), requestDto.getTargetProvider(), idempotencyKey);

        try {
            PaymentProcessorStrategy strategy = strategyFactory.resolve(requestDto.getTargetProvider());
            GatewayResponseDto response = strategy.processPayment(requestDto);
            idempotencyService.cacheResponse(idempotencyKey, requestDto.getMerchantId(),
                    200, objectMapper.writeValueAsString(response));
            return response;
        } catch (PaymentProcessingException ex) {
            // Cache the 422 error response so idempotent retries get the same result
            try {
                String errorJson = objectMapper.writeValueAsString(
                        new com.paymentgateway.dto.ErrorResponseDto(
                                "PAYMENT_PROCESSING_ERROR",
                                ex.getMessage(),
                                java.time.Instant.now().toString(),
                                422));
                idempotencyService.cacheResponse(idempotencyKey, requestDto.getMerchantId(), 422, errorJson);
            } catch (Exception ignored) {
                log.warn("Failed to cache error response for key: {}", idempotencyKey);
            }
            throw ex;
        } catch (Exception ex) {
            throw new PaymentProcessingException("Unexpected error serializing response", ex);
        }
    }
}
