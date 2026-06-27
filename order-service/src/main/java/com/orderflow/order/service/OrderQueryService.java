package com.orderflow.order.service;

import com.orderflow.order.api.dto.OrderDetailResponse;
import com.orderflow.order.api.dto.OrderSummary;
import com.orderflow.order.domain.OrderEntity;
import com.orderflow.order.domain.SagaInstance;
import com.orderflow.order.repository.OrderRepository;
import com.orderflow.order.repository.SagaInstanceRepository;
import com.orderflow.order.repository.SagaStepRepository;
import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Read-side queries for the API + SSE snapshots. */
@Service
@Transactional(readOnly = true)
public class OrderQueryService {

  private final OrderRepository orders;
  private final SagaInstanceRepository sagas;
  private final SagaStepRepository steps;

  public OrderQueryService(
      OrderRepository orders, SagaInstanceRepository sagas, SagaStepRepository steps) {
    this.orders = orders;
    this.sagas = sagas;
    this.steps = steps;
  }

  public OrderDetailResponse detail(UUID orderId) {
    OrderEntity order =
        orders.findById(orderId).orElseThrow(() -> new OrderNotFoundException(orderId));
    SagaInstance saga = sagas.findByOrderId(orderId).orElse(null);
    String state = saga != null ? saga.getCurrentState() : "OrderPlaced";

    List<OrderDetailResponse.ItemView> items =
        order.getItems().stream()
            .map(i -> new OrderDetailResponse.ItemView(i.getSku(), i.getQty(), i.getUnitPrice()))
            .toList();

    List<OrderDetailResponse.StepView> stepViews =
        saga == null
            ? List.of()
            : steps.findBySagaIdOrderByCreatedAtAsc(saga.getSagaId()).stream()
                .map(
                    s ->
                        new OrderDetailResponse.StepView(
                            s.getStep().name(),
                            s.getDirection().name(),
                            s.getStatus().name(),
                            1,
                            s.getError(),
                            s.getCreatedAt()))
                .toList();

    return new OrderDetailResponse(
        order.getOrderId(),
        order.getCustomerId(),
        order.getTotalAmount(),
        order.getStatus().name(),
        state,
        items,
        stepViews,
        order.getCreatedAt(),
        order.getUpdatedAt());
  }

  public Page<OrderSummary> list(int page, int size) {
    return orders
        .findAll(PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt")))
        .map(this::toSummary);
  }

  private OrderSummary toSummary(OrderEntity order) {
    String state =
        sagas
            .findByOrderId(order.getOrderId())
            .map(SagaInstance::getCurrentState)
            .orElse("OrderPlaced");
    return new OrderSummary(
        order.getOrderId(),
        order.getCustomerId(),
        order.getTotalAmount(),
        order.getStatus().name(),
        state,
        order.getUpdatedAt());
  }
}
