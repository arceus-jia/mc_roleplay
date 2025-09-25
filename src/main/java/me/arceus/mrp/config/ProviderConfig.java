package me.arceus.mrp.config;

import java.util.Objects;

public class ProviderConfig {

    private final String name;
    private final String type;
    private final String apiBase;
    private final String apiKey;
    private final String model;
    private final double temperature;
    private final int maxTokens;
    private final int timeoutSeconds;

    public ProviderConfig(String name,
                          String type,
                          String apiBase,
                          String apiKey,
                          String model,
                          double temperature,
                          int maxTokens,
                          int timeoutSeconds) {
        this.name = name;
        this.type = type;
        this.apiBase = apiBase;
        this.apiKey = apiKey;
        this.model = model;
        this.temperature = temperature;
        this.maxTokens = maxTokens;
        this.timeoutSeconds = timeoutSeconds;
    }

    public String getName() {
        return name;
    }

    public String getType() {
        return type;
    }

    public String getApiBase() {
        return apiBase;
    }

    public String getApiKey() {
        return apiKey;
    }

    public String getModel() {
        return model;
    }

    public double getTemperature() {
        return temperature;
    }

    public int getMaxTokens() {
        return maxTokens;
    }

    public int getTimeoutSeconds() {
        return timeoutSeconds;
    }

    @Override
    public String toString() {
        return "ProviderConfig{" +
            "name='" + name + '\'' +
            ", type='" + type + '\'' +
            ", apiBase='" + apiBase + '\'' +
            ", model='" + model + '\'' +
            ", temperature=" + temperature +
            ", maxTokens=" + maxTokens +
            ", timeoutSeconds=" + timeoutSeconds +
            '}';
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof ProviderConfig that)) return false;
        return Double.compare(that.temperature, temperature) == 0
            && maxTokens == that.maxTokens
            && timeoutSeconds == that.timeoutSeconds
            && Objects.equals(name, that.name)
            && Objects.equals(type, that.type)
            && Objects.equals(apiBase, that.apiBase)
            && Objects.equals(apiKey, that.apiKey)
            && Objects.equals(model, that.model);
    }

    @Override
    public int hashCode() {
        return Objects.hash(name, type, apiBase, apiKey, model, temperature, maxTokens, timeoutSeconds);
    }
}
