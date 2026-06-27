package com.orderflow.order.domain;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.annotations.UpdateTimestamp;
import org.hibernate.type.SqlTypes;

/** The customer's order. The saga state lives separately in {@link SagaInstance}. */
@Entity
@Table(name = "orders")
public class OrderEntity {

  @Id
  @Column(name = "order_id", nullable = false)
  private UUID orderId;

  @Column(name = "customer_id", nullable = false)
  private UUID customerId;

  @Column(name = "total_amount", nullable = false)
  private BigDecimal totalAmount;

  @Enumerated(EnumType.STRING)
  @Column(name = "status", nullable = false, length = 32)
  private OrderStatus status;

  @Column(name = "idempotency_key", nullable = false, length = 128)
  private String idempotencyKey;

  /** Demo failure-injection flags as JSON (F-14). Null when no injection requested. */
  @JdbcTypeCode(SqlTypes.JSON)
  @Column(name = "simulate")
  private String simulate;

  @OneToMany(mappedBy = "order", cascade = CascadeType.ALL, fetch = FetchType.LAZY)
  private List<OrderItemEntity> items = new ArrayList<>();

  @CreationTimestamp
  @Column(name = "created_at", nullable = false, updatable = false)
  private Instant createdAt;

  @UpdateTimestamp
  @Column(name = "updated_at", nullable = false)
  private Instant updatedAt;

  @Column(name = "deleted_at")
  private Instant deletedAt;

  protected OrderEntity() {}

  public OrderEntity(
      UUID orderId,
      UUID customerId,
      BigDecimal totalAmount,
      String idempotencyKey,
      String simulate) {
    this.orderId = orderId;
    this.customerId = customerId;
    this.totalAmount = totalAmount;
    this.idempotencyKey = idempotencyKey;
    this.simulate = simulate;
    this.status = OrderStatus.PLACED;
  }

  public void addItem(OrderItemEntity item) {
    item.setOrder(this);
    this.items.add(item);
  }

  public void markConfirmed() {
    this.status = OrderStatus.CONFIRMED;
  }

  public void markFailed() {
    this.status = OrderStatus.FAILED;
  }

  public UUID getOrderId() {
    return orderId;
  }

  public UUID getCustomerId() {
    return customerId;
  }

  public BigDecimal getTotalAmount() {
    return totalAmount;
  }

  public OrderStatus getStatus() {
    return status;
  }

  public String getIdempotencyKey() {
    return idempotencyKey;
  }

  public String getSimulate() {
    return simulate;
  }

  public List<OrderItemEntity> getItems() {
    return items;
  }

  public Instant getCreatedAt() {
    return createdAt;
  }

  public Instant getUpdatedAt() {
    return updatedAt;
  }
}
