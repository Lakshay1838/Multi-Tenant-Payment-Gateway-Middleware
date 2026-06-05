package com.paymentgateway.adapter.stripe;

import java.math.BigDecimal;
import java.util.Map;
import java.util.UUID;

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
import com.paymentgateway.repository.TransactionRecordRepository;

import reactor.core.publisher.Mono;

import static org.junit.jupiter.api.Assertions.assertEquals;
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
    void shouldConvertAmountToCentsForStripeRequest() {
        when(stripeWebClient.post()).thenReturn(requestBodyUriSpec);
        when(requestBodyUriSpec.uri("/v1/charges")).thenReturn(requestBodySpec);
        doReturn(requestHeadersSpec).when(requestBodySpec).bodyValue(any());
        when(requestHeadersSpec.retrieve()).thenReturn(responseSpec);
        when(responseSpec.bodyToMono(any(ParameterizedTypeReference.class)))
                .thenReturn(Mono.just(Map.of("id", "ch_test_123")));

        when(transactionRecordRepository.save(any(TransactionRecord.class))).thenAnswer(invocation -> {
            TransactionRecord record = invocation.getArgument(0);
            if (record.getId() == null) {
                record.setId(UUID.randomUUID());
            }
            return record;
        });

        PaymentRequestDto requestDto = PaymentRequestDto.builder()
                .merchantId("merchant-123")
                .amount(new BigDecimal("250.75"))
                .currency("USD")
                .paymentMethod("CARD")
                .targetProvider("STRIPE")
                .cardDetails(CardDetailsDto.builder()
                        .holderName("Jane Doe")
                        .token("tok_stripe_123")
                        .build())
                .build();

        stripePaymentAdapter.processPayment(requestDto);

        ArgumentCaptor<StripeChargeRequest> captor = ArgumentCaptor.forClass(StripeChargeRequest.class);
        verify(requestBodySpec).bodyValue(captor.capture());

        StripeChargeRequest stripeChargeRequest = captor.getValue();
        assertEquals(25075L, stripeChargeRequest.amount());
        assertEquals("usd", stripeChargeRequest.currency());
        assertEquals("tok_stripe_123", stripeChargeRequest.source());
        assertEquals("Charge for merchant merchant-123", stripeChargeRequest.description());

        verify(requestBodyUriSpec).uri(eq("/v1/charges"));
    }
}
