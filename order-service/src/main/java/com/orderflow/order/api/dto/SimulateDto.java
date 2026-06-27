package com.orderflow.order.api.dto;

/**
 * Optional demo failure-injection flags (F-14). Mirrors {@link
 * com.orderflow.common.messaging.Simulate}.
 *
 * @param paymentFail reject payment (M2)
 * @param shipmentMode "ok" | "fail" | "hang" (M3)
 * @param failAfterReserve force failure right after inventory reservation to demo the release
 *     compensation in M1
 */
public record SimulateDto(Boolean paymentFail, String shipmentMode, Boolean failAfterReserve) {}
