package com.orderflow.shipment.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.orderflow.common.messaging.CommandMessage;
import com.orderflow.common.messaging.CommandType;
import com.orderflow.common.messaging.EventMessage;
import com.orderflow.common.messaging.EventType;
import com.orderflow.common.messaging.Simulate;
import com.orderflow.shipment.domain.Shipment;
import com.orderflow.shipment.domain.ShipmentStatus;
import com.orderflow.shipment.repository.ShipmentRepository;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * Unit tests for ShipmentService (F-10). Mockito only — no DB or Kafka needed.
 * The circuit breaker annotation is not active without a Spring context, so
 * fallback behaviour is tested via {@link ShipmentService#createFallback} directly.
 */
@ExtendWith(MockitoExtension.class)
class ShipmentServiceTest {

  @Mock private ShipmentRepository shipments;
  @Mock private MockCarrierClient carrier;
  @InjectMocks private ShipmentService service;

  private static CommandMessage createCmd(UUID orderId, Simulate simulate) {
    return new CommandMessage(
        UUID.randomUUID(),
        orderId,
        UUID.randomUUID(),
        CommandType.CREATE_SHIPMENT,
        null,
        null,
        simulate,
        Instant.now());
  }

  private static CommandMessage cancelCmd(UUID orderId) {
    return new CommandMessage(
        UUID.randomUUID(),
        orderId,
        UUID.randomUUID(),
        CommandType.CANCEL_SHIPMENT,
        null,
        null,
        null,
        Instant.now());
  }

  @Test
  void create_okMode_returnsInitiated() {
    UUID orderId = UUID.randomUUID();
    when(shipments.findByOrderId(orderId)).thenReturn(Optional.empty());
    when(carrier.createLabel(eq(orderId), any())).thenReturn("LABEL-ABCD1234");

    EventMessage result = service.create(createCmd(orderId, null));

    assertThat(result.type()).isEqualTo(EventType.SHIPMENT_INITIATED);
    assertThat(result.carrierRef()).isEqualTo("LABEL-ABCD1234");
    verify(shipments).save(any(Shipment.class));
  }

  @Test
  void create_idempotent_existingInitiated_doesNotSaveAgain() {
    UUID orderId = UUID.randomUUID();
    Shipment existing = new Shipment(orderId, "LABEL-EXISTING", ShipmentStatus.INITIATED, null);
    when(shipments.findByOrderId(orderId)).thenReturn(Optional.of(existing));

    EventMessage result = service.create(createCmd(orderId, null));

    assertThat(result.type()).isEqualTo(EventType.SHIPMENT_INITIATED);
    assertThat(result.carrierRef()).isEqualTo("LABEL-EXISTING");
    verify(shipments, never()).save(any(Shipment.class));
  }

  @Test
  void createFallback_savesFailedRecord_returnsFailedEvent() {
    UUID orderId = UUID.randomUUID();
    CommandMessage cmd = createCmd(orderId, new Simulate(null, "fail", null));
    when(shipments.existsByOrderId(orderId)).thenReturn(false);

    EventMessage result = service.createFallback(cmd, new CarrierException("injected failure"));

    assertThat(result.type()).isEqualTo(EventType.SHIPMENT_FAILED);
    assertThat(result.reason()).isEqualTo("injected failure");
    verify(shipments).save(any(Shipment.class));
  }

  @Test
  void createFallback_idempotent_alreadySavedFailed_doesNotSaveAgain() {
    UUID orderId = UUID.randomUUID();
    CommandMessage cmd = createCmd(orderId, null);
    when(shipments.existsByOrderId(orderId)).thenReturn(true);

    EventMessage result = service.createFallback(cmd, new CarrierException("cb open"));

    assertThat(result.type()).isEqualTo(EventType.SHIPMENT_FAILED);
    verify(shipments, never()).save(any(Shipment.class));
  }

  @Test
  void cancel_initiatedShipment_cancelsAndReturnsEvent() {
    UUID orderId = UUID.randomUUID();
    Shipment initiated = new Shipment(orderId, "LABEL-X", ShipmentStatus.INITIATED, null);
    when(shipments.findByOrderId(orderId)).thenReturn(Optional.of(initiated));

    EventMessage result = service.cancel(cancelCmd(orderId));

    assertThat(result.type()).isEqualTo(EventType.SHIPMENT_CANCELLED);
    assertThat(initiated.getStatus()).isEqualTo(ShipmentStatus.CANCELLED);
    verify(shipments).save(initiated);
  }

  @Test
  void cancel_idempotent_alreadyCancelled_doesNotSaveAgain() {
    UUID orderId = UUID.randomUUID();
    Shipment cancelled = new Shipment(orderId, "LABEL-X", ShipmentStatus.CANCELLED, null);
    when(shipments.findByOrderId(orderId)).thenReturn(Optional.of(cancelled));

    EventMessage result = service.cancel(cancelCmd(orderId));

    assertThat(result.type()).isEqualTo(EventType.SHIPMENT_CANCELLED);
    verify(shipments, never()).save(any(Shipment.class));
  }
}
