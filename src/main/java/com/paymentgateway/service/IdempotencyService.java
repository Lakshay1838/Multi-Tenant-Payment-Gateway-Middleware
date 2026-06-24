package com.paymentgateway.service;

import java.time.Duration;
import java.util.Optional;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.paymentgateway.entity.IdempotencyRecord;
import com.paymentgateway.exception.PaymentProcessingException;
import com.paymentgateway.repository.IdempotencyRecordRepository;

import lombok.extern.slf4j.Slf4j;

@Service
@Slf4j
public class IdempotencyService {

    private static final String KEY_PREFIX = "idempotency:";

    private final StringRedisTemplate redisTemplate;
    private final IdempotencyRecordRepository idempotencyRecordRepository;
    private final ObjectMapper objectMapper;

    @Value("${idempotency.ttl-hours:24}")
    private long ttlHours;

    public IdempotencyService(StringRedisTemplate redisTemplate,
                              IdempotencyRecordRepository idempotencyRecordRepository,
                              ObjectMapper objectMapper) {
        this.redisTemplate = redisTemplate;
        this.idempotencyRecordRepository = idempotencyRecordRepository;
        this.objectMapper = objectMapper;
    }

    /**
     * Returns a CachedEntry with the stored httpStatus and responseBody JSON.
     */
    public Optional<CachedEntry> findCachedResponse(String idempotencyKey) {
        try {
            String cached = redisTemplate.opsForValue().get(KEY_PREFIX + idempotencyKey);
            if (cached != null) {
                log.info("Idempotency cache HIT (Redis) for key: {}", idempotencyKey);
                return Optional.of(objectMapper.readValue(cached, CachedEntry.class));
            }
        } catch (Exception e) {
            log.warn("Redis unavailable or parse error, falling back to DB for key: {}", idempotencyKey);
        }

        return idempotencyRecordRepository.findById(idempotencyKey)
                .map(record -> {
                    log.info("Idempotency cache HIT (DB) for key: {}", idempotencyKey);
                    try {
                        return objectMapper.readValue(record.getResponseBody(), CachedEntry.class);
                    } catch (Exception e) {
                        // Legacy records without status wrapper — default to 200
                        return new CachedEntry(200, record.getResponseBody());
                    }
                });
    }

    @Transactional(isolation = Isolation.SERIALIZABLE)
    public void cacheResponse(String idempotencyKey, String merchantId, int httpStatus, String responseBody) {
        try {
            CachedEntry entry = new CachedEntry(httpStatus, responseBody);
            String json = objectMapper.writeValueAsString(entry);

            try {
                redisTemplate.opsForValue().set(KEY_PREFIX + idempotencyKey, json, Duration.ofHours(ttlHours));
                log.debug("Idempotency response cached in Redis for key: {}", idempotencyKey);
            } catch (Exception e) {
                log.warn("Redis write failed for key: {}, falling back to DB only", idempotencyKey);
            }

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

    public record CachedEntry(int httpStatus, String responseBody) {}
}
