package com.orderflow.order.service;

import static org.assertj.core.api.Assertions.assertThat;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;

class OrderMetricsTest {

  @Test
  void countersIncrement() {
    SimpleMeterRegistry registry = new SimpleMeterRegistry();
    OrderMetrics metrics = new OrderMetrics(registry);

    metrics.recordPlaced();
    metrics.recordConfirmed();
    metrics.recordFailed();

    assertThat(registry.get("orderflow.orders.placed").counter().count()).isEqualTo(1.0);
    assertThat(registry.get("orderflow.orders.confirmed").counter().count()).isEqualTo(1.0);
    assertThat(registry.get("orderflow.orders.failed").counter().count()).isEqualTo(1.0);
  }
}
