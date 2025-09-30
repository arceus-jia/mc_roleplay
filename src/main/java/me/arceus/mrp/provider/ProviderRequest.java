package me.arceus.mrp.provider;

import java.util.Collections;
import java.util.List;

public class ProviderRequest {

    private final List<ProviderMessage> messages;
    private final int maxTokens;
    private final double temperature;
    private final String model;

    public ProviderRequest(List<ProviderMessage> messages, int maxTokens, double temperature) {
        this(messages, maxTokens, temperature, null);
    }

    public ProviderRequest(List<ProviderMessage> messages, int maxTokens, double temperature, String model) {
        this.messages = List.copyOf(messages);
        this.maxTokens = maxTokens;
        this.temperature = temperature;
        this.model = model;
    }

    public List<ProviderMessage> getMessages() {
        return Collections.unmodifiableList(messages);
    }

    public int getMaxTokens() {
        return maxTokens;
    }

    public double getTemperature() {
        return temperature;
    }

    public String getModel() {
        return model;
    }
}
