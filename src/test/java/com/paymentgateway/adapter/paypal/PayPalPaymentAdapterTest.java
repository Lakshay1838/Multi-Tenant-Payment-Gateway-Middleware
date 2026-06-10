package com.paymentgateway.adapter.paypal;

import java.lang.reflect.Method;
import java.math.BigDecimal;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeoutException;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.web.reactive.function.client.WebClient;

import com.paymentgateway.dto.CardDetailsDto;
import com.paymentgateway.dto.GatewayResponseDto;
import com.paymentgateway.dto.PaymentRequestDto;
import com.paymentgateway.entity.TransactionRecord;
import com.paymentgateway.exception.PaymentProcessingException;
import com.paymentgateway.repository.TransactionRecordRepository;

import reactor.core.publisher.Mono;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PayPalPaymentAdapterTest {

    @Mock
    private WebClient paypalWebClient;

    @Mock
    private TransactionRecordRepository transactionRecordRepository;

        @Mock
        private WebClient.RequestBodyUriSpec requestBodyUriSpec;

        @Mock
        private WebClient.RequestBodySpec requestBodySpec;

        @Mock
        private WebClient.RequestHeadersSpec<?> requestHeadersSpec;

        @Mock
        private WebClient.ResponseSpec responseSpec;

    @InjectMocks
    private PayPalPaymentAdapter payPalPaymentAdapter;

    @Test
    void shouldKeepTwoDecimalScaleInPayPalAmountValue() throws Exception {
        PaymentRequestDto requestDto = PaymentRequestDto.builder()
                .merchantId("merchant-456")
                .amount(new BigDecimal("9.50"))
                .currency("usd")
                .paymentMethod("CARD")
                .targetProvider("PAYPAL")
                .cardDetails(CardDetailsDto.builder()
                        .holderName("John Doe")
                        .token("tok_paypal_123")
                        .build())
                .build();

        Method buildPayPalRequest = PayPalPaymentAdapter.class
                .getDeclaredMethod("buildPayPalRequest", PaymentRequestDto.class);
        buildPayPalRequest.setAccessible(true);

        PayPalOrderRequest request = (PayPalOrderRequest) buildPayPalRequest.invoke(payPalPaymentAdapter, requestDto);

        assertEquals("9.50", request.purchase_units().get(0).amount().value());
    }

        @Test
        void shouldReturnSuccessResponseWhenPayPalCallSucceeds() {
                when(paypalWebClient.post()).thenReturn(requestBodyUriSpec);
                when(requestBodyUriSpec.uri("/v2/checkout/orders")).thenReturn(requestBodySpec);
                doReturn(requestHeadersSpec).when(requestBodySpec).bodyValue(any());
                when(requestHeadersSpec.retrieve()).thenReturn(responseSpec);
                when(responseSpec.bodyToMono(any(ParameterizedTypeReference.class)))
                                .thenReturn(Mono.just(Map.of("id", "pp-order-1")));

                when(transactionRecordRepository.save(any(TransactionRecord.class))).thenAnswer(invocation -> {
                        TransactionRecord record = invocation.getArgument(0);
                        if (record.getId() == null) {
                                record.setId(UUID.randomUUID());
                        }
                        return record;
                });

                GatewayResponseDto response = payPalPaymentAdapter.processPayment(buildRequest("10.00"));

                assertEquals("SUCCESS", response.getStatus());
                assertEquals("pp-order-1", response.getProviderTransactionId());
        }

        @Test
        void shouldThrowPaymentProcessingExceptionOnTimeout() {
                when(paypalWebClient.post()).thenReturn(requestBodyUriSpec);
                when(requestBodyUriSpec.uri("/v2/checkout/orders")).thenReturn(requestBodySpec);
                doReturn(requestHeadersSpec).when(requestBodySpec).bodyValue(any());
                when(requestHeadersSpec.retrieve()).thenReturn(responseSpec);
                when(responseSpec.bodyToMono(any(ParameterizedTypeReference.class)))
                                .thenReturn(Mono.error(new RuntimeException(new TimeoutException("simulated timeout"))));

                when(transactionRecordRepository.save(any(TransactionRecord.class))).thenAnswer(invocation -> invocation.getArgument(0));

                PaymentProcessingException ex = assertThrows(PaymentProcessingException.class,
                                () -> payPalPaymentAdapter.processPayment(buildRequest("10.00")));

                assertTrue(ex.getMessage().toLowerCase().contains("timeout"));
        }

        private PaymentRequestDto buildRequest(String amount) {
                return PaymentRequestDto.builder()
                                .merchantId("merchant-456")
                                .amount(new BigDecimal(amount))
                                .currency("USD")
                                .paymentMethod("CARD")
                                .targetProvider("PAYPAL")
                                .cardDetails(CardDetailsDto.builder()
                                                .holderName("John Doe")
                                                .token("tok_paypal_123")
                                                .build())
                                .build();
        }
}
