package me.arceus.mrp.provider;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import me.arceus.mrp.config.ProviderConfig;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.logging.Logger;

public class OpenAiProvider implements LLMProvider {

    private static final Gson GSON = new Gson();

    private final String name;
    private final ProviderConfig config;
    private final HttpClient httpClient;
    private final ExecutorService executor;
    private final Logger logger;
    private final String label;

    public OpenAiProvider(String name, ProviderConfig config, Logger logger) {
        this(name, config, logger, "OpenAI");
    }

    public OpenAiProvider(String name, ProviderConfig config, Logger logger, String label) {
        this.name = name;
        this.config = config;
        this.logger = logger;
        this.label = label;
        this.executor = Executors.newFixedThreadPool(4, r -> {
            Thread thread = new Thread(r, "mrp-openai-" + name + "-" + System.nanoTime());
            thread.setDaemon(true);
            return thread;
        });
        this.httpClient = HttpClient.newBuilder()
            .version(HttpClient.Version.HTTP_1_1)
            .connectTimeout(Duration.ofSeconds(Math.max(5, config.getTimeoutSeconds())))
            .executor(executor)
            .build();
    }

    @Override
    public String getName() {
        return name;
    }

    @Override
    public CompletableFuture<ProviderResponse> generate(ProviderRequest request) {
        return CompletableFuture.supplyAsync(() -> doGenerate(request), executor);
    }

    private ProviderResponse doGenerate(ProviderRequest request) {
        JsonObject payload = new JsonObject();
        String model = request.getModel();
        if (model == null || model.isBlank()) {
            model = config.getModel();
        }
        payload.addProperty("model", model);
        payload.addProperty("temperature", request.getTemperature());
        if (request.getMaxTokens() > 0) {
            payload.addProperty("max_tokens", request.getMaxTokens());
        }

        JsonArray messagesArray = new JsonArray();
        List<ProviderMessage> messages = request.getMessages();
        for (ProviderMessage message : messages) {
            JsonObject obj = new JsonObject();
            obj.addProperty("role", message.getRole().name().toLowerCase());
            obj.addProperty("content", message.getContent());
            messagesArray.add(obj);
        }
        payload.add("messages", messagesArray);

        String body = GSON.toJson(payload);

        logger.info("[" + label + "] Request payload for provider " + name + ": " + body);

        HttpRequest.Builder builder = HttpRequest.newBuilder()
                .uri(URI.create(config.getApiBase().endsWith("/")
                        ? config.getApiBase() + "chat/completions"
                        : config.getApiBase() + "/chat/completions"))
            .timeout(Duration.ofSeconds(Math.max(5, config.getTimeoutSeconds())))
            .header("Content-Type", "application/json")
            .POST(HttpRequest.BodyPublishers.ofString(body, StandardCharsets.UTF_8));

        if (config.getApiKey() != null && !config.getApiKey().isBlank()) {
            builder.header("Authorization", "Bearer " + config.getApiKey());
        }

        try {
            HttpResponse<String> response = httpClient.send(builder.build(),
                    HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            if (response.statusCode() >= 200 && response.statusCode() < 300) {
                logger.info("[" + label + "] Response payload for provider " + name + ": " + response.body());
                return parseResponse(response.body());
            }
            throw new RuntimeException("OpenAI 调用失败，状态码: " + response.statusCode() + "，响应: " + response.body());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException("OpenAI 请求被中断", e);
        } catch (IOException e) {
            throw new RuntimeException("OpenAI 请求异常: " + e.getMessage(), e);
        }
    }

    private ProviderResponse parseResponse(String body) {
        JsonObject json = GSON.fromJson(body, JsonObject.class);
        JsonArray choices = json.getAsJsonArray("choices");
        if (choices == null || choices.size() == 0) {
            throw new RuntimeException("OpenAI 响应中没有 choices 字段");
        }
        JsonObject first = choices.get(0).getAsJsonObject();
        JsonObject message = first.getAsJsonObject("message");
        String content = message != null && message.has("content") ? message.get("content").getAsString() : "";

        JsonObject usage = json.getAsJsonObject("usage");
        int promptTokens = usage != null && usage.has("prompt_tokens") ? usage.get("prompt_tokens").getAsInt() : 0;
        int completionTokens = usage != null && usage.has("completion_tokens")
                ? usage.get("completion_tokens").getAsInt()
                : 0;

        return new ProviderResponse(content, promptTokens, completionTokens);
    }

    @Override
    public void shutdown() {
        executor.shutdownNow();
    }
}
