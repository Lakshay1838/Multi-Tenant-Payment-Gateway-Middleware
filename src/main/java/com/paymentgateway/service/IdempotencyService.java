package com.paymentgateway.service;

import java.util.Optional;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.paymentgateway.dto.GatewayResponseDto;
import com.paymentgateway.entity.IdempotencyRecord;
import com.paymentgateway.exception.PaymentProcessingException;
import com.paymentgateway.repository.IdempotencyRecordRepository;

import lombok.extern.slf4j.Slf4j;

@Service
@Slf4j
public class IdempotencyService {

    private final IdempotencyRecordRepository idempotencyRecordRepository;
    private final ObjectMapper objectMapper;

    public IdempotencyService(IdempotencyRecordRepository idempotencyRecordRepository, ObjectMapper objectMapper) {
        this.idempotencyRecordRepository = idempotencyRecordRepository;
        this.objectMapper = objectMapper;
    }

    public Optional<String> findCachedResponse(String idempotencyKey) {
        return idempotencyRecordRepository.findById(idempotencyKey)
                .map(record -> {
                    log.info("Idempotency cache HIT for key: {}", idempotencyKey);
                    return record.getResponseBody();
                })
                .or(() -> {
                    log.debug("Idempotency cache MISS for key: {}", idempotencyKey);
                    return Optional.empty();
                });
    }

    @Transactional(isolation = Isolation.SERIALIZABLE)
    public void cacheResponse(String idempotencyKey, String merchantId, GatewayResponseDto responseDto) {
        try {
            String json = objectMapper.writeValueAsString(responseDto);
            IdempotencyRecord record = IdempotencyRecord.builder()
                    .idempotencyKey(idempotencyKey)
                    .merchantId(merchantId)
                    .responseBody(json)
                    .build();

            idempotencyRecordRepository.save(record);
        } catch (DataIntegrityViolationException e) {
            log.warn("Concurrent idempotency key write detected for key: {} — first writer wins.", idempotencyKey);
        } catch (JsonProcessingException e) {
            throw new PaymentProcessingException("Failed to serialize response for idempotency cache", e);
        }
    }
}
