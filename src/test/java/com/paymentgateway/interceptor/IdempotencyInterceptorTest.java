package com.paymentgateway.interceptor;

import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.paymentgateway.service.IdempotencyService;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class IdempotencyInterceptorTest {

    @Mock
    private IdempotencyService idempotencyService;

    @Test
    void preHandleShouldReturnBadRequestWhenHeaderMissing() throws Exception {
        IdempotencyInterceptor interceptor = new IdempotencyInterceptor(idempotencyService, new ObjectMapper());
        MockHttpServletRequest request = new MockHttpServletRequest();
        MockHttpServletResponse response = new MockHttpServletResponse();

        boolean result = interceptor.preHandle(request, response, new Object());

        assertFalse(result);
        assertEquals(400, response.getStatus());
    }

    @Test
    void preHandleShouldShortCircuitWhenCacheHit() throws Exception {
        String key = "idem-hit-123";
        String cachedBody = "{\"status\":\"SUCCESS\"}";

        when(idempotencyService.findCachedResponse(key)).thenReturn(Optional.of(cachedBody));

        IdempotencyInterceptor interceptor = new IdempotencyInterceptor(idempotencyService, new ObjectMapper());
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("Idempotency-Key", key);
        MockHttpServletResponse response = new MockHttpServletResponse();

        boolean result = interceptor.preHandle(request, response, new Object());

        assertFalse(result);
        assertEquals(200, response.getStatus());
        assertEquals(cachedBody, response.getContentAsString());
    }

    @Test
    void preHandleShouldPassThroughWhenCacheMiss() throws Exception {
        String key = "idem-miss-123";

        when(idempotencyService.findCachedResponse(key)).thenReturn(Optional.empty());

        IdempotencyInterceptor interceptor = new IdempotencyInterceptor(idempotencyService, new ObjectMapper());
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("Idempotency-Key", key);
        MockHttpServletResponse response = new MockHttpServletResponse();

        boolean result = interceptor.preHandle(request, response, new Object());

        assertTrue(result);
        assertEquals(key, request.getAttribute("IDEMPOTENCY_KEY"));
    }
}
