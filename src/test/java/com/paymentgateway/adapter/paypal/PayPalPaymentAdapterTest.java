package com.paymentgateway.adapter.paypal;

import java.lang.reflect.Method;
import java.math.BigDecimal;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.reactive.function.client.WebClient;

import com.paymentgateway.dto.CardDetailsDto;
import com.paymentgateway.dto.PaymentRequestDto;
import com.paymentgateway.repository.TransactionRecordRepository;

import static org.junit.jupiter.api.Assertions.assertEquals;

@ExtendWith(MockitoExtension.class)
class PayPalPaymentAdapterTest {

    @Mock
    private WebClient paypalWebClient;

    @Mock
    private TransactionRecordRepository transactionRecordRepository;

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
}
