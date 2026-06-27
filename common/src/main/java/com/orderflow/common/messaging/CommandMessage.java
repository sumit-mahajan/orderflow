package com.orderflow.common.messaging;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * One concrete type per command topic. A {@link CommandType} discriminator avoids polymorphic Kafka
 * deserialization — the consumer always deserializes to this single type, then switches on {@code
 * type}. Nullable fields are populated only when relevant to the command.
 *
 * @param messageId unique id for this message (consumer idempotency / dedup)
 * @param correlationId the orderId, threaded through all services + logs + traces
 * @param sagaId the saga instance id
 * @param type which command
 * @param items inventory lines (RESERVE/RELEASE)
 * @param amount payment amount (CAPTURE/REFUND, M2)
 * @param simulate failure-injection flags (F-14)
 * @param occurredAt when the orchestrator emitted it
 */
public record CommandMessage(
    UUID messageId,
    UUID correlationId,
    UUID sagaId,
    CommandType type,
    List<LineItem> items,
    BigDecimal amount,
    Simulate simulate,
    Instant occurredAt) {}
