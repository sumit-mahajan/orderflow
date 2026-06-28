package com.orderflow.order.service;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.stereotype.Component;

/** Domain counters used by the Grafana dashboard (F-15). */
@Component
public class OrderMetrics {

  private final Counter ordersPlaced;
  private final Counter ordersConfirmed;
  private final Counter ordersFailed;

  public OrderMetrics(MeterRegistry meterRegistry) {
    this.ordersPlaced =
        Counter.builder("orderflow.orders.placed")
            .description("Total orders accepted by POST /api/orders")
            .register(meterRegistry);
    this.ordersConfirmed =
        Counter.builder("orderflow.orders.confirmed")
            .description("Total orders that reached OrderConfirmed")
            .register(meterRegistry);
    this.ordersFailed =
        Counter.builder("orderflow.orders.failed")
            .description("Total orders that reached OrderFailed")
            .register(meterRegistry);
  }

  public void recordPlaced() {
    ordersPlaced.increment();
  }

  public void recordConfirmed() {
    ordersConfirmed.increment();
  }

  public void recordFailed() {
    ordersFailed.increment();
  }
}
