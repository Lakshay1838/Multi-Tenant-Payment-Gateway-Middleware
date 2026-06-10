package com.paymentgateway.adapter.stripe;

public record StripeChargeRequest(long amount, String currency, String source, String description) {
}
