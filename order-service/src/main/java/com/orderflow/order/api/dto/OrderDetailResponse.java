package com.orderflow.order.api.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/** Full order view incl. the saga timeline. Also used as the SSE snapshot payload. */
public record OrderDetailResponse(
    UUID orderId,
    UUID customerId,
    BigDecimal totalAmount,
    String status,
    String state,
    List<ItemView> items,
    List<StepView> steps,
    Instant createdAt,
    Instant updatedAt) {

  public record ItemView(String sku, int qty, BigDecimal unitPrice) {}

  public record StepView(
      String step, String direction, String status, int attempt, String error, Instant at) {}
}
