package com.orderflow.shipment.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

/**
 * A shipment attempt for one order. Idempotency key = {@code order_id} (UNIQUE constraint).
 *
 * <p>Lifecycle: INITIATED (carrier accepted) or FAILED (carrier unavailable / CB open), then
 * optionally CANCELLED during compensation.
 */
@Entity
@Table(name = "shipment")
public class Shipment {

  @Id private UUID id;

  @Column(name = "order_id", nullable = false, unique = true)
  private UUID orderId;

  @Column(name = "carrier_ref", length = 64)
  private String carrierRef;

  @Enumerated(EnumType.STRING)
  @Column(name = "status", nullable = false, length = 16)
  private ShipmentStatus status;

  @Column(name = "failure_reason", length = 128)
  private String failureReason;

  @Column(name = "created_at", nullable = false, updatable = false)
  private Instant createdAt;

  @Column(name = "updated_at", nullable = false)
  private Instant updatedAt;

  protected Shipment() {}

  public Shipment(UUID orderId, String carrierRef, ShipmentStatus status, String failureReason) {
    this.id = UUID.randomUUID();
    this.orderId = orderId;
    this.carrierRef = carrierRef;
    this.status = status;
    this.failureReason = failureReason;
    this.createdAt = Instant.now();
    this.updatedAt = Instant.now();
  }

  public void markCancelled() {
    this.status = ShipmentStatus.CANCELLED;
    this.updatedAt = Instant.now();
  }

  public UUID getId() {
    return id;
  }

  public UUID getOrderId() {
    return orderId;
  }

  public String getCarrierRef() {
    return carrierRef;
  }

  public ShipmentStatus getStatus() {
    return status;
  }

  public String getFailureReason() {
    return failureReason;
  }
}
