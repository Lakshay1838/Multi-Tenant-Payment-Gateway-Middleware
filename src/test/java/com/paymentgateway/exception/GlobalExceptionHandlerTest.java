package com.paymentgateway.exception;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.BindingResult;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;

import com.paymentgateway.dto.ErrorResponseDto;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class GlobalExceptionHandlerTest {

    private final GlobalExceptionHandler handler = new GlobalExceptionHandler();

    @Test
    void handleValidationShouldReturnNormalized400Response() {
        MethodArgumentNotValidException ex = mock(MethodArgumentNotValidException.class);
        BindingResult bindingResult = mock(BindingResult.class);

        when(ex.getBindingResult()).thenReturn(bindingResult);
        when(bindingResult.getFieldErrors()).thenReturn(List.of(
                new FieldError("paymentRequestDto", "merchantId", "merchantId is required"),
                new FieldError("paymentRequestDto", "currency", "currency must be ISO 4217 three-character code")));

        ResponseEntity<ErrorResponseDto> response = handler.handleValidation(ex);

        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        assertEquals("VALIDATION_ERROR", response.getBody().errorCode());
        assertEquals(400, response.getBody().httpStatus());
        assertTrue(response.getBody().message().contains("merchantId: merchantId is required"));
        assertTrue(response.getBody().message().contains("currency: currency must be ISO 4217 three-character code"));
    }

    @Test
    void handlePaymentErrorShouldReturnNormalized422Response() {
        PaymentProcessingException ex = new PaymentProcessingException("Unsupported payment provider: UNKNOWN_BANK");

        ResponseEntity<ErrorResponseDto> response = handler.handlePaymentError(ex);

        assertEquals(HttpStatus.UNPROCESSABLE_ENTITY, response.getStatusCode());
        assertEquals("PAYMENT_PROCESSING_ERROR", response.getBody().errorCode());
        assertEquals("Unsupported payment provider: UNKNOWN_BANK", response.getBody().message());
        assertEquals(422, response.getBody().httpStatus());
    }

    @Test
    void handleGenericShouldReturnNormalized500Response() {
        Exception ex = new RuntimeException("internal detail should not leak");

        ResponseEntity<ErrorResponseDto> response = handler.handleGeneric(ex);

        assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, response.getStatusCode());
        assertEquals("INTERNAL_SERVER_ERROR", response.getBody().errorCode());
        assertEquals("An unexpected error occurred", response.getBody().message());
        assertEquals(500, response.getBody().httpStatus());
    }
}
