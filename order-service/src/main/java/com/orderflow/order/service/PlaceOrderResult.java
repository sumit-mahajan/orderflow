package com.orderflow.order.service;

import com.orderflow.order.domain.OrderStatus;
import java.util.UUID;

public record PlaceOrderResult(UUID orderId, OrderStatus status) {}
