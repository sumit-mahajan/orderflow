package com.orderflow.order.saga;

import com.orderflow.order.saga.SagaState.Compensating;
import com.orderflow.order.saga.SagaState.InventoryReserved;
import com.orderflow.order.saga.SagaState.OrderConfirmed;
import com.orderflow.order.saga.SagaState.OrderFailed;
import com.orderflow.order.saga.SagaState.OrderPlaced;
import com.orderflow.order.saga.SagaState.PaymentCaptured;

/**
 * Legal saga transitions, expressed as pattern-matched {@code switch} over the sealed {@link
 * SagaState}. Each method answers: "given the current state, what is the next state for this
 * trigger?" — and rejects any transition that isn't allowed.
 *
 * <ul>
 *   <li>M1: OrderPlaced → InventoryReserved → Compensating → OrderFailed (compensation path)
 *   <li>M2: adds PaymentCaptured state; happy path now confirms from PaymentCaptured
 *   <li>M3: will add ShipmentInitiated and confirm from there
 * </ul>
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

  /** M2+: inventory reserved → payment captured. */
  public static SagaState onPaymentCaptured(SagaState current) {
    return switch (current) {
      case InventoryReserved ignored -> new PaymentCaptured();
      default -> throw new IllegalSagaTransition("PAYMENT_CAPTURED", current);
    };
  }

  /**
   * Terminal confirmation. M2: from PaymentCaptured (no shipment yet). M3: will extend to accept
   * ShipmentInitiated.
   */
  public static SagaState confirm(SagaState current) {
    return switch (current) {
      case PaymentCaptured ignored -> new OrderConfirmed();
      default -> throw new IllegalSagaTransition("CONFIRM", current);
    };
  }

  /**
   * Start compensation. Valid from any state where a forward step has succeeded and needs undoing:
   * InventoryReserved (payment failed) or PaymentCaptured (shipment failed — M3).
   */
  public static SagaState beginCompensation(SagaState current) {
    return switch (current) {
      case InventoryReserved ignored -> new Compensating();
      case PaymentCaptured ignored -> new Compensating();
      default -> throw new IllegalSagaTransition("BEGIN_COMPENSATION", current);
    };
  }

  public static SagaState onInventoryReleased(SagaState current) {
    return switch (current) {
      case Compensating ignored -> new OrderFailed();
      default -> throw new IllegalSagaTransition("INVENTORY_RELEASED", current);
    };
  }

  /** M3 hook: payment refunded during compensation — still Compensating (more steps to undo). */
  public static SagaState onPaymentRefunded(SagaState current) {
    return switch (current) {
      case Compensating ignored -> new Compensating();
      default -> throw new IllegalSagaTransition("PAYMENT_REFUNDED", current);
    };
  }
}
