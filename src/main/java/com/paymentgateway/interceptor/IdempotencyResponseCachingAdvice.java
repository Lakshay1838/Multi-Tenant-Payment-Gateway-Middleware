package com.paymentgateway.interceptor;

import org.springframework.core.MethodParameter;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageConverter;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.http.server.ServletServerHttpRequest;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.servlet.mvc.method.annotation.ResponseBodyAdvice;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.paymentgateway.dto.ErrorResponseDto;
import com.paymentgateway.service.IdempotencyService;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@ControllerAdvice
@RequiredArgsConstructor
@Slf4j
public class IdempotencyResponseCachingAdvice implements ResponseBodyAdvice<Object> {

    private final IdempotencyService idempotencyService;
    private final ObjectMapper objectMapper;

    @Override
    public boolean supports(MethodParameter returnType, Class<? extends HttpMessageConverter<?>> converterType) {
        return returnType.getDeclaringClass().getName().contains("GlobalExceptionHandler");
    }

    @Override
    public Object beforeBodyWrite(Object body, MethodParameter returnType, MediaType selectedContentType,
            Class<? extends HttpMessageConverter<?>> selectedConverterType,
            ServerHttpRequest request, ServerHttpResponse response) {

        if (!(body instanceof ErrorResponseDto error)) {
            return body;
        }
        if (!request.getURI().getPath().contains("/api/v1/payments")) {
            return body;
        }

        String idempotencyKey = (String) ((ServletServerHttpRequest) request)
                .getServletRequest().getAttribute("IDEMPOTENCY_KEY");
        if (idempotencyKey == null) {
            return body;
        }

        // Only cache payment processing errors (422) — not validation/auth errors
        if (error.httpStatus() != 422) {
            return body;
        }

        try {
            String json = objectMapper.writeValueAsString(error);
            idempotencyService.cacheResponse(idempotencyKey, "", error.httpStatus(), json);
            log.debug("Cached 422 error response for idempotency key: {}", idempotencyKey);
        } catch (Exception e) {
            log.warn("Failed to cache error response for idempotency key: {}", idempotencyKey, e);
        }

        return body;
    }
}
