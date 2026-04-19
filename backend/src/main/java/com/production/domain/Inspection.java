package com.production.domain;

import lombok.Data;
import java.time.LocalDateTime;
import java.util.Map;

@Data
public class Inspection {
    private String inspectionId;
    private Integer operationId;           // links to operation step
    private String type;                   // EL_TEST, FLASH_TEST, VISUAL, DIMENSION
    private String description;
    private Boolean required;              // if true, order cannot complete without PASS
    private Map<String, Object> specification;  // flexible acceptance criteria
    private Object result;                 // can store measured values (number, string, array)
    private String status;                 // PENDING, IN_PROGRESS, PASSED, FAILED, SKIPPED
    private LocalDateTime scheduledTime;
    private LocalDateTime completedTime;
    private Object measuredValue;          // actual measured value(s)
    private String judgment;               // PASS, FAIL, REWORK
    private String remarks;
}