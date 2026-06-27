package com.orderflow.order.api.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

/**
 * Request body for POST /api/orders. Validated at the controller boundary (Jakarta Bean Validation,
 * ≈ .NET DataAnnotations) — never inside the use case.
 */
public record PlaceOrderRequest(
    @NotNull UUID customerId,
    @NotEmpty @Valid List<Item> items,
    @NotNull @Positive BigDecimal amount,
    @Valid SimulateDto simulate) {

  public record Item(@NotNull String sku, @Positive int qty) {}
}
