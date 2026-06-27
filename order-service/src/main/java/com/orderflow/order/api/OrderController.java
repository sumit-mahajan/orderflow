package com.orderflow.order.api;

import com.orderflow.order.api.dto.OrderDetailResponse;
import com.orderflow.order.api.dto.OrderResponse;
import com.orderflow.order.api.dto.OrderSummary;
import com.orderflow.order.api.dto.PlaceOrderRequest;
import com.orderflow.order.service.OrderQueryService;
import com.orderflow.order.service.PlaceOrderResult;
import com.orderflow.order.service.PlaceOrderService;
import com.orderflow.order.sse.SseService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

/** REST + SSE boundary. Validates input, delegates to use cases — no business logic here. */
@RestController
@RequestMapping("/api/orders")
@Validated
public class OrderController {

  private final PlaceOrderService placeOrderService;
  private final OrderQueryService queryService;
  private final SseService sseService;

  public OrderController(
      PlaceOrderService placeOrderService,
      OrderQueryService queryService,
      SseService sseService) {
    this.placeOrderService = placeOrderService;
    this.queryService = queryService;
    this.sseService = sseService;
  }

  @PostMapping
  public ResponseEntity<OrderResponse> place(
      @RequestHeader("Idempotency-Key") @NotBlank String idempotencyKey,
      @Valid @RequestBody PlaceOrderRequest request) {
    PlaceOrderResult result = placeOrderService.place(idempotencyKey, request);
    return ResponseEntity.status(HttpStatus.CREATED)
        .body(new OrderResponse(result.orderId(), result.status().name()));
  }

  @GetMapping("/{id}")
  public OrderDetailResponse get(@PathVariable("id") UUID id) {
    return queryService.detail(id);
  }

  @GetMapping
  public Page<OrderSummary> list(
      @RequestParam(name = "page", defaultValue = "0") int page,
      @RequestParam(name = "size", defaultValue = "20") int size) {
    return queryService.list(page, size);
  }

  @GetMapping(value = "/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
  public SseEmitter stream() {
    return sseService.subscribe();
  }
}
