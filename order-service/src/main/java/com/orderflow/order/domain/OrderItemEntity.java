package com.orderflow.order.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.util.UUID;

/**
 * A line on an order. NOTE (M1): per-item pricing isn't supplied by the API (only an order total),
 * so {@code unit_price} is recorded as 0 here; the authoritative charge is {@code orders.total_amount}.
 * Itemized pricing is future work.
 */
@Entity
@Table(name = "order_items")
public class OrderItemEntity {

  @Id
  @Column(name = "id", nullable = false)
  private UUID id;

  @ManyToOne
  @JoinColumn(name = "order_id", nullable = false)
  private OrderEntity order;

  @Column(name = "sku", nullable = false, length = 64)
  private String sku;

  @Column(name = "qty", nullable = false)
  private int qty;

  @Column(name = "unit_price", nullable = false)
  private BigDecimal unitPrice;

  protected OrderItemEntity() {}

  public OrderItemEntity(String sku, int qty, BigDecimal unitPrice) {
    this.id = UUID.randomUUID();
    this.sku = sku;
    this.qty = qty;
    this.unitPrice = unitPrice;
  }

  void setOrder(OrderEntity order) {
    this.order = order;
  }

  public UUID getId() {
    return id;
  }

  public String getSku() {
    return sku;
  }

  public int getQty() {
    return qty;
  }

  public BigDecimal getUnitPrice() {
    return unitPrice;
  }
}
