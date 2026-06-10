package com.paymentgateway.dto;

public record ErrorResponseDto(String errorCode, String message, String timestamp, int httpStatus) {
}
