package com.orderflow.common.messaging;

/**
 * Kafka topic names. Single source of truth shared by producers and consumers.
 *
 * <p>Convention: {@code orderflow.commands.<service>} (orchestrator -> service) and {@code
 * orderflow.events.<service>} (service -> orchestrator). Messages are keyed by orderId so all
 * messages for one order stay ordered on the same partition.
 */
public final class Topics {

  private Topics() {}

  public static final String COMMANDS_INVENTORY = "orderflow.commands.inventory";
  public static final String EVENTS_INVENTORY = "orderflow.events.inventory";

  public static final String COMMANDS_PAYMENT = "orderflow.commands.payment";
  public static final String EVENTS_PAYMENT = "orderflow.events.payment";

  public static final String COMMANDS_SHIPMENT = "orderflow.commands.shipment";
  public static final String EVENTS_SHIPMENT = "orderflow.events.shipment";
}
