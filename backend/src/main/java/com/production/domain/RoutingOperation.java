package com.production.domain;

import lombok.Data;

@Data
public class RoutingOperation {
    private String material;
    private Integer operationId;
    private String workCenter;
    private String description;
    private Double setupHours;
    private Double processingHoursPerPanel;
    private Double teardownHours;
}