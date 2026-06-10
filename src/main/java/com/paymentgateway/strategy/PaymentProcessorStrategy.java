package com.paymentgateway.strategy;

import com.paymentgateway.dto.GatewayResponseDto;
import com.paymentgateway.dto.PaymentRequestDto;
import com.paymentgateway.exception.PaymentProcessingException;

public interface PaymentProcessorStrategy {

    String getProviderName();

    GatewayResponseDto processPayment(PaymentRequestDto requestDto) throws PaymentProcessingException;
}
