package com.orderflow.order.domain;

/** Small enums for the saga step audit log. Grouped in one file as they're tiny + related. */
public final class StepEnums {

  private StepEnums() {}

  public enum StepName {
    INVENTORY,
    PAYMENT,
    SHIPMENT
  }

  public enum Direction {
    FORWARD,
    COMPENSATION
  }

  public enum Status {
    STARTED,
    SUCCESS,
    FAILED
  }
}
