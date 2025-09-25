package me.arceus.mrp.config;

public class ConversationSettings {

    private final int contextWindow;
    private final int maxResponseTokens;

    public ConversationSettings(int contextWindow, int maxResponseTokens) {
        this.contextWindow = contextWindow;
        this.maxResponseTokens = maxResponseTokens;
    }

    public int getContextWindow() {
        return contextWindow;
    }

    public int getMaxResponseTokens() {
        return maxResponseTokens;
    }
}
