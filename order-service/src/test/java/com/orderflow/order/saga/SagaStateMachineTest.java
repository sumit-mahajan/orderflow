package com.orderflow.order.saga;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.orderflow.order.saga.SagaState.Compensating;
import com.orderflow.order.saga.SagaState.InventoryReserved;
import com.orderflow.order.saga.SagaState.OrderConfirmed;
import com.orderflow.order.saga.SagaState.OrderFailed;
import com.orderflow.order.saga.SagaState.OrderPlaced;
import com.orderflow.order.saga.SagaState.PaymentCaptured;
import com.orderflow.order.saga.SagaState.ShipmentInitiated;
import org.junit.jupiter.api.Test;

class SagaStateMachineTest {

  @Test
  void happyPath_placed_reserved_paymentCaptured_shipmentInitiated_confirmed() {
    SagaState reserved = SagaStateMachine.onInventoryReserved(new OrderPlaced());
    assertThat(reserved).isInstanceOf(InventoryReserved.class);

    SagaState paymentCaptured = SagaStateMachine.onPaymentCaptured(reserved);
    assertThat(paymentCaptured).isInstanceOf(PaymentCaptured.class);

    SagaState shipmentInitiated = SagaStateMachine.onShipmentInitiated(paymentCaptured);
    assertThat(shipmentInitiated).isInstanceOf(ShipmentInitiated.class);

    SagaState confirmed = SagaStateMachine.confirm(shipmentInitiated);
    assertThat(confirmed).isInstanceOf(OrderConfirmed.class);
    assertThat(confirmed.isTerminal()).isTrue();
  }

  @Test
  void compensationPath_paymentFailed_releasesInventory_thenFails() {
    // M2: payment fails → beginCompensation from InventoryReserved → release → OrderFailed
    SagaState reserved = SagaStateMachine.onInventoryReserved(new OrderPlaced());
    SagaState compensating = SagaStateMachine.beginCompensation(reserved);
    assertThat(compensating).isInstanceOf(Compensating.class);

    SagaState failed = SagaStateMachine.onInventoryReleased(compensating);
    assertThat(failed).isInstanceOf(OrderFailed.class);
    assertThat(failed.isTerminal()).isTrue();
  }

  @Test
  void compensationPath_shipmentFailed_refundPayment_releaseInventory_thenFails() {
    // M3: shipment fails → beginCompensation from PaymentCaptured → refund → release → OrderFailed
    SagaState paymentCaptured = SagaStateMachine.onPaymentCaptured(new InventoryReserved());
    SagaState compensating = SagaStateMachine.beginCompensation(paymentCaptured);
    assertThat(compensating).isInstanceOf(Compensating.class);

    // Payment refunded — still compensating (inventory release follows)
    SagaState stillCompensating = SagaStateMachine.onPaymentRefunded(compensating);
    assertThat(stillCompensating).isInstanceOf(Compensating.class);

    SagaState failed = SagaStateMachine.onInventoryReleased(stillCompensating);
    assertThat(failed).isInstanceOf(OrderFailed.class);
    assertThat(failed.isTerminal()).isTrue();
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
    // M3: confirm only valid from ShipmentInitiated
    assertThatThrownBy(() -> SagaStateMachine.confirm(new PaymentCaptured()))
        .isInstanceOf(IllegalSagaTransition.class);
    assertThatThrownBy(() -> SagaStateMachine.confirm(new InventoryReserved()))
        .isInstanceOf(IllegalSagaTransition.class);
    assertThatThrownBy(() -> SagaStateMachine.confirm(new OrderPlaced()))
        .isInstanceOf(IllegalSagaTransition.class);
    assertThatThrownBy(() -> SagaStateMachine.onInventoryReleased(new OrderPlaced()))
        .isInstanceOf(IllegalSagaTransition.class);
    assertThatThrownBy(() -> SagaStateMachine.beginCompensation(new OrderPlaced()))
        .isInstanceOf(IllegalSagaTransition.class);
    assertThatThrownBy(() -> SagaStateMachine.onShipmentInitiated(new InventoryReserved()))
        .isInstanceOf(IllegalSagaTransition.class);
  }

  @Test
  void stateName_roundTripsThroughParser() {
    SagaState parsed = SagaStates.parse(new Compensating().stateName());
    assertThat(parsed).isInstanceOf(Compensating.class);

    SagaState shipment = SagaStates.parse(new ShipmentInitiated().stateName());
    assertThat(shipment).isInstanceOf(ShipmentInitiated.class);

    SagaState payment = SagaStates.parse(new PaymentCaptured().stateName());
    assertThat(payment).isInstanceOf(PaymentCaptured.class);
  }
}
