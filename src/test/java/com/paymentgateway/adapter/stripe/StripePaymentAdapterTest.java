package com.paymentgateway.adapter.stripe;

import java.math.BigDecimal;
import java.lang.reflect.Method;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeoutException;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.web.reactive.function.client.WebClient;

import com.paymentgateway.dto.CardDetailsDto;
import com.paymentgateway.dto.PaymentRequestDto;
import com.paymentgateway.entity.TransactionRecord;
import com.paymentgateway.exception.PaymentProcessingException;
import com.paymentgateway.repository.TransactionRecordRepository;

import reactor.core.publisher.Mono;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class StripePaymentAdapterTest {

    @Mock
    private WebClient stripeWebClient;

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
    private StripePaymentAdapter stripePaymentAdapter;

    @Test
    void testAmountConversion_standardCase() throws Exception {
        PaymentRequestDto requestDto = buildRequest("9.99");

        StripeChargeRequest stripeChargeRequest = invokeBuildStripeRequest(requestDto);

        assertEquals(999L, stripeChargeRequest.amount());
    }

    @Test
    void testAmountConversion_halfUpRounding() throws Exception {
        PaymentRequestDto requestDto = buildRequest("1.005");

        StripeChargeRequest stripeChargeRequest = invokeBuildStripeRequest(requestDto);

        assertEquals(101L, stripeChargeRequest.amount());
    }

    @Test
    void testTimeoutThrowsPaymentProcessingException() {
        when(stripeWebClient.post()).thenReturn(requestBodyUriSpec);
        when(requestBodyUriSpec.uri("/v1/charges")).thenReturn(requestBodySpec);
        doReturn(requestHeadersSpec).when(requestBodySpec).bodyValue(any());
        when(requestHeadersSpec.retrieve()).thenReturn(responseSpec);
        when(responseSpec.bodyToMono(any(ParameterizedTypeReference.class)))
                .thenReturn(Mono.error(new RuntimeException(new TimeoutException("simulated timeout"))));

        when(transactionRecordRepository.save(any(TransactionRecord.class))).thenAnswer(invocation -> {
            TransactionRecord record = invocation.getArgument(0);
            if (record.getId() == null) {
                record.setId(UUID.randomUUID());
            }
            return record;
        });

        PaymentRequestDto requestDto = buildRequest("10.00");

        PaymentProcessingException ex = assertThrows(PaymentProcessingException.class,
                () -> stripePaymentAdapter.processPayment(requestDto));

        assertTrue(ex.getMessage().toLowerCase().contains("timeout"));
        verify(requestBodyUriSpec).uri(eq("/v1/charges"));
    }

    private PaymentRequestDto buildRequest(String amount) {
        return PaymentRequestDto.builder()
                .merchantId("merchant-123")
                .amount(new BigDecimal(amount))
                .currency("USD")
                .paymentMethod("CARD")
                .targetProvider("STRIPE")
                .cardDetails(CardDetailsDto.builder()
                        .holderName("Jane Doe")
                        .token("tok_stripe_123")
                        .build())
                .build();
    }

    private StripeChargeRequest invokeBuildStripeRequest(PaymentRequestDto requestDto) throws Exception {
        Method method = StripePaymentAdapter.class.getDeclaredMethod("buildStripeRequest", PaymentRequestDto.class);
        method.setAccessible(true);
        return (StripeChargeRequest) method.invoke(stripePaymentAdapter, requestDto);
    }
}
