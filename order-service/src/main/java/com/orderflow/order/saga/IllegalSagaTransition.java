package com.orderflow.order.saga;

/**
 * Thrown when a trigger is applied to a state that does not allow it (e.g. a duplicate/late event).
 * The orchestrator treats this as a no-op (logs and ignores), which is what makes redelivered events
 * safe.
 */
public class IllegalSagaTransition extends RuntimeException {
  public IllegalSagaTransition(String trigger, SagaState from) {
    super("Illegal transition '" + trigger + "' from state " + from.stateName());
  }
}
