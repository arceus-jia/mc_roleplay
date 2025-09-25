package me.arceus.mrp.provider;

public class ProviderResponse {

    private final String content;
    private final int promptTokens;
    private final int completionTokens;

    public ProviderResponse(String content, int promptTokens, int completionTokens) {
        this.content = content;
        this.promptTokens = promptTokens;
        this.completionTokens = completionTokens;
    }

    public String getContent() {
        return content;
    }

    public int getPromptTokens() {
        return promptTokens;
    }

    public int getCompletionTokens() {
        return completionTokens;
    }
}
