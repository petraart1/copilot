package com.copilot.service;

import dev.langchain4j.model.chat.ChatModel;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class ChatService {

    private final ChatModel chatModel;

    public String generate(String prompt) {
        log.debug("Sending prompt to LLM: {}", prompt);

        try {
            String response = chatModel.chat(prompt);
            log.info("LLM response generated successfully. Length: {}", response.length());
            return response;
        } catch (Exception e) {
            log.error("Error calling LLM: {}", e.getMessage(), e);
            throw new RuntimeException("Failed to generate response from LLM", e);
        }
    }

}
