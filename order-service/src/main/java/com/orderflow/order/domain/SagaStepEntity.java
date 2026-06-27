package com.orderflow.order.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import org.hibernate.annotations.CreationTimestamp;

/** Append-only audit row: one per step attempt (forward or compensation). Powers the timeline. */
@Entity
@Table(name = "saga_step")
public class SagaStepEntity {

  @Id
  @Column(name = "id", nullable = false)
  private UUID id;

  @Column(name = "saga_id", nullable = false)
  private UUID sagaId;

  @Enumerated(EnumType.STRING)
  @Column(name = "step", nullable = false, length = 32)
  private StepEnums.StepName step;

  @Enumerated(EnumType.STRING)
  @Column(name = "direction", nullable = false, length = 16)
  private StepEnums.Direction direction;

  @Enumerated(EnumType.STRING)
  @Column(name = "status", nullable = false, length = 16)
  private StepEnums.Status status;

  @Column(name = "attempt", nullable = false)
  private int attempt;

  @Column(name = "correlation_id", nullable = false)
  private UUID correlationId;

  @Column(name = "error")
  private String error;

  @CreationTimestamp
  @Column(name = "created_at", nullable = false, updatable = false)
  private Instant createdAt;

  protected SagaStepEntity() {}

  public SagaStepEntity(
      UUID sagaId,
      StepEnums.StepName step,
      StepEnums.Direction direction,
      StepEnums.Status status,
      UUID correlationId,
      String error) {
    this.id = UUID.randomUUID();
    this.sagaId = sagaId;
    this.step = step;
    this.direction = direction;
    this.status = status;
    this.attempt = 1;
    this.correlationId = correlationId;
    this.error = error;
  }

  public UUID getId() {
    return id;
  }

  public StepEnums.StepName getStep() {
    return step;
  }

  public StepEnums.Direction getDirection() {
    return direction;
  }

  public StepEnums.Status getStatus() {
    return status;
  }

  public String getError() {
    return error;
  }

  public Instant getCreatedAt() {
    return createdAt;
  }
}
