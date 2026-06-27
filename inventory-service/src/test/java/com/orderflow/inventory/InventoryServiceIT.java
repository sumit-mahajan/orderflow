package com.orderflow.inventory;

import static org.assertj.core.api.Assertions.assertThat;

import com.orderflow.common.messaging.CommandMessage;
import com.orderflow.common.messaging.CommandType;
import com.orderflow.common.messaging.LineItem;
import com.orderflow.inventory.repository.InventoryItemRepository;
import com.orderflow.inventory.service.InventoryService;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.KafkaContainer;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

/**
 * Real Postgres + Kafka via Testcontainers. Proves reserve/release correctness AND idempotency
 * (F-04/F-05) against a real database, including the atomic conditional-UPDATE stock logic.
 */
@SpringBootTest
@Testcontainers
class InventoryServiceIT {

  @Container
  static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine");

  @Container
  static KafkaContainer kafka =
      new KafkaContainer(DockerImageName.parse("confluentinc/cp-kafka:7.6.1"));

  @DynamicPropertySource
  static void props(DynamicPropertyRegistry registry) {
    registry.add("spring.datasource.url", postgres::getJdbcUrl);
    registry.add("spring.datasource.username", postgres::getUsername);
    registry.add("spring.datasource.password", postgres::getPassword);
    registry.add("spring.kafka.bootstrap-servers", kafka::getBootstrapServers);
  }

  @Autowired private InventoryService service;
  @Autowired private InventoryItemRepository items;

  private static CommandMessage cmd(UUID orderId, CommandType type, String sku, int qty) {
    return new CommandMessage(
        UUID.randomUUID(),
        orderId,
        UUID.randomUUID(),
        type,
        List.of(new LineItem(sku, qty)),
        null,
        null,
        Instant.now());
  }

  private int available(String sku) {
    return items.findById(sku).orElseThrow().getAvailableQty();
  }

  @Test
  void reserveAndRelease_areIdempotent() {
    UUID orderId = UUID.randomUUID();
    int before = available("SKU-LAPTOP");

    service.reserve(cmd(orderId, CommandType.RESERVE_INVENTORY, "SKU-LAPTOP", 2));
    assertThat(available("SKU-LAPTOP")).isEqualTo(before - 2);

    // redelivered reserve must not double-decrement
    service.reserve(cmd(orderId, CommandType.RESERVE_INVENTORY, "SKU-LAPTOP", 2));
    assertThat(available("SKU-LAPTOP")).isEqualTo(before - 2);

    service.release(cmd(orderId, CommandType.RELEASE_INVENTORY, "SKU-LAPTOP", 2));
    assertThat(available("SKU-LAPTOP")).isEqualTo(before);

    // redelivered release must not double-increment
    service.release(cmd(orderId, CommandType.RELEASE_INVENTORY, "SKU-LAPTOP", 2));
    assertThat(available("SKU-LAPTOP")).isEqualTo(before);
  }
}
