package com.orderflow.inventory.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

/**
 * A reservation of {@code qty} of {@code sku} for an order. The UNIQUE(order_id, sku) constraint is
 * the idempotency guard: a redelivered reserve command cannot create a second reservation, and
 * release flips status to RELEASED (a second release becomes a no-op).
 */
@Entity
@Table(name = "inventory_reservation")
public class InventoryReservation {

  @Id
  @Column(name = "id", nullable = false)
  private UUID id;

  @Column(name = "order_id", nullable = false)
  private UUID orderId;

  @Column(name = "sku", nullable = false, length = 64)
  private String sku;

  @Column(name = "qty", nullable = false)
  private int qty;

  @Enumerated(EnumType.STRING)
  @Column(name = "status", nullable = false, length = 16)
  private ReservationStatus status;

  @CreationTimestamp
  @Column(name = "created_at", nullable = false, updatable = false)
  private Instant createdAt;

  @UpdateTimestamp
  @Column(name = "updated_at", nullable = false)
  private Instant updatedAt;

  protected InventoryReservation() {}

  public InventoryReservation(UUID orderId, String sku, int qty) {
    this.id = UUID.randomUUID();
    this.orderId = orderId;
    this.sku = sku;
    this.qty = qty;
    this.status = ReservationStatus.RESERVED;
  }

  public void markReleased() {
    this.status = ReservationStatus.RELEASED;
  }

  public UUID getId() {
    return id;
  }

  public UUID getOrderId() {
    return orderId;
  }

  public String getSku() {
    return sku;
  }

  public int getQty() {
    return qty;
  }

  public ReservationStatus getStatus() {
    return status;
  }
}
