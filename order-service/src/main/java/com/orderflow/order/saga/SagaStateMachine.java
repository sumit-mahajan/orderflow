package com.orderflow.order.saga;

import com.orderflow.order.saga.SagaState.Compensating;
import com.orderflow.order.saga.SagaState.InventoryReserved;
import com.orderflow.order.saga.SagaState.OrderConfirmed;
import com.orderflow.order.saga.SagaState.OrderFailed;
import com.orderflow.order.saga.SagaState.OrderPlaced;
import com.orderflow.order.saga.SagaState.PaymentCaptured;
import com.orderflow.order.saga.SagaState.ShipmentInitiated;

/**
 * Legal saga transitions, expressed as pattern-matched {@code switch} over the sealed {@link
 * SagaState}. Each method answers: "given the current state, what is the next state for this
 * trigger?" — and rejects any transition that isn't allowed.
 *
 * <ul>
 *   <li>M1: OrderPlaced → InventoryReserved → Compensating → OrderFailed (compensation path)
 *   <li>M2: adds PaymentCaptured; compensation from InventoryReserved (payment fail) → release →
 *       OrderFailed
 *   <li>M3: adds ShipmentInitiated; confirm only from ShipmentInitiated; compensation from
 *       PaymentCaptured (shipment fail) → refund payment → release inventory → OrderFailed
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

  /** M3: payment captured → shipment initiated. */
  public static SagaState onShipmentInitiated(SagaState current) {
    return switch (current) {
      case PaymentCaptured ignored -> new ShipmentInitiated();
      default -> throw new IllegalSagaTransition("SHIPMENT_INITIATED", current);
    };
  }

  /**
   * Terminal confirmation. M3: only from ShipmentInitiated (full 3-step happy path).
   */
  public static SagaState confirm(SagaState current) {
    return switch (current) {
      case ShipmentInitiated ignored -> new OrderConfirmed();
      default -> throw new IllegalSagaTransition("CONFIRM", current);
    };
  }

  /**
   * Start compensation. Valid from any forward state that has succeeded and needs undoing:
   *
   * <ul>
   *   <li>InventoryReserved — payment failed (M2): issue release inventory.
   *   <li>PaymentCaptured — shipment failed before initiation (M3): issue refund payment.
   * </ul>
   */
  public static SagaState beginCompensation(SagaState current) {
    return switch (current) {
      case InventoryReserved ignored -> new Compensating();
      case PaymentCaptured ignored -> new Compensating();
      default -> throw new IllegalSagaTransition("BEGIN_COMPENSATION", current);
    };
  }

  /** Payment refunded during compensation — still Compensating (inventory release follows). */
  public static SagaState onPaymentRefunded(SagaState current) {
    return switch (current) {
      case Compensating ignored -> new Compensating();
      default -> throw new IllegalSagaTransition("PAYMENT_REFUNDED", current);
    };
  }

  public static SagaState onInventoryReleased(SagaState current) {
    return switch (current) {
      case Compensating ignored -> new OrderFailed();
      default -> throw new IllegalSagaTransition("INVENTORY_RELEASED", current);
    };
  }
}
