package com.paymentgateway.controller;

import java.math.BigDecimal;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import com.paymentgateway.dto.GatewayResponseDto;
import com.paymentgateway.entity.IdempotencyRecord;
import com.paymentgateway.repository.IdempotencyRecordRepository;
import com.paymentgateway.service.PaymentService;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureMockMvc
class PaymentControllerIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private PaymentService paymentService;

    @Autowired
    private IdempotencyRecordRepository idempotencyRecordRepository;

    @BeforeEach
    void setUp() {
        idempotencyRecordRepository.deleteAll();
    }

    @Test
    void testHappyPath() throws Exception {
        GatewayResponseDto responseDto = GatewayResponseDto.builder()
                .gatewayTransactionId("gw-test-1")
                .providerTransactionId("prov-test-1")
                .status("SUCCESS")
                .amountProcessed(new BigDecimal("10.00"))
                .currency("USD")
                .processedAt("2026-06-09T00:00:00Z")
                .errorDetails(null)
                .build();

        when(paymentService.processPayment(any(), eq("happy-001"))).thenReturn(responseDto);

        mockMvc.perform(post("/api/v1/payments")
                        .header("Idempotency-Key", "happy-001")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validPayload("STRIPE")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.gatewayTransactionId").value("gw-test-1"));
    }

    @Test
    void testMissingIdempotencyHeader() throws Exception {
        mockMvc.perform(post("/api/v1/payments")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validPayload("STRIPE")))
                .andExpect(status().isBadRequest())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("Idempotency-Key header is required")));

        verifyNoInteractions(paymentService);
    }

    @Test
    void testValidationFailure() throws Exception {
        String invalidPayload = """
                {
                  \"merchantId\": \"\",
                  \"amount\": 10.00,
                  \"currency\": \"USD\",
                  \"paymentMethod\": \"CREDIT_CARD\",
                  \"targetProvider\": \"STRIPE\",
                  \"cardDetails\": {
                    \"holderName\": \"John\",
                    \"token\": \"tok_test\"
                  }
                }
                """;

        mockMvc.perform(post("/api/v1/payments")
                        .header("Idempotency-Key", "invalid-001")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(invalidPayload))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("VALIDATION_ERROR"));

        verifyNoInteractions(paymentService);
    }

    @Test
    void testDuplicateIdempotencyKey() throws Exception {
        String cachedResponse = "{" +
                "\"gatewayTransactionId\":\"gw-cached-1\"," +
                "\"providerTransactionId\":\"prov-cached-1\"," +
                "\"status\":\"SUCCESS\"," +
                "\"amountProcessed\":10.00," +
                "\"currency\":\"USD\"," +
                "\"processedAt\":\"2026-06-09T00:00:00Z\"," +
                "\"errorDetails\":null" +
                "}";

        idempotencyRecordRepository.save(IdempotencyRecord.builder()
                .idempotencyKey("dup-001")
                .merchantId("merchant-123")
                .responseBody(cachedResponse)
                .build());

        mockMvc.perform(post("/api/v1/payments")
                        .header("Idempotency-Key", "dup-001")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validPayload("STRIPE")))
                .andExpect(status().isOk())
                .andExpect(content().json(cachedResponse));

        verifyNoInteractions(paymentService);
    }

    private String validPayload(String provider) {
        return """
                {
                  \"merchantId\": \"merchant-123\",
                  \"amount\": 10.00,
                  \"currency\": \"USD\",
                  \"paymentMethod\": \"CREDIT_CARD\",
                  \"targetProvider\": \"%s\",
                  \"cardDetails\": {
                    \"holderName\": \"John\",
                    \"token\": \"tok_test\"
                  }
                }
                """.formatted(provider);
    }
}
