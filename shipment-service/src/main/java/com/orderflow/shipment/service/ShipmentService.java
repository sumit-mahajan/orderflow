package com.orderflow.shipment.service;

import com.orderflow.common.messaging.CommandMessage;
import com.orderflow.common.messaging.EventMessage;
import com.orderflow.shipment.domain.Shipment;
import com.orderflow.shipment.domain.ShipmentStatus;
import com.orderflow.shipment.messaging.Events;
import com.orderflow.shipment.repository.ShipmentRepository;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import java.util.Optional;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Create / cancel shipments (F-10 + F-11).
 *
 * <p><b>create</b> — calls the mock carrier wrapped in a Resilience4j {@code @CircuitBreaker}. When
 * the breaker is OPEN (or the carrier throws), the {@code createFallback} method saves a FAILED
 * shipment and returns {@code ShipmentFailed}, driving the orchestrator to compensate. The
 * orchestrator then issues {@code RefundPayment} followed by {@code ReleaseInventory} (full reverse
 * cascade — F-12).
 *
 * <p><b>cancel</b> — idempotent by {@code order_id}: if already CANCELLED, the retried command is
 * a no-op.
 *
 * <p>Both operations are idempotent by the {@code order_id} UNIQUE constraint (F-10).
 */
@Service
public class ShipmentService {

  private static final Logger log = LoggerFactory.getLogger(ShipmentService.class);

  private final ShipmentRepository shipments;
  private final MockCarrierClient carrier;

  public ShipmentService(ShipmentRepository shipments, MockCarrierClient carrier) {
    this.shipments = shipments;
    this.carrier = carrier;
  }

  /**
   * Attempt to create a shipment. The {@code @CircuitBreaker} wraps the carrier call; any
   * exception (including an OPEN breaker throwing {@code CallNotPermittedException}) routes to
   * {@link #createFallback}.
   */
  @Transactional
  @CircuitBreaker(name = "carrier", fallbackMethod = "createFallback")
  public EventMessage create(CommandMessage cmd) {
    UUID orderId = cmd.correlationId();

    Optional<Shipment> existing = shipments.findByOrderId(orderId);
    if (existing.isPresent()) {
      log.info("Shipment for order {} already processed — idempotent replay", orderId);
      return existingCreateResult(cmd, existing.get());
    }

    String carrierRef = carrier.createLabel(orderId, cmd.simulate());
    Shipment s = new Shipment(orderId, carrierRef, ShipmentStatus.INITIATED, null);
    shipments.save(s);
    log.info("Shipment INITIATED for order {} carrierRef={}", orderId, carrierRef);
    return Events.shipmentInitiated(cmd, carrierRef);
  }

  /**
   * Fallback invoked when the carrier throws (fail/hang mode) or the circuit breaker is OPEN.
   * Saves a FAILED row (idempotent — skips if already saved) and returns a failure event so the
   * orchestrator can compensate.
   */
  @Transactional
  public EventMessage createFallback(CommandMessage cmd, Throwable cause) {
    UUID orderId = cmd.correlationId();
    String reason = cause.getMessage();

    if (!shipments.existsByOrderId(orderId)) {
      Shipment failed = new Shipment(orderId, null, ShipmentStatus.FAILED, reason);
      shipments.save(failed);
    }

    log.warn("Shipment FAILED for order {} reason={}", orderId, reason);
    return Events.shipmentFailed(cmd, reason);
  }

  @Transactional
  public EventMessage cancel(CommandMessage cmd) {
    UUID orderId = cmd.correlationId();

    Optional<Shipment> existing = shipments.findByOrderId(orderId);
    if (existing.isPresent() && existing.get().getStatus() == ShipmentStatus.CANCELLED) {
      log.info("Shipment for order {} already cancelled — idempotent replay", orderId);
      return Events.shipmentCancelled(cmd);
    }

    existing.ifPresent(s -> {
      s.markCancelled();
      shipments.save(s);
    });

    log.info("Shipment CANCELLED for order {}", orderId);
    return Events.shipmentCancelled(cmd);
  }

  private EventMessage existingCreateResult(CommandMessage cmd, Shipment shipment) {
    return switch (shipment.getStatus()) {
      case INITIATED -> Events.shipmentInitiated(cmd, shipment.getCarrierRef());
      case FAILED -> Events.shipmentFailed(cmd, shipment.getFailureReason());
      case CANCELLED -> Events.shipmentFailed(cmd, "shipment was cancelled");
    };
  }
}
