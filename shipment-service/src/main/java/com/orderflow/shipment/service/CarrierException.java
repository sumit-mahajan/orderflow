package com.orderflow.shipment.service;

/** Thrown by {@link MockCarrierClient} for injected fail or hang scenarios. */
public class CarrierException extends RuntimeException {

  public CarrierException(String message) {
    super(message);
  }
}
