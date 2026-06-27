package com.orderflow.order.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.orderflow.common.messaging.CommandMessage;
import com.orderflow.common.messaging.CommandType;
import com.orderflow.common.messaging.LineItem;
import com.orderflow.common.messaging.Simulate;
import com.orderflow.common.messaging.Topics;
import com.orderflow.order.api.dto.PlaceOrderRequest;
import com.orderflow.order.domain.OrderEntity;
import com.orderflow.order.domain.OrderItemEntity;
import com.orderflow.order.domain.OrderStatus;
import com.orderflow.order.domain.SagaInstance;
import com.orderflow.order.domain.SagaStepEntity;
import com.orderflow.order.domain.StepEnums;
import com.orderflow.order.messaging.OutboxWriter;
import com.orderflow.order.repository.OrderRepository;
import com.orderflow.order.repository.SagaInstanceRepository;
import com.orderflow.order.repository.SagaStepRepository;
import com.orderflow.order.saga.SagaState;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.HexFormat;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Places an order (F-01) idempotently (F-02), then kicks off the saga by writing a
 * ReserveInventory command to the outbox in the SAME transaction as the order + saga rows (F-07).
 */
@Service
public class PlaceOrderService {

  private static final Logger log = LoggerFactory.getLogger(PlaceOrderService.class);

  private final OrderRepository orders;
  private final SagaInstanceRepository sagas;
  private final SagaStepRepository steps;
  private final OutboxWriter outboxWriter;
  private final IdempotencyService idempotency;
  private final ObjectMapper json;

  public PlaceOrderService(
      OrderRepository orders,
      SagaInstanceRepository sagas,
      SagaStepRepository steps,
      OutboxWriter outboxWriter,
      IdempotencyService idempotency,
      ObjectMapper json) {
    this.orders = orders;
    this.sagas = sagas;
    this.steps = steps;
    this.outboxWriter = outboxWriter;
    this.idempotency = idempotency;
    this.json = json;
  }

  @Transactional
  public PlaceOrderResult place(String idempotencyKey, PlaceOrderRequest request) {
    UUID orderId = UUID.randomUUID();
    String bodyHash = hash(request);

    IdempotencyService.Acquire acquire = idempotency.acquire(idempotencyKey, orderId, bodyHash);
    if (!acquire.acquired()) {
      Optional<OrderEntity> existing = orders.findById(acquire.existingOrderId());
      if (existing.isPresent()) {
        log.info("Duplicate submission for key {} → returning existing order {}",
            idempotencyKey, existing.get().getOrderId());
        return new PlaceOrderResult(existing.get().getOrderId(), existing.get().getStatus());
      }
      // Redis slot pointed at an order that never committed — take it over.
      idempotency.overwrite(idempotencyKey, orderId, bodyHash);
    }

    Simulate simulate = toSimulate(request);
    OrderEntity order =
        new OrderEntity(
            orderId, request.customerId(), request.amount(), idempotencyKey, toJsonOrNull(simulate));
    for (PlaceOrderRequest.Item item : request.items()) {
      order.addItem(new OrderItemEntity(item.sku(), item.qty(), BigDecimal.ZERO));
    }
    orders.save(order);

    SagaInstance saga = new SagaInstance(orderId, new SagaState.OrderPlaced().stateName());
    sagas.save(saga);

    List<LineItem> lines =
        request.items().stream().map(i -> new LineItem(i.sku(), i.qty())).toList();
    CommandMessage reserve =
        new CommandMessage(
            UUID.randomUUID(),
            orderId,
            saga.getSagaId(),
            CommandType.RESERVE_INVENTORY,
            lines,
            request.amount(),
            simulate,
            Instant.now());
    outboxWriter.enqueueCommand(Topics.COMMANDS_INVENTORY, reserve);

    steps.save(
        new SagaStepEntity(
            saga.getSagaId(),
            StepEnums.StepName.INVENTORY,
            StepEnums.Direction.FORWARD,
            StepEnums.Status.STARTED,
            orderId,
            null));

    log.info("Placed order {} ({} line(s)) — reserve command enqueued", orderId, lines.size());
    return new PlaceOrderResult(orderId, OrderStatus.PLACED);
  }

  private Simulate toSimulate(PlaceOrderRequest request) {
    if (request.simulate() == null) {
      return null;
    }
    return new Simulate(
        request.simulate().paymentFail(),
        request.simulate().shipmentMode(),
        request.simulate().failAfterReserve());
  }

  private String toJsonOrNull(Simulate simulate) {
    if (simulate == null) {
      return null;
    }
    try {
      return json.writeValueAsString(simulate);
    } catch (Exception e) {
      throw new IllegalStateException("Failed to serialize simulate flags", e);
    }
  }

  /** Stable fingerprint of the business content, used to detect same-key-different-body conflicts. */
  private String hash(PlaceOrderRequest request) {
    try {
      String canonical =
          json.writeValueAsString(
              List.of(request.customerId(), request.items(), request.amount()));
      byte[] digest =
          MessageDigest.getInstance("SHA-256").digest(canonical.getBytes(StandardCharsets.UTF_8));
      return HexFormat.of().formatHex(digest);
    } catch (Exception e) {
      throw new IllegalStateException("Failed to hash request", e);
    }
  }
}
