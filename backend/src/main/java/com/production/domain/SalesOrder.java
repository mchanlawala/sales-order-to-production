package com.production.domain;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class SalesOrder {
    private String salesOrderId;
    private String customerCode;
    private String material;
    private Integer quantity;
    private LocalDateTime requestedDeliveryDate;
    private LocalDateTime orderReceived;
    private String variantConfig; // e.g., "545W / MONO / HALF / BLACK"
    private Integer priority;      // 1 = highest
    private String status;
}