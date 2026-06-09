# Multi-Tenant Payment Gateway Middleware

## Project Description
This project is a Spring Boot based middleware for processing multi-tenant card payments through pluggable providers. It includes provider routing, idempotency protection, request validation, and normalized error handling to support fintech-grade API behavior.

## Technology Stack
- Java 17
- Spring Boot 3.2
- Spring MVC + WebFlux WebClient
- Spring Data JPA
- PostgreSQL (runtime dependency)
- H2 (local/in-memory development)
- Redis (idempotency integration point)
- Maven

## Design Patterns
- Interceptor Pattern: Idempotency request firewall
- Strategy Pattern: Dynamic payment provider selection
- Adapter Pattern: Provider-specific request/response mapping
- DTO Pattern: API contract boundaries
- Builder Pattern: DTO and entity object construction

## Prerequisites
- Java 17
- Maven 3.8+

## Build and Run
```bash
mvn clean install
mvn spring-boot:run
```

## H2 Console
- URL: http://localhost:8080/h2-console
- JDBC URL: jdbc:h2:mem:paymentgateway
- Username: sa
- Password: (leave blank)

## API Reference - 5 Postman Verification Calls

### Call 1 - Missing Idempotency Header
- Method: POST
- URL: http://localhost:8080/api/v1/payments
- Headers: none
- Body example:
```json
{
  "merchantId": "merchant-any",
  "amount": 10.00,
  "currency": "USD",
  "paymentMethod": "CREDIT_CARD",
  "targetProvider": "STRIPE",
  "cardDetails": {
    "holderName": "John",
    "token": "tok_test"
  }
}
```
- Expected:
  - HTTP 400
  - Body contains: {"error": "Idempotency-Key header is required"}

### Call 2 - Validation Failure
- Method: POST
- URL: http://localhost:8080/api/v1/payments
- Headers:
  - Idempotency-Key: idem-test-001
  - Content-Type: application/json
- Body:
```json
{
  "merchantId": "",
  "amount": -5.00,
  "currency": "US",
  "paymentMethod": "CREDIT_CARD",
  "targetProvider": "STRIPE",
  "cardDetails": {
    "holderName": "John",
    "token": "tok_test"
  }
}
```
- Expected:
  - HTTP 400
  - errorCode: VALIDATION_ERROR
  - message lists violated fields

### Call 3 - First Successful Payment (Stripe)
- Method: POST
- URL: http://localhost:8080/api/v1/payments
- Headers:
  - Idempotency-Key: idem-test-002
  - Content-Type: application/json
- Body:
```json
{
  "merchantId": "merch_998811",
  "amount": 250.75,
  "currency": "USD",
  "paymentMethod": "CREDIT_CARD",
  "targetProvider": "STRIPE",
  "cardDetails": {
    "holderName": "John Doe",
    "token": "tok_mock_card_visa"
  }
}
```
- Expected for fully configured Stripe sandbox:
  - HTTP 200
  - GatewayResponseDto with status: SUCCESS
- Local demo note:
  - In local/offline mode or with invalid Stripe credentials, this may return a PaymentProcessingException mapped to HTTP 422. That behavior is expected and confirms provider error handling.

### Call 4 - Idempotency Short-Circuit
- Method: POST
- URL: http://localhost:8080/api/v1/payments
- Headers:
  - Idempotency-Key: idem-test-002
  - Content-Type: application/json
- Body:
  - Use the exact same body as Call 3
- Expected:
  - HTTP 200
  - Response body byte-for-byte identical to Call 3
  - Served from idempotency cache (interceptor short-circuit)

### Call 5 - Unsupported Provider
- Method: POST
- URL: http://localhost:8080/api/v1/payments
- Headers:
  - Idempotency-Key: idem-test-003
  - Content-Type: application/json
- Body:
```json
{
  "merchantId": "merch_998811",
  "amount": 250.75,
  "currency": "USD",
  "paymentMethod": "CREDIT_CARD",
  "targetProvider": "UNKNOWN_BANK",
  "cardDetails": {
    "holderName": "John Doe",
    "token": "tok_mock_card_visa"
  }
}
```
- Expected:
  - HTTP 422
  - errorCode: PAYMENT_PROCESSING_ERROR
  - message contains: Unsupported payment provider: UNKNOWN_BANK
