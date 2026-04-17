package com.warehouse.ontology.ai;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

/**
 * Core AI service: manages conversations with Claude API using ontology tools.
 * Uses Spring RestClient directly (no Anthropic SDK needed).
 *
 * Flow per user message:
 * 1. Build retrieval context (schema + summary + alerts)
 * 2. Add user message to conversation history
 * 3. Call Claude with tools — loop until stop_reason != "tool_use"
 * 4. For each tool_use: execute tool, emit SSE event, feed result back
 * 5. Emit final text + optional graph data
 */
@Service
public class ChatService {

    private static final Logger log = LoggerFactory.getLogger(ChatService.class);
    private static final String CLAUDE_API_URL = "https://api.anthropic.com/v1/messages";
    private static final String ANTHROPIC_VERSION = "2023-06-01";
    private static final int MAX_TOOL_ROUNDS = 10;
    private static final int MAX_TOKENS = 4096;

    private static final String SYSTEM_PROMPT_TEMPLATE = """
            你係倉庫管理 AI 助手，基於 Ontology 驅動嘅數據模型運作。
            你可以查詢同操作倉庫嘅所有實體（Object Types）、關係（Link Types）同動作（Action Types）。

            規則：
            1. 用繁體中文回答
            2. 執行寫入操作（execute_action）之前，必須先同用戶描述會發生咩改變，等用戶確認
            3. 查詢完數據後，用簡潔嘅自然語言總結，唔好直接 dump raw JSON
            4. 如果需要多步查詢（例如先搵 Task，再 trace 佢嘅 connections），自動 chain tool calls
            5. 當用戶問「有咩問題」或「目前狀態」，優先參考下面嘅 ACTIVE ALERTS 同 DATA SUMMARY
            6. 用 explore_connections 嘅結果會自動喺右側顯示圖形，唔使額外描述圖形結構

            %s
            """;

    private final String apiKey;
    private final String modelName;
    private final RestClient restClient;
    private final RetrievalContextBuilder retrievalContextBuilder;
    private final OntologyToolExecutor toolExecutor;
    private final ObjectMapper objectMapper;

    // In-memory conversation store (MVP — no persistence)
    private final ConcurrentHashMap<String, List<JsonNode>> conversations = new ConcurrentHashMap<>();

    public ChatService(
            @Value("${anthropic.api-key:}") String apiKey,
            @Value("${anthropic.model:claude-sonnet-4-20250514}") String modelName,
            RetrievalContextBuilder retrievalContextBuilder,
            OntologyToolExecutor toolExecutor,
            ObjectMapper objectMapper
    ) {
        this.apiKey = apiKey;
        this.modelName = modelName;
        this.retrievalContextBuilder = retrievalContextBuilder;
        this.toolExecutor = toolExecutor;
        this.objectMapper = objectMapper;
        this.restClient = RestClient.builder()
                .baseUrl(CLAUDE_API_URL)
                .defaultHeader("x-api-key", apiKey != null ? apiKey : "")
                .defaultHeader("anthropic-version", ANTHROPIC_VERSION)
                .defaultHeader("content-type", "application/json")
                .build();

        if (apiKey == null || apiKey.isBlank()) {
            log.warn("ANTHROPIC_API_KEY not set — AI chat will be unavailable");
        } else {
            log.info("Claude AI client initialized (model: {})", modelName);
        }
    }

    public boolean isAvailable() {
        return apiKey != null && !apiKey.isBlank();
    }

    /**
     * Process a user message and emit SSE events via the callback.
     */
    public String chat(String conversationId, String userMessage, Consumer<ChatEvent> eventEmitter) {
        if (!isAvailable()) {
            eventEmitter.accept(ChatEvent.text("AI 功能未啟用 — 請設定 ANTHROPIC_API_KEY 環境變數。"));
            eventEmitter.accept(ChatEvent.done());
            return conversationId != null ? conversationId : UUID.randomUUID().toString();
        }

        String convId = conversationId != null ? conversationId : UUID.randomUUID().toString();
        List<JsonNode> history = conversations.computeIfAbsent(convId, k -> new ArrayList<>());

        // Add user message
        history.add(userMessage(userMessage));

        // Build system prompt with retrieval context
        String retrievalContext = retrievalContextBuilder.build();
        String systemPrompt = String.format(SYSTEM_PROMPT_TEMPLATE, retrievalContext);

        // Tool-calling loop
        int round = 0;
        while (round < MAX_TOOL_ROUNDS) {
            round++;
            log.debug("Claude API call round {} for conversation {}", round, convId);

            JsonNode response = callClaude(systemPrompt, history);
            if (response == null) {
                eventEmitter.accept(ChatEvent.text("Claude API 呼叫失敗，請稍後再試。"));
                break;
            }

            String stopReason = response.path("stop_reason").asText("");
            JsonNode contentArray = response.path("content");

            // Build assistant content for history
            ObjectNode assistantMsg = objectMapper.createObjectNode();
            assistantMsg.put("role", "assistant");
            assistantMsg.set("content", contentArray);
            history.add(assistantMsg);

            boolean hasToolUse = false;
            ArrayNode toolResultsContent = objectMapper.createArrayNode();

            for (JsonNode block : contentArray) {
                String type = block.path("type").asText();

                if ("text".equals(type)) {
                    String text = block.path("text").asText();
                    if (!text.isBlank()) {
                        eventEmitter.accept(ChatEvent.text(text));
                    }
                } else if ("tool_use".equals(type)) {
                    hasToolUse = true;
                    String toolName = block.path("name").asText();
                    String toolUseId = block.path("id").asText();
                    JsonNode toolInput = block.path("input");

                    // Emit tool_use event to frontend
                    eventEmitter.accept(ChatEvent.toolUse(toolName, toolInput.toString()));

                    // Execute the tool
                    OntologyToolExecutor.ToolExecutionResult result = toolExecutor.execute(toolName, toolInput);

                    // Emit tool_result event with display data
                    eventEmitter.accept(ChatEvent.toolResult(toolName, result.displayType(), result.displayData()));

                    // Build tool_result for Claude
                    ObjectNode toolResult = objectMapper.createObjectNode();
                    toolResult.put("type", "tool_result");
                    toolResult.put("tool_use_id", toolUseId);
                    toolResult.put("content", result.textForClaude());
                    toolResultsContent.add(toolResult);
                }
            }

            if (!hasToolUse) {
                break;
            }

            // Feed tool results back to Claude
            ObjectNode toolResultMsg = objectMapper.createObjectNode();
            toolResultMsg.put("role", "user");
            toolResultMsg.set("content", toolResultsContent);
            history.add(toolResultMsg);
        }

        eventEmitter.accept(ChatEvent.done());

        // Trim conversation if too long (keep last 20 messages)
        if (history.size() > 20) {
            List<JsonNode> trimmed = new ArrayList<>(history.subList(history.size() - 20, history.size()));
            conversations.put(convId, trimmed);
        }

        return convId;
    }

    private JsonNode callClaude(String systemPrompt, List<JsonNode> messages) {
        try {
            ObjectNode requestBody = objectMapper.createObjectNode();
            requestBody.put("model", modelName);
            requestBody.put("max_tokens", MAX_TOKENS);
            requestBody.put("system", systemPrompt);

            ArrayNode messagesArray = objectMapper.createArrayNode();
            messages.forEach(messagesArray::add);
            requestBody.set("messages", messagesArray);

            ArrayNode toolsArray = objectMapper.valueToTree(OntologyToolDefinitions.allTools());
            requestBody.set("tools", toolsArray);

            String body = objectMapper.writeValueAsString(requestBody);

            String responseStr = restClient.post()
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(body)
                    .retrieve()
                    .body(String.class);

            return objectMapper.readTree(responseStr);
        } catch (Exception e) {
            log.error("Claude API call failed: {}", e.getMessage(), e);
            return null;
        }
    }

    private JsonNode userMessage(String text) {
        ObjectNode msg = objectMapper.createObjectNode();
        msg.put("role", "user");
        msg.put("content", text);
        return msg;
    }

    /**
     * Events emitted to the frontend via SSE.
     */
    public record ChatEvent(
            String type,
            String content,
            Map<String, Object> data
    ) {
        static ChatEvent text(String content) {
            return new ChatEvent("text", content, Map.of());
        }

        static ChatEvent toolUse(String toolName, String input) {
            return new ChatEvent("tool_use", toolName, Map.of("input", input));
        }

        static ChatEvent toolResult(String toolName, String displayType, Map<String, Object> displayData) {
            return new ChatEvent("tool_result", toolName,
                    Map.of("displayType", displayType, "displayData", displayData));
        }

        static ChatEvent done() {
            return new ChatEvent("done", "", Map.of());
        }
    }
}
