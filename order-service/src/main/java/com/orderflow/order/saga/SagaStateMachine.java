package com.orderflow.order.saga;

import com.orderflow.order.saga.SagaState.Compensating;
import com.orderflow.order.saga.SagaState.InventoryReserved;
import com.orderflow.order.saga.SagaState.OrderConfirmed;
import com.orderflow.order.saga.SagaState.OrderFailed;
import com.orderflow.order.saga.SagaState.OrderPlaced;

/**
 * Legal saga transitions, expressed as pattern-matched {@code switch} over the sealed {@link
 * SagaState}. Each method answers: "given the current state, what is the next state for this
 * trigger?" — and rejects any transition that isn't allowed.
 *
 * <p>M1 only wires the inventory step: OrderPlaced → InventoryReserved → (OrderConfirmed | begin
 * Compensating → OrderFailed). Payment/shipment transitions arrive in M2/M3.
 */
public final class SagaStateMachine {

  private SagaStateMachine() {}

  public static SagaState onInventoryReserved(SagaState current) {
    return switch (current) {
      case OrderPlaced ignored -> new InventoryReserved();
      default -> throw new IllegalSagaTransition("INVENTORY_RESERVED", current);
    };
  }

  public static SagaState onInventoryReservationFailed(SagaState current) {
    return switch (current) {
      case OrderPlaced ignored -> new OrderFailed();
      default -> throw new IllegalSagaTransition("INVENTORY_RESERVATION_FAILED", current);
    };
  }

  public static SagaState confirm(SagaState current) {
    return switch (current) {
      case InventoryReserved ignored -> new OrderConfirmed();
      default -> throw new IllegalSagaTransition("CONFIRM", current);
    };
  }

  public static SagaState beginCompensation(SagaState current) {
    return switch (current) {
      case InventoryReserved ignored -> new Compensating();
      default -> throw new IllegalSagaTransition("BEGIN_COMPENSATION", current);
    };
  }

  public static SagaState onInventoryReleased(SagaState current) {
    return switch (current) {
      case Compensating ignored -> new OrderFailed();
      default -> throw new IllegalSagaTransition("INVENTORY_RELEASED", current);
    };
  }
}
