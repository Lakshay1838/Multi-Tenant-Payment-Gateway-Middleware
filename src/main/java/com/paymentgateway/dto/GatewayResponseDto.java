package com.paymentgateway.dto;

import java.math.BigDecimal;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class GatewayResponseDto {

    private String gatewayTransactionId;
    private String providerTransactionId;
    private String status;
    private BigDecimal amountProcessed;
    private String currency;
    private String processedAt;
    private String errorDetails;
}
