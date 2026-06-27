package com.orderflow.common.messaging;

/**
 * Demo failure-injection flags (F-14), carried from the order request through the saga commands so
 * all demo scenarios are drivable from the UI.
 *
 * @param paymentFail if true, payment-service rejects the capture (M2)
 * @param shipmentMode one of "ok" | "fail" | "hang" (M3)
 * @param failAfterReserve if true, the orchestrator forces failure right after inventory is
 *     reserved, so the release compensation can be demonstrated in M1 (before payment exists)
 */
public record Simulate(Boolean paymentFail, String shipmentMode, Boolean failAfterReserve) {}
