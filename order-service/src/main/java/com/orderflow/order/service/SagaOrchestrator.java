package com.orderflow.order.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.orderflow.common.messaging.CommandMessage;
import com.orderflow.common.messaging.CommandType;
import com.orderflow.common.messaging.EventMessage;
import com.orderflow.common.messaging.LineItem;
import com.orderflow.common.messaging.Simulate;
import com.orderflow.common.messaging.Topics;
import com.orderflow.order.domain.OrderEntity;
import com.orderflow.order.domain.SagaInstance;
import com.orderflow.order.domain.SagaStepEntity;
import com.orderflow.order.domain.StepEnums;
import com.orderflow.order.messaging.OutboxWriter;
import com.orderflow.order.repository.OrderRepository;
import com.orderflow.order.repository.SagaInstanceRepository;
import com.orderflow.order.repository.SagaStepRepository;
import com.orderflow.order.saga.IllegalSagaTransition;
import com.orderflow.order.saga.SagaState;
import com.orderflow.order.saga.SagaStateMachine;
import com.orderflow.order.saga.SagaStates;
import com.orderflow.order.sse.SseService;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/**
 * The saga orchestrator (F-06). Reacts to participant result events, advances the saga via the
 * {@link SagaStateMachine}, and — on failure — drives compensations in reverse order.
 *
 * <ul>
 *   <li>M1/M2/M3 inventory: InventoryReserved → send CapturePayment (or failAfterReserve →
 *       compensation). InventoryReservationFailed → OrderFailed. InventoryReleased (compensating)
 *       → OrderFailed.
 *   <li>M2 payment: PaymentFailed → begin compensation, emit ReleaseInventory. PaymentRefunded
 *       (M3 compensation) → emit ReleaseInventory, still Compensating.
 *   <li>M3 shipment: PaymentCaptured → emit CreateShipment. ShipmentInitiated → confirm.
 *       ShipmentFailed → begin compensation, emit RefundPayment (→ release inventory → OrderFailed).
 * </ul>
 *
 * <p>Redelivered/late events are safe: the state machine rejects illegal transitions and we treat
 * that as a no-op (idempotent event handling).
 */
@Service
public class SagaOrchestrator {

  private static final Logger log = LoggerFactory.getLogger(SagaOrchestrator.class);

  private final SagaInstanceRepository sagas;
  private final SagaStepRepository steps;
  private final OrderRepository orders;
  private final OutboxWriter outboxWriter;
  private final SseService sse;
  private final ObjectMapper json;

  public SagaOrchestrator(
      SagaInstanceRepository sagas,
      SagaStepRepository steps,
      OrderRepository orders,
      OutboxWriter outboxWriter,
      SseService sse,
      ObjectMapper json) {
    this.sagas = sagas;
    this.steps = steps;
    this.orders = orders;
    this.outboxWriter = outboxWriter;
    this.sse = sse;
    this.json = json;
  }

  @Transactional
  public void handle(EventMessage event) {
    UUID orderId = event.correlationId();
    SagaInstance saga =
        sagas
            .findByOrderId(orderId)
            .orElseThrow(() -> new IllegalStateException("No saga for order " + orderId));
    SagaState current = SagaStates.parse(saga.getCurrentState());

    try {
      switch (event.type()) {
        case INVENTORY_RESERVED -> onInventoryReserved(saga, current, orderId);
        case INVENTORY_RESERVATION_FAILED -> onReservationFailed(saga, current, orderId, event);
        case INVENTORY_RELEASED -> onInventoryReleased(saga, current, orderId);
        case PAYMENT_CAPTURED -> onPaymentCaptured(saga, current, orderId);
        case PAYMENT_FAILED -> onPaymentFailed(saga, current, orderId, event);
        case PAYMENT_REFUNDED -> onPaymentRefunded(saga, current, orderId);
        case SHIPMENT_INITIATED -> onShipmentInitiated(saga, current, orderId, event);
        case SHIPMENT_FAILED -> onShipmentFailed(saga, current, orderId, event);
        default -> log.warn("Unhandled event {} for order {}", event.type(), orderId);
      }
    } catch (IllegalSagaTransition e) {
      log.warn("Ignoring event {} for order {}: {}", event.type(), orderId, e.getMessage());
      return;
    }

    publishSnapshotAfterCommit(orderId);
  }

  private void onInventoryReserved(SagaInstance saga, SagaState current, UUID orderId) {
    SagaState inventoryReserved = SagaStateMachine.onInventoryReserved(current);
    recordStep(saga, StepEnums.StepName.INVENTORY, StepEnums.Direction.FORWARD,
        StepEnums.Status.SUCCESS, orderId, null);

    OrderEntity order = orders.findById(orderId).orElseThrow();
    Simulate simulate = readSimulate(order);

    if (simulate != null && Boolean.TRUE.equals(simulate.failAfterReserve())) {
      // M1 demo: injected failure before payment is attempted.
      SagaState compensating = SagaStateMachine.beginCompensation(inventoryReserved);
      saga.setCurrentState(compensating.stateName());
      emitReleaseInventory(saga, order, simulate);
      recordStep(saga, StepEnums.StepName.INVENTORY, StepEnums.Direction.COMPENSATION,
          StepEnums.Status.STARTED, orderId, "failAfterReserve injected");
      log.info("Order {} reserved then forced to fail — compensating (release inventory)", orderId);
    } else {
      // M2: advance to InventoryReserved and send CapturePayment.
      saga.setCurrentState(inventoryReserved.stateName());
      emitCapturePayment(saga, order, simulate);
      recordStep(saga, StepEnums.StepName.PAYMENT, StepEnums.Direction.FORWARD,
          StepEnums.Status.STARTED, orderId, null);
      log.info("Order {} inventory reserved — capturing payment", orderId);
    }
    sagas.save(saga);
  }

  private void onPaymentCaptured(SagaInstance saga, SagaState current, UUID orderId) {
    SagaState paymentCaptured = SagaStateMachine.onPaymentCaptured(current);
    saga.setCurrentState(paymentCaptured.stateName());
    recordStep(saga, StepEnums.StepName.PAYMENT, StepEnums.Direction.FORWARD,
        StepEnums.Status.SUCCESS, orderId, null);
    OrderEntity order = orders.findById(orderId).orElseThrow();
    Simulate simulate = readSimulate(order);
    emitCreateShipment(saga, order, simulate);
    recordStep(saga, StepEnums.StepName.SHIPMENT, StepEnums.Direction.FORWARD,
        StepEnums.Status.STARTED, orderId, null);
    sagas.save(saga);
    log.info("Order {} payment captured — initiating shipment", orderId);
  }

  private void onShipmentInitiated(
      SagaInstance saga, SagaState current, UUID orderId, EventMessage event) {
    SagaState shipmentInitiated = SagaStateMachine.onShipmentInitiated(current);
    SagaState confirmed = SagaStateMachine.confirm(shipmentInitiated);
    saga.setCurrentState(confirmed.stateName());
    recordStep(saga, StepEnums.StepName.SHIPMENT, StepEnums.Direction.FORWARD,
        StepEnums.Status.SUCCESS, orderId, null);
    orders.findById(orderId).ifPresent(OrderEntity::markConfirmed);
    sagas.save(saga);
    log.info("Order {} shipment initiated (carrierRef={}) → confirmed", orderId, event.carrierRef());
  }

  private void onShipmentFailed(
      SagaInstance saga, SagaState current, UUID orderId, EventMessage event) {
    SagaState compensating = SagaStateMachine.beginCompensation(current);
    saga.setCurrentState(compensating.stateName());
    recordStep(saga, StepEnums.StepName.SHIPMENT, StepEnums.Direction.FORWARD,
        StepEnums.Status.FAILED, orderId, event.reason());
    OrderEntity order = orders.findById(orderId).orElseThrow();
    Simulate simulate = readSimulate(order);
    emitRefundPayment(saga, order, simulate);
    recordStep(saga, StepEnums.StepName.PAYMENT, StepEnums.Direction.COMPENSATION,
        StepEnums.Status.STARTED, orderId, "shipment failed, refunding payment");
    sagas.save(saga);
    log.info("Order {} shipment failed → compensating (refund payment)", orderId);
  }

  private void onPaymentRefunded(SagaInstance saga, SagaState current, UUID orderId) {
    SagaStateMachine.onPaymentRefunded(current); // validates Compensating → Compensating
    recordStep(saga, StepEnums.StepName.PAYMENT, StepEnums.Direction.COMPENSATION,
        StepEnums.Status.SUCCESS, orderId, null);
    OrderEntity order = orders.findById(orderId).orElseThrow();
    Simulate simulate = readSimulate(order);
    emitReleaseInventory(saga, order, simulate);
    recordStep(saga, StepEnums.StepName.INVENTORY, StepEnums.Direction.COMPENSATION,
        StepEnums.Status.STARTED, orderId, "payment refunded, releasing inventory");
    sagas.save(saga);
    log.info("Order {} payment refunded → releasing inventory", orderId);
  }

  private void onPaymentFailed(
      SagaInstance saga, SagaState current, UUID orderId, EventMessage event) {
    SagaState compensating = SagaStateMachine.beginCompensation(current);
    saga.setCurrentState(compensating.stateName());
    recordStep(saga, StepEnums.StepName.PAYMENT, StepEnums.Direction.FORWARD,
        StepEnums.Status.FAILED, orderId, event.reason());
    OrderEntity order = orders.findById(orderId).orElseThrow();
    Simulate simulate = readSimulate(order);
    emitReleaseInventory(saga, order, simulate);
    recordStep(saga, StepEnums.StepName.INVENTORY, StepEnums.Direction.COMPENSATION,
        StepEnums.Status.STARTED, orderId, "payment failed, releasing inventory");
    sagas.save(saga);
    log.info("Order {} payment failed → compensating (release inventory)", orderId);
  }

  private void onReservationFailed(
      SagaInstance saga, SagaState current, UUID orderId, EventMessage event) {
    SagaState failed = SagaStateMachine.onInventoryReservationFailed(current);
    saga.setCurrentState(failed.stateName());
    sagas.save(saga);
    recordStep(saga, StepEnums.StepName.INVENTORY, StepEnums.Direction.FORWARD,
        StepEnums.Status.FAILED, orderId, event.reason());
    orders.findById(orderId).ifPresent(OrderEntity::markFailed);
    log.info("Order {} failed at inventory: {}", orderId, event.reason());
  }

  private void onInventoryReleased(SagaInstance saga, SagaState current, UUID orderId) {
    SagaState failed = SagaStateMachine.onInventoryReleased(current);
    saga.setCurrentState(failed.stateName());
    sagas.save(saga);
    recordStep(saga, StepEnums.StepName.INVENTORY, StepEnums.Direction.COMPENSATION,
        StepEnums.Status.SUCCESS, orderId, null);
    orders.findById(orderId).ifPresent(OrderEntity::markFailed);
    log.info("Order {} compensation complete (inventory released) — order failed", orderId);
  }

  private void emitCreateShipment(SagaInstance saga, OrderEntity order, Simulate simulate) {
    CommandMessage create =
        new CommandMessage(
            UUID.randomUUID(),
            order.getOrderId(),
            saga.getSagaId(),
            CommandType.CREATE_SHIPMENT,
            null,
            null,
            simulate,
            Instant.now());
    outboxWriter.enqueueCommand(Topics.COMMANDS_SHIPMENT, create);
  }

  private void emitRefundPayment(SagaInstance saga, OrderEntity order, Simulate simulate) {
    CommandMessage refund =
        new CommandMessage(
            UUID.randomUUID(),
            order.getOrderId(),
            saga.getSagaId(),
            CommandType.REFUND_PAYMENT,
            null,
            order.getTotalAmount(),
            simulate,
            Instant.now());
    outboxWriter.enqueueCommand(Topics.COMMANDS_PAYMENT, refund);
  }

  private void emitCapturePayment(SagaInstance saga, OrderEntity order, Simulate simulate) {
    CommandMessage capture =
        new CommandMessage(
            UUID.randomUUID(),
            order.getOrderId(),
            saga.getSagaId(),
            CommandType.CAPTURE_PAYMENT,
            null,
            order.getTotalAmount(),
            simulate,
            Instant.now());
    outboxWriter.enqueueCommand(Topics.COMMANDS_PAYMENT, capture);
  }

  private void emitReleaseInventory(SagaInstance saga, OrderEntity order, Simulate simulate) {
    List<LineItem> lines =
        order.getItems().stream().map(i -> new LineItem(i.getSku(), i.getQty())).toList();
    CommandMessage release =
        new CommandMessage(
            UUID.randomUUID(),
            order.getOrderId(),
            saga.getSagaId(),
            CommandType.RELEASE_INVENTORY,
            lines,
            null,
            simulate,
            Instant.now());
    outboxWriter.enqueueCommand(Topics.COMMANDS_INVENTORY, release);
  }

  private void recordStep(
      SagaInstance saga,
      StepEnums.StepName step,
      StepEnums.Direction direction,
      StepEnums.Status status,
      UUID correlationId,
      String error) {
    steps.save(new SagaStepEntity(saga.getSagaId(), step, direction, status, correlationId, error));
  }

  private Simulate readSimulate(OrderEntity order) {
    if (order.getSimulate() == null) {
      return null;
    }
    try {
      return json.readValue(order.getSimulate(), Simulate.class);
    } catch (Exception e) {
      log.warn("Could not parse simulate for order {}: {}", order.getOrderId(), e.toString());
      return null;
    }
  }

  private void publishSnapshotAfterCommit(UUID orderId) {
    if (TransactionSynchronizationManager.isSynchronizationActive()) {
      TransactionSynchronizationManager.registerSynchronization(
          new TransactionSynchronization() {
            @Override
            public void afterCommit() {
              sse.publishSnapshot(orderId);
            }
          });
    } else {
      sse.publishSnapshot(orderId);
    }
  }
}
