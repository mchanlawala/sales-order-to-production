package com.production.domain;

import lombok.Data;

@Data
public class CustomerMaster {
    private String customerCode;
    private String name;
    private String country;
    private Integer paymentTermsDays;
    private String pricingGroup;
}