package com.paymentgateway.strategy;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.paymentgateway.adapter.paypal.PayPalPaymentAdapter;
import com.paymentgateway.adapter.stripe.StripePaymentAdapter;
import com.paymentgateway.exception.PaymentProcessingException;

import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PaymentStrategyFactoryTest {

    @Mock
    private StripePaymentAdapter stripePaymentAdapter;

    @Mock
    private PayPalPaymentAdapter payPalPaymentAdapter;

    @Test
    void resolveShouldReturnStrategyForUppercaseProvider() {
        when(stripePaymentAdapter.getProviderName()).thenReturn("STRIPE");
        when(payPalPaymentAdapter.getProviderName()).thenReturn("PAYPAL");

        PaymentStrategyFactory factory = new PaymentStrategyFactory(List.of(stripePaymentAdapter, payPalPaymentAdapter));

        PaymentProcessorStrategy resolved = factory.resolve("STRIPE");
        assertSame(stripePaymentAdapter, resolved);
    }

    @Test
    void resolveShouldBeCaseInsensitiveForProviderName() {
        when(stripePaymentAdapter.getProviderName()).thenReturn("STRIPE");
        when(payPalPaymentAdapter.getProviderName()).thenReturn("PAYPAL");

        PaymentStrategyFactory factory = new PaymentStrategyFactory(List.of(stripePaymentAdapter, payPalPaymentAdapter));

        PaymentProcessorStrategy resolved = factory.resolve("stripe");
        assertSame(stripePaymentAdapter, resolved);
    }

    @Test
    void resolveShouldThrowForUnknownProvider() {
        when(stripePaymentAdapter.getProviderName()).thenReturn("STRIPE");
        when(payPalPaymentAdapter.getProviderName()).thenReturn("PAYPAL");

        PaymentStrategyFactory factory = new PaymentStrategyFactory(List.of(stripePaymentAdapter, payPalPaymentAdapter));

        assertThrows(PaymentProcessingException.class, () -> factory.resolve("UNKNOWN"));
    }
}
