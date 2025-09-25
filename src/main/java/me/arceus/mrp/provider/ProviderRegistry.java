package me.arceus.mrp.provider;

import me.arceus.mrp.MrpPlugin;
import me.arceus.mrp.config.ProviderConfig;
import me.arceus.mrp.config.ProviderSettings;

import java.util.Collections;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.logging.Logger;

public class ProviderRegistry {

    private final MrpPlugin plugin;
    private final Map<String, LLMProvider> providers = new HashMap<>();
    private String defaultProvider;

    public ProviderRegistry(MrpPlugin plugin) {
        this.plugin = plugin;
    }

    public void initialize(ProviderSettings settings) {
        shutdown();
        if (settings == null) {
            plugin.getLogger().warning("Provider 设置为空，无法初始化");
            return;
        }

        this.defaultProvider = settings.getDefaultProvider();
        Logger logger = plugin.getLogger();

        for (ProviderConfig config : settings.getProviders().values()) {
            LLMProvider provider = buildProvider(config);
            providers.put(config.getName(), provider);
            logger.info("已注册 Provider: " + config.getName() + " (" + config.getType() + ")");
        }

        if (!providers.containsKey(defaultProvider) && !providers.isEmpty()) {
            defaultProvider = providers.keySet().iterator().next();
            logger.warning("默认 Provider 不存在，已自动切换为 " + defaultProvider);
        }
    }

    private LLMProvider buildProvider(ProviderConfig config) {
        String type = config.getType() != null ? config.getType().toLowerCase(Locale.ROOT) : "openai";
        if (isOpenAiCompatible(type)) {
            boolean requiresKey = requiresApiKey(type);
            if (requiresKey && (config.getApiKey() == null || config.getApiKey().isBlank())) {
                plugin.getLogger().warning("Provider " + config.getName() + " 缺少 api-key，回退至占位 Provider");
                return new PlaceholderProvider(config.getName());
            }
            String label = resolveLabel(type);
            return new OpenAiProvider(config.getName(), config, plugin.getLogger(), label);
        }
        return new PlaceholderProvider(config.getName());
    }

    private boolean isOpenAiCompatible(String type) {
        return Set.of("openai", "openai-compatible", "compatible", "doubao", "volcengine", "vllm").contains(type);
    }

    private boolean requiresApiKey(String type) {
        return !"vllm".equals(type) && !"compatible".equals(type);
    }

    private String resolveLabel(String type) {
        return switch (type) {
            case "doubao", "volcengine" -> "Doubao";
            case "vllm" -> "vLLM";
            case "openai" -> "OpenAI";
            default -> "OpenAI-Compatible";
        };
    }

    public LLMProvider getProvider(String name) {
        return providers.get(name);
    }

    public LLMProvider getDefaultProvider() {
        if (defaultProvider == null) {
            return null;
        }
        return providers.get(defaultProvider);
    }

    public Map<String, LLMProvider> getProviders() {
        return Collections.unmodifiableMap(providers);
    }

    public void shutdown() {
        providers.values().forEach(LLMProvider::shutdown);
        providers.clear();
    }
}
