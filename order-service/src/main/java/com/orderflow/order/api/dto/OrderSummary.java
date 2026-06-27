package com.orderflow.order.api.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/** Row in the dashboard order list. */
public record OrderSummary(
    UUID orderId, UUID customerId, BigDecimal totalAmount, String status, String state, Instant updatedAt) {}
