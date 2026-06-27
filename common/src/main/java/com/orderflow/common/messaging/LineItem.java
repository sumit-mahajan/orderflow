package com.orderflow.common.messaging;

/**
 * A single order line in a Kafka message. Record = immutable data carrier (≈ C# record).
 *
 * @param sku product identifier
 * @param qty quantity (> 0)
 */
public record LineItem(String sku, int qty) {}
