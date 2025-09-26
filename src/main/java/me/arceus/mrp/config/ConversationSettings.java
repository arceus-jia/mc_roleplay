package me.arceus.mrp.config;

public class ConversationSettings {

    private final int contextWindow;
    private final int maxResponseTokens;
    private final ConversationDisplayMode displayMode;

    public ConversationSettings(int contextWindow, int maxResponseTokens, ConversationDisplayMode displayMode) {
        this.contextWindow = contextWindow;
        this.maxResponseTokens = maxResponseTokens;
        this.displayMode = displayMode;
    }

    public int getContextWindow() {
        return contextWindow;
    }

    public int getMaxResponseTokens() {
        return maxResponseTokens;
    }

    public ConversationDisplayMode getDisplayMode() {
        return displayMode;
    }
}
