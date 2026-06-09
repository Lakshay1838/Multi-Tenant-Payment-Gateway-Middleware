package com.paymentgateway.service;

import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.paymentgateway.entity.IdempotencyRecord;
import com.paymentgateway.repository.IdempotencyRecordRepository;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class IdempotencyServiceTest {

    @Mock
    private IdempotencyRecordRepository idempotencyRecordRepository;

    @Mock
    private ObjectMapper objectMapper;

    @InjectMocks
    private IdempotencyService idempotencyService;

    @Test
    void findCachedResponseShouldReturnCachedJsonWhenKeyExists() {
        String idempotencyKey = "idem-key-123";
        String expectedJson = "{\"status\":\"SUCCESS\",\"providerTransactionId\":\"txn-1\"}";

        IdempotencyRecord record = IdempotencyRecord.builder()
                .idempotencyKey(idempotencyKey)
                .merchantId("merchant-123")
                .responseBody(expectedJson)
                .build();

        when(idempotencyRecordRepository.findById(idempotencyKey)).thenReturn(Optional.of(record));

        Optional<String> cachedResponse = idempotencyService.findCachedResponse(idempotencyKey);

        assertTrue(cachedResponse.isPresent());
        assertEquals(expectedJson, cachedResponse.get());
    }
}
