package com.orderflow.order.api.dto;

import java.util.UUID;

/** Response for POST /api/orders. */
public record OrderResponse(UUID orderId, String status) {}
