package com.copilot.controller;

import com.copilot.dto.request.GenerateRequest;
import com.copilot.dto.response.GenerateResponse;
import com.copilot.service.ChatService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/chat")
@RequiredArgsConstructor
@Slf4j
public class ChatController {

    private final ChatService llmService;

    @GetMapping("/generate")
    public ResponseEntity<String> generate(@RequestParam String prompt) {
        log.info("Test generate endpoint called with prompt: {}", prompt);
        String response = llmService.generate(prompt);
        return ResponseEntity.ok(response);
    }

    @PostMapping("/generate-json")
    public ResponseEntity<GenerateResponse> generateJson(
            @RequestBody GenerateRequest request) {

        log.info("Test generate JSON endpoint called");

        String response = llmService.generate(request.prompt());

        return ResponseEntity.ok(
                new GenerateResponse(
                        request.prompt(),
                        response
                )
        );
    }

}
