package dev;

import java.io.IOException;
import java.io.StringReader;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Properties;

import com.fasterxml.jackson.databind.ObjectMapper;

public class LLMService {
    private static final int DEFAULT_MAX_TOKENS = 1024;
    private static final Path CONFIG_FILE = Paths.get(System.getProperty("user.home"), ".recall", "config.properties");

    private final String apiKey;
    private final String provider;
    private final String model;
    private final String apiUrl;
    private final HttpClient httpClient;
    private final ObjectMapper objectMapper;

    public LLMService() throws IOException {
        this.httpClient = HttpClient.newBuilder()
            .version(HttpClient.Version.HTTP_2)
            .connectTimeout(Duration.ofSeconds(10))
            .build();
        this.objectMapper = new ObjectMapper();

        Properties config = new Properties();
        if (Files.exists(CONFIG_FILE)) {
            config.load(new StringReader(Files.readString(CONFIG_FILE)));
        }

        this.provider = config.getProperty("llm.provider", "claude").toLowerCase();
        this.model = config.getProperty("llm.model", defaultModel());

        String key = config.getProperty("llm.api-key", "");
        this.apiKey = !key.isBlank() ? key : System.getenv("DEVOS_API_KEY");

        String url = config.getProperty("llm.api-url", "");
        this.apiUrl = !url.isBlank() ? url : defaultApiUrl();
    }

    private String defaultModel() {
        return switch (provider) {
            case "openai" -> "gpt-4o";
            case "openai-compatible" -> "llama3";
            case "deepseek" -> "deepseek-chat";
            case "gemini" -> "gemini-2.0-flash";
            default -> "claude-sonnet-4-6";
        };
    }

    private String defaultApiUrl() {
        return switch (provider) {
            case "openai" -> "https://api.openai.com/v1/chat/completions";
            case "openai-compatible" -> "http://localhost:11434/v1/chat/completions";
            case "deepseek" -> "https://api.deepseek.com/v1/chat/completions";
            case "gemini" -> "https://generativelanguage.googleapis.com/v1beta/models/";
            default -> "https://api.anthropic.com/v1/messages";
        };
    }

    public String query(String prompt, String systemPrompt) throws IOException, InterruptedException {
        return query(prompt, systemPrompt, DEFAULT_MAX_TOKENS);
    }

    public String query(String prompt, String systemPrompt, int maxTokens) throws IOException, InterruptedException {
        if (apiKey == null || apiKey.isEmpty()) {
            throw new RuntimeException("Set DEVOS_API_KEY or llm.api-key in config.");
        }

        HttpRequest request = switch (provider) {
            case "openai", "openai-compatible", "deepseek" -> buildOpenAIRequest(prompt, systemPrompt, maxTokens);
            case "gemini" -> buildGeminiRequest(prompt, systemPrompt, maxTokens);
            default -> buildClaudeRequest(prompt, systemPrompt, maxTokens);
        };

        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() != 200) {
            throw new RuntimeException("LLM API error (" + provider + "): " + response.body());
        }

        return switch (provider) {
            case "openai", "openai-compatible", "deepseek" -> parseOpenAIResponse(response.body());
            case "gemini" -> parseGeminiResponse(response.body());
            default -> parseClaudeResponse(response.body());
        };
    }

    private HttpRequest buildClaudeRequest(String prompt, String systemPrompt, int maxTokens) throws IOException {
        Map<String, Object> body = Map.of(
            "model", model,
            "max_tokens", maxTokens,
            "system", systemPrompt,
            "messages", new Map[]{Map.of("role", "user", "content", prompt)}
        );
        return HttpRequest.newBuilder()
            .uri(URI.create(apiUrl))
            .header("x-api-key", apiKey)
            .header("anthropic-version", "2023-06-01")
            .header("Content-Type", "application/json")
            .POST(HttpRequest.BodyPublishers.ofString(objectMapper.writeValueAsString(body)))
            .build();
    }

    private HttpRequest buildOpenAIRequest(String prompt, String systemPrompt, int maxTokens) throws IOException {
        Map<String, Object> body = Map.of(
            "model", model,
            "max_tokens", maxTokens,
            "messages", new Map[]{
                Map.of("role", "system", "content", systemPrompt),
                Map.of("role", "user", "content", prompt)
            }
        );
        return HttpRequest.newBuilder()
            .uri(URI.create(apiUrl))
            .header("Authorization", "Bearer " + apiKey)
            .header("Content-Type", "application/json")
            .POST(HttpRequest.BodyPublishers.ofString(objectMapper.writeValueAsString(body)))
            .build();
    }

    private String parseClaudeResponse(String body) throws IOException {
        Map<String, Object> response = objectMapper.readValue(body, Map.class);
        return ((Map<String, String>) ((List<Map>) response.get("content")).get(0)).get("text");
    }

    private String parseOpenAIResponse(String body) throws IOException {
        Map<String, Object> response = objectMapper.readValue(body, Map.class);
        List<Map> choices = (List<Map>) response.get("choices");
        return (String) ((Map) choices.get(0).get("message")).get("content");
    }

    private HttpRequest buildGeminiRequest(String prompt, String systemPrompt, int maxTokens) throws IOException {
        Map<String, Object> contents = Map.of("parts", new Map[]{Map.of("text", prompt)});
        Map<String, Object> body;
        if (!systemPrompt.isBlank()) {
            Map<String, Object> systemInstruction = Map.of("parts", new Map[]{Map.of("text", systemPrompt)});
            body = Map.of(
                "contents", new Map[]{contents},
                "system_instruction", systemInstruction,
                "generationConfig", Map.of("maxOutputTokens", maxTokens)
            );
        } else {
            body = Map.of(
                "contents", new Map[]{contents},
                "generationConfig", Map.of("maxOutputTokens", maxTokens)
            );
        }
        return HttpRequest.newBuilder()
            .uri(URI.create(apiUrl + model + ":generateContent"))
            .header("x-goog-api-key", apiKey)
            .header("Content-Type", "application/json")
            .POST(HttpRequest.BodyPublishers.ofString(objectMapper.writeValueAsString(body)))
            .build();
    }

    private String parseGeminiResponse(String body) throws IOException {
        Map<String, Object> response = objectMapper.readValue(body, Map.class);
        List<Map> candidates = (List<Map>) response.get("candidates");
        Map<String, Object> content = (Map<String, Object>) candidates.get(0).get("content");
        List<Map> parts = (List<Map>) content.get("parts");
        return (String) parts.get(0).get("text");
    }
}