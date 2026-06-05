package com.paymentgateway.adapter.paypal;

import java.math.RoundingMode;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeoutException;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;

import com.paymentgateway.dto.GatewayResponseDto;
import com.paymentgateway.dto.PaymentRequestDto;
import com.paymentgateway.entity.TransactionRecord;
import com.paymentgateway.entity.TransactionStatus;
import com.paymentgateway.exception.PaymentProcessingException;
import com.paymentgateway.repository.TransactionRecordRepository;
import com.paymentgateway.strategy.PaymentProcessorStrategy;

import lombok.extern.slf4j.Slf4j;

@Service
@Slf4j
public class PayPalPaymentAdapter implements PaymentProcessorStrategy {

    private final WebClient paypalWebClient;
    private final TransactionRecordRepository transactionRecordRepository;

    public PayPalPaymentAdapter(@Qualifier("paypalWebClient") WebClient paypalWebClient,
            TransactionRecordRepository transactionRecordRepository) {
        this.paypalWebClient = paypalWebClient;
        this.transactionRecordRepository = transactionRecordRepository;
    }

    @Override
    public String getProviderName() {
        return "PAYPAL";
    }

    private PayPalOrderRequest buildPayPalRequest(PaymentRequestDto dto) {
        String value = dto.getAmount().setScale(2, RoundingMode.HALF_UP).toPlainString();
        Amount amount = new Amount(dto.getCurrency().toUpperCase(), value);
        PurchaseUnit unit = new PurchaseUnit(amount, "Payment from merchant " + dto.getMerchantId());
        return new PayPalOrderRequest("CAPTURE", List.of(unit));
    }

    @Override
    public GatewayResponseDto processPayment(PaymentRequestDto dto) throws PaymentProcessingException {
        TransactionRecord transactionRecord = TransactionRecord.builder()
                .merchantId(dto.getMerchantId())
                .amount(dto.getAmount())
                .currency(dto.getCurrency())
                .status(TransactionStatus.PENDING)
                .build();

        transactionRecord = transactionRecordRepository.save(transactionRecord);

        log.info("Initiating PayPal order for merchant {} amount {} {}",
                dto.getMerchantId(), dto.getAmount(), dto.getCurrency());

        PayPalOrderRequest payPalOrderRequest = buildPayPalRequest(dto);

        try {
            Map<String, Object> response = paypalWebClient.post()
                    .uri("/v2/checkout/orders")
                    .bodyValue(payPalOrderRequest)
                    .retrieve()
                    .bodyToMono(new ParameterizedTypeReference<Map<String, Object>>() {
                    })
                    .timeout(Duration.ofMillis(5000))
                    .block();

            String providerTransactionId = response != null && response.get("id") != null
                    ? response.get("id").toString()
                    : null;

            transactionRecord.setProviderTransactionId(providerTransactionId);
            transactionRecord.setStatus(TransactionStatus.SUCCESS);
            transactionRecord = transactionRecordRepository.save(transactionRecord);

            return GatewayResponseDto.builder()
                    .gatewayTransactionId(transactionRecord.getId() != null ? transactionRecord.getId().toString() : null)
                    .providerTransactionId(providerTransactionId)
                    .status(TransactionStatus.SUCCESS.name())
                    .amountProcessed(dto.getAmount())
                    .currency(dto.getCurrency())
                    .processedAt(LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME))
                    .errorDetails(null)
                    .build();
        } catch (WebClientResponseException ex) {
            transactionRecord.setStatus(TransactionStatus.FAILED);
            transactionRecordRepository.save(transactionRecord);
            throw new PaymentProcessingException("PayPal API error: " + ex.getMessage(), ex);
        } catch (RuntimeException ex) {
            if (hasTimeoutCause(ex)) {
                transactionRecord.setStatus(TransactionStatus.PENDING);
                transactionRecordRepository.save(transactionRecord);
                throw new PaymentProcessingException("PayPal API timeout after 5000ms", ex);
            }

            throw new PaymentProcessingException("PayPal payment processing failed", ex);
        }
    }

    private boolean hasTimeoutCause(Throwable throwable) {
        Throwable current = throwable;
        while (current != null) {
            if (current instanceof TimeoutException) {
                return true;
            }
            current = current.getCause();
        }
        return false;
    }
}
