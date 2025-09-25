package me.arceus.mrp.provider;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;

public class PlaceholderProvider implements LLMProvider {

    private final String name;

    public PlaceholderProvider(String name) {
        this.name = name;
    }

    @Override
    public String getName() {
        return name;
    }

    @Override
    public CompletableFuture<ProviderResponse> generate(ProviderRequest request) {
        StringBuilder builder = new StringBuilder();
        builder.append("[Placeholder] ");
        request.getMessages().stream()
            .filter(msg -> msg.getRole() == ProviderMessage.Role.USER)
            .reduce((first, second) -> second)
            .ifPresent(lastUser -> builder.append("收到输入：").append(lastUser.getContent()));

        ProviderResponse response = new ProviderResponse(builder.toString(), 0, 0);
        return CompletableFuture.completedFuture(response);
    }
}
