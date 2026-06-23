package com.paymentgateway.adapter.stripe;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
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
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;

import lombok.extern.slf4j.Slf4j;

@Service
@Slf4j
public class StripePaymentAdapter implements PaymentProcessorStrategy {

    private final WebClient stripeWebClient;
    private final TransactionRecordRepository transactionRecordRepository;

    public StripePaymentAdapter(@Qualifier("stripeWebClient") WebClient stripeWebClient,
            TransactionRecordRepository transactionRecordRepository) {
        this.stripeWebClient = stripeWebClient;
        this.transactionRecordRepository = transactionRecordRepository;
    }

    @Override
    public String getProviderName() {
        return "STRIPE";
    }

    private StripeChargeRequest buildStripeRequest(PaymentRequestDto dto) {
        long amountInCents = dto.getAmount()
                .multiply(new BigDecimal("100"))
                .setScale(0, RoundingMode.HALF_UP)
                .longValueExact();

        return new StripeChargeRequest(
                amountInCents,
                dto.getCurrency().toLowerCase(),
                dto.getCardDetails().getToken(),
                "Charge for merchant " + dto.getMerchantId());
    }

    @Override
    @CircuitBreaker(name = "stripe", fallbackMethod = "circuitBreakerFallback")
    public GatewayResponseDto processPayment(PaymentRequestDto dto) throws PaymentProcessingException {
        TransactionRecord transactionRecord = TransactionRecord.builder()
                .merchantId(dto.getMerchantId())
                .amount(dto.getAmount())
                .currency(dto.getCurrency())
                .status(TransactionStatus.PENDING)
                .build();

        transactionRecord = transactionRecordRepository.save(transactionRecord);

        log.info("Initiating Stripe charge for merchant {} amount {} {}",
                dto.getMerchantId(), dto.getAmount(), dto.getCurrency());

        StripeChargeRequest stripeChargeRequest = buildStripeRequest(dto);

        try {
            Map<String, Object> response = stripeWebClient.post()
                    .uri("/v1/charges")
                    .bodyValue(stripeChargeRequest)
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
            throw new PaymentProcessingException("Stripe API error: " + ex.getMessage(), ex);
        } catch (RuntimeException ex) {
            if (hasTimeoutCause(ex)) {
                transactionRecord.setStatus(TransactionStatus.PENDING);
                transactionRecordRepository.save(transactionRecord);
                throw new PaymentProcessingException("Stripe API timeout after 5000ms", ex);
            }

            throw new PaymentProcessingException("Stripe payment processing failed", ex);
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

    public GatewayResponseDto circuitBreakerFallback(PaymentRequestDto dto, Throwable t) {
        throw new PaymentProcessingException("Stripe circuit breaker open: " + t.getMessage(), t);
    }
}
