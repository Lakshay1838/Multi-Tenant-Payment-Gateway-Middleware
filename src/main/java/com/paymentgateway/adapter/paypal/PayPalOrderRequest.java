package com.paymentgateway.adapter.paypal;

import java.util.List;

public record PayPalOrderRequest(String intent, List<PurchaseUnit> purchase_units) {
}
