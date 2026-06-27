package com.orderflow.order.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.Instant;
import java.util.UUID;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

/**
 * Durable saga state — the system of record (D-02). {@code current_state} holds the {@link
 * com.orderflow.order.saga.SagaState} name.
 *
 * <p>{@code @Version} gives optimistic locking (D-08): if two threads load the same saga and both
 * try to advance it, the second commit fails with an OptimisticLockException and is retried. This is
 * the correctness backstop behind the Redis transition lock. ≈ EF Core {@code [Timestamp]}/rowversion.
 */
@Entity
@Table(name = "saga_instance")
public class SagaInstance {

  @Id
  @Column(name = "saga_id", nullable = false)
  private UUID sagaId;

  @Column(name = "order_id", nullable = false, unique = true)
  private UUID orderId;

  @Column(name = "current_state", nullable = false, length = 32)
  private String currentState;

  @Version
  @Column(name = "version", nullable = false)
  private int version;

  @CreationTimestamp
  @Column(name = "created_at", nullable = false, updatable = false)
  private Instant createdAt;

  @UpdateTimestamp
  @Column(name = "updated_at", nullable = false)
  private Instant updatedAt;

  protected SagaInstance() {}

  public SagaInstance(UUID orderId, String initialState) {
    this.sagaId = UUID.randomUUID();
    this.orderId = orderId;
    this.currentState = initialState;
  }

  public void setCurrentState(String currentState) {
    this.currentState = currentState;
  }

  public UUID getSagaId() {
    return sagaId;
  }

  public UUID getOrderId() {
    return orderId;
  }

  public String getCurrentState() {
    return currentState;
  }

  public int getVersion() {
    return version;
  }
}
