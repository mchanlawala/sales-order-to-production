package com.production.domain;

import lombok.Data;

@Data
public class BillOfMaterial {
    private String material;
    private String component;
    private String componentDescription;
    private Double quantityPerPanel;
    private String unit;
    private Double scrapPercentage;
}