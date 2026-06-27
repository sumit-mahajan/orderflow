package com.orderflow.inventory.service;

/**
 * Thrown when a SKU cannot be reserved (insufficient stock or unknown SKU). Thrown from inside the
 * {@code @Transactional} reserve method so the whole reservation rolls back atomically; the listener
 * catches it and emits an INVENTORY_RESERVATION_FAILED event.
 */
public class InsufficientStockException extends RuntimeException {
  public InsufficientStockException(String sku) {
    super("Insufficient stock or unknown SKU: " + sku);
  }
}
