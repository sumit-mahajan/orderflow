package com.orderflow.payment.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * A payment attempt for one order. Idempotency key = {@code order_id} (UNIQUE constraint).
 *
 * <p>Lifecycle: captured → (on shipment fail in M3) refunded; or failed on first attempt.
 */
@Entity
@Table(name = "payment")
public class Payment {

  @Id private UUID id;

  @Column(name = "order_id", nullable = false, unique = true)
  private UUID orderId;

  @Column(name = "amount", nullable = false)
  private BigDecimal amount;

  @Enumerated(EnumType.STRING)
  @Column(name = "status", nullable = false, length = 16)
  private PaymentStatus status;

  @Column(name = "failure_reason", length = 128)
  private String failureReason;

  @Column(name = "created_at", nullable = false, updatable = false)
  private Instant createdAt;

  @Column(name = "updated_at", nullable = false)
  private Instant updatedAt;

  protected Payment() {}

  public Payment(UUID orderId, BigDecimal amount, PaymentStatus status, String failureReason) {
    this.id = UUID.randomUUID();
    this.orderId = orderId;
    this.amount = amount;
    this.status = status;
    this.failureReason = failureReason;
    this.createdAt = Instant.now();
    this.updatedAt = Instant.now();
  }

  public void markRefunded() {
    this.status = PaymentStatus.REFUNDED;
    this.updatedAt = Instant.now();
  }

  public UUID getId() {
    return id;
  }

  public UUID getOrderId() {
    return orderId;
  }

  public BigDecimal getAmount() {
    return amount;
  }

  public PaymentStatus getStatus() {
    return status;
  }

  public String getFailureReason() {
    return failureReason;
  }
}
