package com.warehouse.ontology.ai;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

@RestController
@RequestMapping("/api/chat")
public class ChatController {

    private static final Logger log = LoggerFactory.getLogger(ChatController.class);
    private static final long SSE_TIMEOUT = 120_000L; // 2 minutes

    private final ChatService chatService;
    private final ObjectMapper objectMapper;
    private final ExecutorService executor = Executors.newCachedThreadPool();

    public ChatController(ChatService chatService, ObjectMapper objectMapper) {
        this.chatService = chatService;
        this.objectMapper = objectMapper;
    }

    @GetMapping("/status")
    public Map<String, Object> status() {
        return Map.of("available", chatService.isAvailable());
    }

    @PostMapping(produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter chat(@RequestBody ChatRequest request) {
        SseEmitter emitter = new SseEmitter(SSE_TIMEOUT);

        executor.execute(() -> {
            try {
                String conversationId = chatService.chat(
                        request.conversationId(),
                        request.message(),
                        event -> {
                            try {
                                String json = objectMapper.writeValueAsString(event);
                                emitter.send(SseEmitter.event()
                                        .name("message")
                                        .data(json, MediaType.APPLICATION_JSON));
                            } catch (IOException e) {
                                log.warn("SSE send failed: {}", e.getMessage());
                            }
                        }
                );

                // Send the conversation ID so frontend can maintain it
                emitter.send(SseEmitter.event()
                        .name("conversation")
                        .data(objectMapper.writeValueAsString(
                                Map.of("conversationId", conversationId)),
                                MediaType.APPLICATION_JSON));

                emitter.complete();
            } catch (Exception e) {
                log.error("Chat processing failed", e);
                try {
                    String errorJson = objectMapper.writeValueAsString(
                            ChatService.ChatEvent.text("發生錯誤：" + e.getMessage()));
                    emitter.send(SseEmitter.event().name("message").data(errorJson, MediaType.APPLICATION_JSON));
                } catch (IOException ignored) {
                }
                emitter.complete();
            }
        });

        emitter.onTimeout(emitter::complete);
        emitter.onError(t -> log.warn("SSE error: {}", t.getMessage()));

        return emitter;
    }

    public record ChatRequest(
            String conversationId,
            String message
    ) {
    }
}
