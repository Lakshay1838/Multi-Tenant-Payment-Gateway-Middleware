package com.paymentgateway.service;

import java.math.BigDecimal;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.paymentgateway.dto.CardDetailsDto;
import com.paymentgateway.dto.GatewayResponseDto;
import com.paymentgateway.dto.PaymentRequestDto;
import com.paymentgateway.strategy.PaymentProcessorStrategy;
import com.paymentgateway.strategy.PaymentStrategyFactory;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PaymentServiceTest {

    @Mock
    private PaymentStrategyFactory paymentStrategyFactory;

    @Mock
    private IdempotencyService idempotencyService;

    @Mock
    private PaymentProcessorStrategy paymentProcessorStrategy;

    @Mock
    private ObjectMapper objectMapper;

    @InjectMocks
    private PaymentService paymentService;

    @Test
    void processPaymentShouldCacheResponseWithProvidedIdempotencyKey() throws Exception {
        String idempotencyKey = "idem-abc-123";

        PaymentRequestDto requestDto = PaymentRequestDto.builder()
                .merchantId("merchant-100")
                .amount(new BigDecimal("150.25"))
                .currency("USD")
                .paymentMethod("CARD")
                .targetProvider("STRIPE")
                .cardDetails(CardDetailsDto.builder()
                        .holderName("John Doe")
                        .token("tok_abc")
                        .build())
                .build();

        GatewayResponseDto expectedResponse = GatewayResponseDto.builder()
                .gatewayTransactionId("gw-1")
                .providerTransactionId("prov-1")
                .status("SUCCESS")
                .amountProcessed(new BigDecimal("150.25"))
                .currency("USD")
                .processedAt("2026-06-09T19:00:00")
                .errorDetails(null)
                .build();

        when(paymentStrategyFactory.resolve("STRIPE")).thenReturn(paymentProcessorStrategy);
        when(paymentProcessorStrategy.processPayment(requestDto)).thenReturn(expectedResponse);
        when(objectMapper.writeValueAsString(expectedResponse)).thenReturn("{\"status\":\"SUCCESS\"}");

        GatewayResponseDto actualResponse = paymentService.processPayment(requestDto, idempotencyKey);

        assertSame(expectedResponse, actualResponse);
        assertEquals("SUCCESS", actualResponse.getStatus());
        verify(idempotencyService, times(1))
                .cacheResponse(eq(idempotencyKey), eq(requestDto.getMerchantId()), eq(200), anyString());
    }
}
