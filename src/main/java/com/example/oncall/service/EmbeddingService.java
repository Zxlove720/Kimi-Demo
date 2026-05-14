package com.example.oncall.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;

import java.util.List;
import java.util.Map;

@Service
public class EmbeddingService {

    private static final Logger log = LoggerFactory.getLogger(EmbeddingService.class);

    @Value("${dashscope.api-key}")
    private String apiKey;

    @Value("${dashscope.base-url:https://dashscope.aliyuncs.com/compatible-mode/v1}")
    private String baseUrl;

    @Value("${dashscope.embedding-model:text-embedding-v3}")
    private String model;

    private WebClient webClient;
    private final ObjectMapper mapper = new ObjectMapper();

    @PostConstruct
    public void init() {
        this.webClient = WebClient.builder()
                .baseUrl(baseUrl)
                .defaultHeader(HttpHeaders.AUTHORIZATION, "Bearer " + apiKey)
                .defaultHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                .build();
    }

    public float[] embed(String text) {
        if (text == null || text.isBlank()) {
            return new float[0];
        }

        // Skip if API key is not configured
        if (apiKey == null || apiKey.isBlank() || apiKey.contains("your-dashscope-key")) {
            log.warn("DashScope API key not configured, skipping embedding");
            return new float[0];
        }

        // Truncate if too long (DashScope has token limit)
        if (text.length() > 8000) {
            text = text.substring(0, 8000);
        }

        try {
            // OpenAI-compatible format: input is a string or array of strings
            Map<String, Object> body = Map.of(
                    "model", model,
                    "input", List.of(text)
            );

            String response = webClient.post()
                    .uri("/embeddings")
                    .bodyValue(body)
                    .retrieve()
                    .bodyToMono(String.class)
                    .block();

            log.debug("Embedding response: {}", response);

            JsonNode root = mapper.readTree(response);

            // Try OpenAI-compatible format first: data[0].embedding
            JsonNode data = root.path("data");
            if (data.isArray() && data.size() > 0) {
                JsonNode embeddingNode = data.get(0).path("embedding");
                if (embeddingNode.isArray()) {
                    return jsonArrayToFloatArray(embeddingNode);
                }
            }

            // Fallback to DashScope native format: output.embeddings[0].embedding
            JsonNode embeddings = root.path("output").path("embeddings");
            if (embeddings.isArray() && embeddings.size() > 0) {
                JsonNode embeddingNode = embeddings.get(0).path("embedding");
                if (embeddingNode.isArray()) {
                    return jsonArrayToFloatArray(embeddingNode);
                }
            }

            log.warn("Empty or unexpected embedding response: {}", response);
            return new float[0];
        } catch (Exception e) {
            log.error("Embedding API call failed: {}", e.getMessage());
            return new float[0];
        }
    }

    private float[] jsonArrayToFloatArray(JsonNode arrayNode) {
        float[] vec = new float[arrayNode.size()];
        for (int i = 0; i < arrayNode.size(); i++) {
            vec[i] = (float) arrayNode.get(i).asDouble();
        }
        return vec;
    }
}
