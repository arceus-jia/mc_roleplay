package me.arceus.mrp.config;

import me.arceus.mrp.MrpPlugin;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;

import java.util.HashMap;
import java.util.Map;
import java.util.logging.Logger;

public class ConfigService {

    private final MrpPlugin plugin;
    private ProviderSettings providerSettings;
    private ConversationSettings conversationSettings;
    private PromptSettings promptSettings;

    public ConfigService(MrpPlugin plugin) {
        this.plugin = plugin;
    }

    public void reload() {
        FileConfiguration config = plugin.getConfig();
        this.providerSettings = loadProviders(config);
        this.conversationSettings = loadConversation(config);
        this.promptSettings = loadPrompt(config);
    }

    public ProviderSettings getProviderSettings() {
        return providerSettings;
    }

    public ConversationSettings getConversationSettings() {
        return conversationSettings;
    }

    public PromptSettings getPromptSettings() {
        return promptSettings;
    }

    private ProviderSettings loadProviders(FileConfiguration config) {
        String defaultProvider = config.getString("providers.default", "openai");
        ConfigurationSection listSection = config.getConfigurationSection("providers.list");
        Map<String, ProviderConfig> providers = new HashMap<>();
        Logger logger = plugin.getLogger();

        if (listSection != null) {
            for (String key : listSection.getKeys(false)) {
                ConfigurationSection section = listSection.getConfigurationSection(key);
                if (section == null) {
                    continue;
                }
                String type = section.getString("type", "openai");
                String apiBase = section.getString("api-base", "https://api.openai.com/v1");
                String apiKey = section.getString("api-key", "");
                String model = section.getString("model", "gpt-5-chat-latest");
                double temperature = section.getDouble("temperature", 0.8D);
                int maxTokens = section.getInt("max-tokens", 512);
                int timeoutSeconds = section.getInt("timeout-seconds", 30);
                ProviderConfig providerConfig = new ProviderConfig(key, type, apiBase, apiKey, model, temperature, maxTokens, timeoutSeconds);
                providers.put(key, providerConfig);
            }
        } else {
            logger.warning("未在 config.yml 中找到 providers.list 配置，使用默认设置");
        }

        if (!providers.containsKey(defaultProvider) && !providers.isEmpty()) {
            logger.warning("默认 Provider '" + defaultProvider + "' 未在 providers.list 中定义，改用第一个可用项");
            defaultProvider = providers.keySet().iterator().next();
        }

        return new ProviderSettings(defaultProvider, providers);
    }

    private ConversationSettings loadConversation(FileConfiguration config) {
        ConfigurationSection section = config.getConfigurationSection("conversation");
        int memoryWindow = 8;
        int maxResponseTokens = 512;
        ConversationDisplayMode displayMode = ConversationDisplayMode.INVENTORY;
        if (section != null) {
            memoryWindow = section.getInt("memory-window", memoryWindow);
            maxResponseTokens = section.getInt("max-response-tokens", maxResponseTokens);
            String mode = section.getString("display-mode", "inventory").toLowerCase();
            if (mode.equals("book")) {
                displayMode = ConversationDisplayMode.BOOK;
            } else {
                displayMode = ConversationDisplayMode.INVENTORY;
            }
        }
        return new ConversationSettings(memoryWindow, maxResponseTokens, displayMode);
    }

    private PromptSettings loadPrompt(FileConfiguration config) {
        ConfigurationSection section = config.getConfigurationSection("prompt");
        String template = null;
        java.util.List<String> notes = java.util.Collections.emptyList();
        if (section != null) {
            template = section.getString("system-template");
            notes = section.getStringList("extra-notes");
        }
        return new PromptSettings(template, notes);
    }
}
