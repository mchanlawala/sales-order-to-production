package com.production.domain;

import lombok.Data;

@Data
public class Break {
    private String breakStart;
    private String breakEnd;
    private Integer dailyOccurrence;
}