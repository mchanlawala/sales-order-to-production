package com.production.domain;

import lombok.Data;
import java.time.LocalDateTime;

@Data
public class DowntimeEvent {
    private String eventId;
    private String workCenter;
    private LocalDateTime startDateTime;
    private LocalDateTime endDateTime;
    private String reason;
    private String type;
}