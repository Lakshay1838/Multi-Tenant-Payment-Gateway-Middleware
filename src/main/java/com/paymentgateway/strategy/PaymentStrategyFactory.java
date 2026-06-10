package com.paymentgateway.strategy;

import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;

import com.paymentgateway.exception.PaymentProcessingException;

import lombok.extern.slf4j.Slf4j;

@Service
@Slf4j
public class PaymentStrategyFactory {

    private final Map<String, PaymentProcessorStrategy> strategyMap;

    public PaymentStrategyFactory(List<PaymentProcessorStrategy> strategies) {
        this.strategyMap = strategies.stream()
            .sorted(Comparator.comparing(
                (PaymentProcessorStrategy strategy) -> strategy.getProviderName().toUpperCase()).reversed())
                .collect(Collectors.toMap(
                        strategy -> strategy.getProviderName().toUpperCase(),
                strategy -> strategy,
                (existing, replacement) -> existing,
                LinkedHashMap::new));

        log.info("Registered payment strategies: {}", strategyMap.keySet());
    }

    public PaymentProcessorStrategy resolve(String providerName) {
        PaymentProcessorStrategy strategy = strategyMap.get(providerName.toUpperCase());
        if (strategy == null) {
            throw new PaymentProcessingException("Unsupported payment provider: " + providerName);
        }

        log.debug("Resolved strategy for provider: {}", providerName);
        return strategy;
    }
}
