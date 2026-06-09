package com.paymentgateway.interceptor;

import java.util.Map;
import java.util.Optional;

import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.paymentgateway.service.IdempotencyService;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;

@Component
@Slf4j
public class IdempotencyInterceptor implements HandlerInterceptor {

    private final IdempotencyService idempotencyService;
    private final ObjectMapper objectMapper;

    public IdempotencyInterceptor(IdempotencyService idempotencyService, ObjectMapper objectMapper) {
        this.idempotencyService = idempotencyService;
        this.objectMapper = objectMapper;
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
        String idempotencyKey = request.getHeader("Idempotency-Key");

        if (idempotencyKey == null || idempotencyKey.isBlank()) {
            response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
            response.setContentType("application/json");
            response.getWriter().write(objectMapper.writeValueAsString(
                    Map.of("error", "Idempotency-Key header is required")));
            return false;
        }

        Optional<String> cached = idempotencyService.findCachedResponse(idempotencyKey);
        if (cached.isPresent()) {
            log.info("Short-circuiting duplicate request for idempotency key: {}", idempotencyKey);
            response.setStatus(HttpServletResponse.SC_OK);
            response.setContentType("application/json");
            response.getWriter().write(cached.get());
            return false;
        }

        request.setAttribute("IDEMPOTENCY_KEY", idempotencyKey);
        log.debug("Passing through new request with idempotency key: {}", idempotencyKey);
        return true;
    }
}
