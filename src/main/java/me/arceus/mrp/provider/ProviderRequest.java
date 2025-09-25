package me.arceus.mrp.provider;

import java.util.Collections;
import java.util.List;

public class ProviderRequest {

    private final List<ProviderMessage> messages;
    private final int maxTokens;
    private final double temperature;

    public ProviderRequest(List<ProviderMessage> messages, int maxTokens, double temperature) {
        this.messages = List.copyOf(messages);
        this.maxTokens = maxTokens;
        this.temperature = temperature;
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
}
