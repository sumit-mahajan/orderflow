package com.orderflow.order.saga;

/**
 * The saga state model — the interview centerpiece (F-03).
 *
 * <p>A <b>sealed interface</b> means the set of states is closed and known at compile time, so a
 * {@code switch} over it can be checked for exhaustiveness. Each state is a {@code record} (an
 * immutable data carrier). This is the Java equivalent of a closed/discriminated-union type — in C#
 * you'd model it as a sealed class hierarchy.
 *
 * <p>Permitted subtypes are nested, so the {@code permits} clause is inferred. Forward states for
 * M2/M3 ({@code PaymentCaptured}, {@code ShipmentInitiated}) are declared now so the type is stable.
 */
public sealed interface SagaState {

  record OrderPlaced() implements SagaState {}

  record InventoryReserved() implements SagaState {}

  record PaymentCaptured() implements SagaState {}

  record ShipmentInitiated() implements SagaState {}

  record OrderConfirmed() implements SagaState {}

  record Compensating() implements SagaState {}

  record OrderFailed() implements SagaState {}

  /** Stored in {@code saga_instance.current_state}; also used to rehydrate via {@link SagaStates}. */
  default String stateName() {
    return getClass().getSimpleName();
  }

  default boolean isTerminal() {
    return this instanceof OrderConfirmed || this instanceof OrderFailed;
  }
}
