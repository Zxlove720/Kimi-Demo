package com.example.oncall.service;

import com.example.oncall.model.ChatMessage;
import com.example.oncall.model.ChatResponse;
import com.example.oncall.model.Conversation;
import com.example.oncall.tool.FileTools;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;

import jakarta.annotation.PostConstruct;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
public class OnCallAgentService {

    private static final Logger log = LoggerFactory.getLogger(OnCallAgentService.class);

    private final FileTools fileTools;
    private final ConversationStore conversationStore;
    private final ObjectMapper objectMapper = new ObjectMapper();
    private WebClient webClient;

    @Value("${dashscope.api-key}")
    private String apiKey;

    @Value("${dashscope.base-url:https://dashscope.aliyuncs.com/compatible-mode/v1}")
    private String baseUrl;

    @Value("${dashscope.chat-model:qwen-plus}")
    private String model;

    private static final Pattern TOOL_CALL_PATTERN = Pattern.compile(
            "<tool_call>\\s*(.+?)\\s*</tool_call>", Pattern.DOTALL);

    private static final String SYSTEM_PROMPT = """
            你是一名资深 On-Call 值班助手，精通各部门的 SOP 文档。你的任务是通过对话回答用户的 On-Call 问题。

            ## 可用 SOP 文档清单
            - sop-001.html：后端服务 On-Call SOP（OOM、服务超时、降级策略、故障分级）
            - sop-002.html：数据库 DBA On-Call SOP（主从延迟、慢查询、连接池满、数据恢复）
            - sop-003.html：前端 On-Call SOP（页面白屏、CDN 资源加载失败、兼容性、性能劣化）
            - sop-004.html：SRE On-Call SOP（K8s 集群问题、监控告警、容量规划、故障响应）
            - sop-005.html：信息安全 On-Call SOP（安全事件分级、入侵检测、漏洞响应、DDoS）
            - sop-006.html：数据平台 On-Call SOP（数据管道故障、ETL 失败、Spark 集群）
            - sop-007.html：移动端 On-Call SOP（App 崩溃率、热修复、推送服务）
            - sop-008.html：AI & 算法 On-Call SOP（模型推理延迟、推荐质量下降、GPU 集群）
            - sop-009.html：QA On-Call SOP（测试环境故障、自动化测试、发版卡点）
            - sop-010.html：网络 & CDN On-Call SOP（CDN 节点故障、DNS 异常、DDoS 防护）

            ## 工具说明
            你有一个工具 readFile(fname: string) -> string，可以读取 data/ 目录下的 SOP 文档。

            ## 工作流程
            1. 分析用户问题，判断涉及哪些领域
            2. 如果你需要读取 SOP 文档来确认细节，请严格按以下格式输出工具调用：
               <tool_call>{"tool":"readFile","args":{"fname":"sop-001.html"}}</tool_call>
               你可以在一行中输出多个 tool_call。
            3. 读取到文档内容后，我会将内容发回给你，你基于内容给出准确回答。
            4. 如果问题涉及多个领域，请分别读取相关文档。
            5. 如涉及故障分级（P0/P1/P2），明确说明响应时效和升级路径。

            ## 重要规则
            - 不要编造 SOP 中没有的内容
            - 如果文档中没有相关信息，请如实告知
            - 回答要结构化、步骤清晰
            """;

    private static final String TOOL_RESULT_PROMPT = """
            以下是工具 readFile 的返回结果：

            ```
            %s
            ```

            请基于上述文档内容，回答用户的问题。保持结构化输出，列出关键步骤。
            """;

    @Autowired
    public OnCallAgentService(FileTools fileTools, ConversationStore conversationStore) {
        this.fileTools = fileTools;
        this.conversationStore = conversationStore;
    }

    @PostConstruct
    public void init() {
        this.webClient = WebClient.builder()
                .baseUrl(baseUrl)
                .defaultHeader(HttpHeaders.AUTHORIZATION, "Bearer " + apiKey)
                .defaultHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                .build();
    }

    public ChatResponse chat(String conversationId, String userMessage) {
        log.info("Agent chat: conversationId={}, userMessage={}", conversationId, userMessage);

        // 1. 获取或创建对话
        if (conversationId == null || conversationId.isBlank()) {
            Conversation conversation = conversationStore.createConversation(userMessage);
            conversationId = conversation.getId();
        } else if (conversationStore.getConversation(conversationId).isEmpty()) {
            Conversation conversation = conversationStore.createConversation(userMessage);
            conversationId = conversation.getId();
        }

        // 2. 保存用户消息
        conversationStore.addMessage(conversationId, "user", userMessage);

        // 3. 构建消息列表（system + 历史 + 当前用户消息）
        List<Map<String, String>> messages = new ArrayList<>();
        messages.add(Map.of("role", "system", "content", SYSTEM_PROMPT));

        List<ChatMessage> history = conversationStore.getMessages(conversationId);
        for (ChatMessage msg : history) {
            messages.add(Map.of("role", msg.getRole(), "content", msg.getContent()));
        }

        // 4. 第一轮 LLM 调用
        String firstResponse = callLLM(messages);
        log.info("First LLM response: {}", firstResponse.substring(0, Math.min(200, firstResponse.length())));

        // 5. 检查是否需要工具调用
        List<String> toolResults = executeToolCalls(firstResponse);

        StringBuilder reasoning = new StringBuilder();
        String finalResponse;
        if (!toolResults.isEmpty()) {
            reasoning.append("【思考过程】\n").append(firstResponse).append("\n\n");
            reasoning.append("【工具调用】\n");
            for (String result : toolResults) {
                reasoning.append(result).append("\n\n");
            }

            messages.add(Map.of("role", "assistant", "content", firstResponse));
            String combinedResults = String.join("\n\n---\n\n", toolResults);
            messages.add(Map.of("role", "user", "content", TOOL_RESULT_PROMPT.formatted(combinedResults)));

            finalResponse = callLLM(messages);
            log.info("Final LLM response: {} chars", finalResponse.length());
        } else {
            finalResponse = firstResponse;
            reasoning.append("【直接回答】\n").append(firstResponse);
        }

        // 6. 保存助手回复（包含完整过程，让历史记录中也能看到工具调用）
        String fullReply = reasoning.length() > 0
                ? reasoning + "\n【回答】\n" + finalResponse
                : finalResponse;
        conversationStore.addMessage(conversationId, "assistant", fullReply);

        ChatResponse response = new ChatResponse(conversationId, finalResponse);
        if (reasoning.length() > 0) {
            response.setReasoning(reasoning.toString());
        }
        return response;
    }



    private String callLLM(List<Map<String, String>> messages) {
        try {
            Map<String, Object> body = Map.of(
                    "model", model,
                    "messages", messages,
                    "temperature", 0.3
            );

            String response = webClient.post()
                    .uri("/chat/completions")
                    .bodyValue(body)
                    .retrieve()
                    .bodyToMono(String.class)
                    .block();

            log.debug("Raw LLM response: {}", response);

            JsonNode root = objectMapper.readTree(response);
            JsonNode choices = root.path("choices");
            if (choices.isArray() && choices.size() > 0) {
                return choices.get(0).path("message").path("content").asText("");
            }
            return "抱歉，模型返回了空结果。";
        } catch (Exception e) {
            log.error("LLM call failed", e);
            return "[系统错误] 调用大模型失败: " + e.getMessage();
        }
    }

    private List<String> executeToolCalls(String llmResponse) {
        List<String> results = new ArrayList<>();
        Matcher matcher = TOOL_CALL_PATTERN.matcher(llmResponse);

        while (matcher.find()) {
            try {
                String jsonStr = matcher.group(1).trim();
                JsonNode node = objectMapper.readTree(jsonStr);
                String tool = node.path("tool").asText();
                JsonNode args = node.path("args");

                if ("readFile".equals(tool)) {
                    String fname = args.path("fname").asText();
                    if (!fname.isBlank()) {
                        String result = fileTools.readFile(fname);
                        results.add("【读取文件: " + fname + "】\n" + result);
                    }
                }
            } catch (Exception e) {
                log.error("Failed to parse tool call", e);
                results.add("【工具调用失败】解析错误: " + e.getMessage());
            }
        }

        return results;
    }
}
