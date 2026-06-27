package com.orderflow.common.messaging;

import java.time.Instant;
import java.util.UUID;

/**
 * One concrete type per event topic. An {@link EventType} discriminator avoids polymorphic Kafka
 * deserialization. Nullable fields are populated only when relevant to the event.
 *
 * @param messageId unique id for this message
 * @param correlationId the orderId
 * @param sagaId the saga instance id
 * @param type which result
 * @param reason failure reason (on *_FAILED events)
 * @param carrierRef mock carrier label id (SHIPMENT_INITIATED, M3)
 * @param occurredAt when the participant emitted it
 */
public record EventMessage(
    UUID messageId,
    UUID correlationId,
    UUID sagaId,
    EventType type,
    String reason,
    String carrierRef,
    Instant occurredAt) {}
