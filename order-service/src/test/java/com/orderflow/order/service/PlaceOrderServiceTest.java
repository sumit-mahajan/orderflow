package com.orderflow.order.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.orderflow.common.messaging.CommandMessage;
import com.orderflow.common.messaging.Topics;
import com.orderflow.order.api.dto.PlaceOrderRequest;
import com.orderflow.order.domain.OrderEntity;
import com.orderflow.order.domain.OrderStatus;
import com.orderflow.order.domain.SagaInstance;
import com.orderflow.order.domain.SagaStepEntity;
import com.orderflow.order.messaging.OutboxWriter;
import com.orderflow.order.repository.OrderRepository;
import com.orderflow.order.repository.SagaInstanceRepository;
import com.orderflow.order.repository.SagaStepRepository;
import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class PlaceOrderServiceTest {

  @Mock private OrderRepository orders;
  @Mock private SagaInstanceRepository sagas;
  @Mock private SagaStepRepository steps;
  @Mock private OutboxWriter outboxWriter;
  @Mock private IdempotencyService idempotency;

  private final ObjectMapper json = new ObjectMapper().registerModule(new JavaTimeModule());
  private PlaceOrderService service;

  @BeforeEach
  void setUp() {
    service = new PlaceOrderService(orders, sagas, steps, outboxWriter, idempotency, json);
  }

  private static PlaceOrderRequest request() {
    return new PlaceOrderRequest(
        UUID.randomUUID(),
        List.of(new PlaceOrderRequest.Item("SKU-LAPTOP", 1)),
        new BigDecimal("10.00"),
        null);
  }

  @Test
  void newOrder_persistsOrderSagaOutboxAndStep() {
    when(idempotency.acquire(anyString(), any(UUID.class), anyString()))
        .thenReturn(new IdempotencyService.Acquire(true, null));

    PlaceOrderResult result = service.place("key-1", request());

    assertThat(result.status()).isEqualTo(OrderStatus.PLACED);
    verify(orders).save(any(OrderEntity.class));
    verify(sagas).save(any(SagaInstance.class));
    verify(outboxWriter).enqueueCommand(eq(Topics.COMMANDS_INVENTORY), any(CommandMessage.class));
    verify(steps).save(any(SagaStepEntity.class));
  }

  @Test
  void duplicateSubmission_returnsExistingOrder_withoutCreatingNew() {
    UUID existingId = UUID.randomUUID();
    when(idempotency.acquire(anyString(), any(UUID.class), anyString()))
        .thenReturn(new IdempotencyService.Acquire(false, existingId));
    OrderEntity existing =
        new OrderEntity(existingId, UUID.randomUUID(), new BigDecimal("10.00"), "key-1", null);
    when(orders.findById(existingId)).thenReturn(Optional.of(existing));

    PlaceOrderResult result = service.place("key-1", request());

    assertThat(result.orderId()).isEqualTo(existingId);
    verify(orders, never()).save(any(OrderEntity.class));
    verify(outboxWriter, never()).enqueueCommand(anyString(), any(CommandMessage.class));
  }
}
