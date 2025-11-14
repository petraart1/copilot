package com.copilot.tools.calendar.dto;

import java.time.LocalDateTime;
import java.util.List;

public record EventResponse(
        String eventId,
        String title,
        LocalDateTime startTime,
        LocalDateTime endTime,
        String description,
        List<String> attendeeEmails,
        String location,
        String calendarUrl
) {
}

