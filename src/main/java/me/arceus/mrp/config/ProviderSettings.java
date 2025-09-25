package me.arceus.mrp.config;

import java.util.Collections;
import java.util.Map;

public class ProviderSettings {

    private final String defaultProvider;
    private final Map<String, ProviderConfig> providers;

    public ProviderSettings(String defaultProvider, Map<String, ProviderConfig> providers) {
        this.defaultProvider = defaultProvider;
        this.providers = providers;
    }

    public String getDefaultProvider() {
        return defaultProvider;
    }

    public Map<String, ProviderConfig> getProviders() {
        return Collections.unmodifiableMap(providers);
    }

    public ProviderConfig getProvider(String name) {
        return providers.get(name);
    }
}
