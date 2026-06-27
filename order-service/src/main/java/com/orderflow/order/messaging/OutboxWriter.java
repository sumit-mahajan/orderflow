package com.orderflow.order.messaging;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.orderflow.common.messaging.CommandMessage;
import com.orderflow.order.domain.OutboxMessage;
import com.orderflow.order.repository.OutboxRepository;
import org.springframework.stereotype.Component;

/**
 * Writes a command to the outbox (F-07). Call this INSIDE the same transaction as the saga state
 * change so the message and the state are committed atomically. The {@link OutboxRelay} ships it to
 * Kafka afterwards.
 */
@Component
public class OutboxWriter {

  private final OutboxRepository outbox;
  private final ObjectMapper json;

  public OutboxWriter(OutboxRepository outbox, ObjectMapper json) {
    this.outbox = outbox;
    this.json = json;
  }

  public void enqueueCommand(String topic, CommandMessage command) {
    String payload = serialize(command);
    outbox.save(
        new OutboxMessage(
            "ORDER", command.correlationId(), topic, command.correlationId().toString(), payload));
  }

  private String serialize(CommandMessage command) {
    try {
      return json.writeValueAsString(command);
    } catch (JsonProcessingException e) {
      throw new IllegalStateException("Failed to serialize command " + command.type(), e);
    }
  }
}
