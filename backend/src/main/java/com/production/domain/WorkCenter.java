package com.production.domain;

import lombok.Data;
import java.util.ArrayList;
import java.util.List;

@Data
public class WorkCenter {
    private String workCenter;
    private String plant;
    private Integer availableHoursPerDay;
    private Integer shifts;
    private Double breakHours;
    private String defaultStart;   // e.g., "08:00"
    private String defaultEnd;     // e.g., "20:00" or "00:00" (next day)
    private List<ShiftOverride> shiftOverrides = new ArrayList<>();
    private List<Break> breaks = new ArrayList<>();
    private List<DowntimeEvent> downtimeEvents = new ArrayList<>();
}