package com.orderflow.order.saga;

/** Rehydrates a {@link SagaState} from its stored {@code stateName()}. */
public final class SagaStates {

  private SagaStates() {}

  public static SagaState parse(String name) {
    return switch (name) {
      case "OrderPlaced" -> new SagaState.OrderPlaced();
      case "InventoryReserved" -> new SagaState.InventoryReserved();
      case "PaymentCaptured" -> new SagaState.PaymentCaptured();
      case "ShipmentInitiated" -> new SagaState.ShipmentInitiated();
      case "OrderConfirmed" -> new SagaState.OrderConfirmed();
      case "Compensating" -> new SagaState.Compensating();
      case "OrderFailed" -> new SagaState.OrderFailed();
      default -> throw new IllegalArgumentException("Unknown saga state: " + name);
    };
  }
}
