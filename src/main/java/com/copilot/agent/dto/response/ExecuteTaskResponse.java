package com.copilot.agent.dto.response;

import java.util.List;

public record ExecuteTaskResponse(
        String status,
        String result,
        List<ActionResponse> actions
) {
}

