package com.production.domain;

import lombok.Data;

@Data
public class InspectionUpdateRequest {
    private Object measuredValue;
    private String judgment;   // PASS, FAIL, REWORK
    private String status;     // PASSED, FAILED, etc.
    private String remarks;
}