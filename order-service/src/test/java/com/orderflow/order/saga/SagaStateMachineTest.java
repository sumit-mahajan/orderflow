package com.orderflow.order.saga;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.orderflow.order.saga.SagaState.Compensating;
import com.orderflow.order.saga.SagaState.InventoryReserved;
import com.orderflow.order.saga.SagaState.OrderConfirmed;
import com.orderflow.order.saga.SagaState.OrderFailed;
import com.orderflow.order.saga.SagaState.OrderPlaced;
import com.orderflow.order.saga.SagaState.PaymentCaptured;
import org.junit.jupiter.api.Test;

class SagaStateMachineTest {

  @Test
  void happyPath_placed_reserved_paymentCaptured_confirmed() {
    SagaState reserved = SagaStateMachine.onInventoryReserved(new OrderPlaced());
    assertThat(reserved).isInstanceOf(InventoryReserved.class);

    SagaState paymentCaptured = SagaStateMachine.onPaymentCaptured(reserved);
    assertThat(paymentCaptured).isInstanceOf(PaymentCaptured.class);

    SagaState confirmed = SagaStateMachine.confirm(paymentCaptured);
    assertThat(confirmed).isInstanceOf(OrderConfirmed.class);
    assertThat(confirmed.isTerminal()).isTrue();
  }

  @Test
  void compensationPath_reservedThenPaymentFailed_releasesInventory_thenFails() {
    SagaState reserved = SagaStateMachine.onInventoryReserved(new OrderPlaced());
    SagaState compensating = SagaStateMachine.beginCompensation(reserved);
    assertThat(compensating).isInstanceOf(Compensating.class);

    SagaState failed = SagaStateMachine.onInventoryReleased(compensating);
    assertThat(failed).isInstanceOf(OrderFailed.class);
    assertThat(failed.isTerminal()).isTrue();
  }

  @Test
  void compensationPath_paymentCapturedThenFailed_beginCompensation() {
    SagaState compensating = SagaStateMachine.beginCompensation(new PaymentCaptured());
    assertThat(compensating).isInstanceOf(Compensating.class);
  }

  @Test
  void reservationFailure_placed_to_failed() {
    SagaState failed = SagaStateMachine.onInventoryReservationFailed(new OrderPlaced());
    assertThat(failed).isInstanceOf(OrderFailed.class);
  }

  @Test
  void illegalTransitions_areRejected() {
    assertThatThrownBy(() -> SagaStateMachine.onInventoryReserved(new OrderConfirmed()))
        .isInstanceOf(IllegalSagaTransition.class);
    // confirm no longer valid from InventoryReserved (must go through PaymentCaptured)
    assertThatThrownBy(() -> SagaStateMachine.confirm(new InventoryReserved()))
        .isInstanceOf(IllegalSagaTransition.class);
    assertThatThrownBy(() -> SagaStateMachine.confirm(new OrderPlaced()))
        .isInstanceOf(IllegalSagaTransition.class);
    assertThatThrownBy(() -> SagaStateMachine.onInventoryReleased(new OrderPlaced()))
        .isInstanceOf(IllegalSagaTransition.class);
    assertThatThrownBy(() -> SagaStateMachine.beginCompensation(new OrderPlaced()))
        .isInstanceOf(IllegalSagaTransition.class);
    assertThatThrownBy(() -> SagaStateMachine.onPaymentCaptured(new OrderPlaced()))
        .isInstanceOf(IllegalSagaTransition.class);
  }

  @Test
  void stateName_roundTripsThroughParser() {
    SagaState parsed = SagaStates.parse(new Compensating().stateName());
    assertThat(parsed).isInstanceOf(Compensating.class);

    SagaState payment = SagaStates.parse(new PaymentCaptured().stateName());
    assertThat(payment).isInstanceOf(PaymentCaptured.class);
  }
}
