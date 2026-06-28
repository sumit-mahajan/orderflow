package com.orderflow.order.messaging;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.orderflow.common.messaging.CommandMessage;
import com.orderflow.order.domain.OutboxMessage;
import com.orderflow.order.repository.OutboxRepository;
import io.micrometer.tracing.Span;
import io.micrometer.tracing.TraceContext;
import io.micrometer.tracing.Tracer;
import java.util.Map;
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
  private final Tracer tracer;

  public OutboxWriter(OutboxRepository outbox, ObjectMapper json, Tracer tracer) {
    this.outbox = outbox;
    this.json = json;
    this.tracer = tracer;
  }

  public void enqueueCommand(String topic, CommandMessage command) {
    String payload = serialize(command);
    String headers = traceHeadersAsJson();
    outbox.save(
        new OutboxMessage(
            "ORDER",
            command.correlationId(),
            topic,
            command.correlationId().toString(),
            payload,
            headers));
  }

  private String serialize(CommandMessage command) {
    try {
      return json.writeValueAsString(command);
    } catch (JsonProcessingException e) {
      throw new IllegalStateException("Failed to serialize command " + command.type(), e);
    }
  }

  private String traceHeadersAsJson() {
    Span span = tracer.currentSpan();
    if (span == null) {
      return null;
    }
    TraceContext context = span.context();
    if (context == null || context.traceId() == null || context.spanId() == null) {
      return null;
    }
    String traceparent =
        "00-%s-%s-%s"
            .formatted(
                context.traceId(),
                context.spanId(),
                Boolean.TRUE.equals(context.sampled()) ? "01" : "00");
    try {
      return json.writeValueAsString(Map.of("traceparent", traceparent));
    } catch (JsonProcessingException e) {
      throw new IllegalStateException("Failed to serialize outbox headers", e);
    }
  }
}
