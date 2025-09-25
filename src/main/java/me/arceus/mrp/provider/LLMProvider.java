package me.arceus.mrp.provider;

import java.util.concurrent.CompletableFuture;

public interface LLMProvider {

    String getName();

    CompletableFuture<ProviderResponse> generate(ProviderRequest request);

    default void shutdown() {
        // 默认无资源需要释放
    }
}
