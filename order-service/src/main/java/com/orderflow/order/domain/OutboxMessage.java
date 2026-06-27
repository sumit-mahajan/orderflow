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
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/**
 * Transactional outbox row (F-07). Written in the SAME transaction as the saga state change, so a
 * command is never "published" without the state change committing too. A scheduled relay then ships
 * PENDING rows to Kafka and marks them SENT.
 */
@Entity
@Table(name = "outbox")
public class OutboxMessage {

  @Id
  @Column(name = "id", nullable = false)
  private UUID id;

  @Column(name = "aggregate_type", nullable = false, length = 32)
  private String aggregateType;

  @Column(name = "aggregate_id", nullable = false)
  private UUID aggregateId;

  @Column(name = "topic", nullable = false, length = 128)
  private String topic;

  @Column(name = "msg_key", nullable = false, length = 128)
  private String msgKey;

  @JdbcTypeCode(SqlTypes.JSON)
  @Column(name = "payload", nullable = false)
  private String payload;

  @JdbcTypeCode(SqlTypes.JSON)
  @Column(name = "headers")
  private String headers;

  @Enumerated(EnumType.STRING)
  @Column(name = "status", nullable = false, length = 16)
  private OutboxStatus status;

  @CreationTimestamp
  @Column(name = "created_at", nullable = false, updatable = false)
  private Instant createdAt;

  @Column(name = "published_at")
  private Instant publishedAt;

  protected OutboxMessage() {}

  public OutboxMessage(
      String aggregateType, UUID aggregateId, String topic, String msgKey, String payload) {
    this.id = UUID.randomUUID();
    this.aggregateType = aggregateType;
    this.aggregateId = aggregateId;
    this.topic = topic;
    this.msgKey = msgKey;
    this.payload = payload;
    this.status = OutboxStatus.PENDING;
  }

  public void markSent() {
    this.status = OutboxStatus.SENT;
    this.publishedAt = Instant.now();
  }

  public UUID getId() {
    return id;
  }

  public String getTopic() {
    return topic;
  }

  public String getMsgKey() {
    return msgKey;
  }

  public String getPayload() {
    return payload;
  }

  public OutboxStatus getStatus() {
    return status;
  }
}
