package com.production.domain;

import lombok.Data;

@Data
public class MaterialMaster {
    private String material;
    private String description;
    private String baseUnit;
    private String mrpType;      // M0 = make-to-order
    private String lotSize;      // FO = fixed, EX = lot-for-lot
    private Integer fixedLotSize;
    private String procurementType; // E = in-house
    private String plant;
}