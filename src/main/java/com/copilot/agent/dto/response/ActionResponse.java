package com.copilot.agent.dto.response;

import java.util.Map;

public record ActionResponse(
        String name,
        String status,
        Map<String, Object> output
) {
}

