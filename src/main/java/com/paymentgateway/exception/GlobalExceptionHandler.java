package com.paymentgateway.exception;

import java.time.Instant;
import java.util.stream.Collectors;

import org.springframework.http.ResponseEntity;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;

import com.paymentgateway.dto.ErrorResponseDto;

import lombok.extern.slf4j.Slf4j;

@ControllerAdvice
@Slf4j
public class GlobalExceptionHandler {

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorResponseDto> handleValidation(MethodArgumentNotValidException ex) {
        String message = ex.getBindingResult().getFieldErrors().stream()
                .map(this::formatFieldError)
                .collect(Collectors.joining(", ", "Validation failed: ", ""));

        log.warn(message);
        return ResponseEntity.badRequest()
                .body(new ErrorResponseDto(
                        "VALIDATION_ERROR",
                        message,
                        Instant.now().toString(),
                        400));
    }

    @ExceptionHandler(PaymentProcessingException.class)
    public ResponseEntity<ErrorResponseDto> handlePaymentError(PaymentProcessingException ex) {
        log.error("Payment processing error: {}", ex.getMessage(), ex);
        return ResponseEntity.unprocessableEntity()
                .body(new ErrorResponseDto(
                        "PAYMENT_PROCESSING_ERROR",
                        ex.getMessage(),
                        Instant.now().toString(),
                        422));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponseDto> handleGeneric(Exception ex) {
        log.error("Unhandled exception: {}", ex.getMessage(), ex);
        return ResponseEntity.internalServerError()
                .body(new ErrorResponseDto(
                        "INTERNAL_SERVER_ERROR",
                        "An unexpected error occurred",
                        Instant.now().toString(),
                        500));
    }

    private String formatFieldError(FieldError error) {
        return error.getField() + ": " + error.getDefaultMessage();
    }
}
