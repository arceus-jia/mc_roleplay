package me.arceus.mrp;

import me.arceus.mrp.command.MrpCommandExecutor;
import me.arceus.mrp.config.ConfigService;
import me.arceus.mrp.conversation.ConversationChatService;
import me.arceus.mrp.conversation.ConversationSessionManager;
import me.arceus.mrp.conversation.ConversationStorage;
import me.arceus.mrp.provider.ProviderRegistry;
import me.arceus.mrp.prompt.PromptService;
import me.arceus.mrp.logging.ConversationLogger;
import me.arceus.mrp.villager.VillagerRegistry;
import me.arceus.mrp.listener.VillagerInteractListener;
import me.arceus.mrp.listener.VillagerProtectionListener;
import me.arceus.mrp.ui.ConversationUiService;
import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.entity.Villager;
import org.bukkit.event.HandlerList;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.Objects;

public class MrpPlugin extends JavaPlugin {

    private ConfigService configService;
    private ProviderRegistry providerRegistry;
    private VillagerRegistry villagerRegistry;
    private ConversationSessionManager sessionManager;
    private ConversationStorage conversationStorage;
    private PromptService promptService;
    private ConversationLogger conversationLogger;
    private ConversationChatService conversationChatService;
    private ConversationUiService conversationUiService;

    @Override
    public void onEnable() {
        this.saveDefaultConfig();

        this.configService = new ConfigService(this);
        this.providerRegistry = new ProviderRegistry(this);
        this.villagerRegistry = new VillagerRegistry(this);
        this.conversationStorage = new ConversationStorage(this);
        this.sessionManager = new ConversationSessionManager(this, conversationStorage);
        this.promptService = new PromptService(this);
        this.conversationLogger = new ConversationLogger(this);
        this.conversationChatService = new ConversationChatService(this);
        this.conversationUiService = new ConversationUiService(this, conversationChatService);

        reloadSettings(true);
        villagerRegistry.loadVillagers();
        refreshNpcProtections();

        Objects.requireNonNull(getCommand("mrp"), "在 plugin.yml 中缺少 mrp 命令")
            .setExecutor(new MrpCommandExecutor(this));

        getServer().getPluginManager().registerEvents(conversationUiService, this);
        getServer().getPluginManager().registerEvents(new VillagerInteractListener(this), this);
        getServer().getPluginManager().registerEvents(new VillagerProtectionListener(this), this);

        getLogger().info("MRP Villager Roleplay 已启用");
    }

    @Override
    public void onDisable() {
        HandlerList.unregisterAll(this);
        if (sessionManager != null) {
            sessionManager.shutdown();
        }
        if (villagerRegistry != null) {
            villagerRegistry.saveVillagers();
        }
        if (providerRegistry != null) {
            providerRegistry.shutdown();
        }
        if (conversationLogger != null) {
            conversationLogger.shutdown();
        }
        if (conversationStorage != null) {
            conversationStorage.shutdown();
        }
        getLogger().info("MRP Villager Roleplay 已卸载");
    }

    public ConfigService getConfigService() {
        return configService;
    }

    public ProviderRegistry getProviderRegistry() {
        return providerRegistry;
    }

    public VillagerRegistry getVillagerRegistry() {
        return villagerRegistry;
    }

    public ConversationSessionManager getSessionManager() {
        return sessionManager;
    }

    public PromptService getPromptService() {
        return promptService;
    }

    public ConversationLogger getConversationLogger() {
        return conversationLogger;
    }

    public ConversationStorage getConversationStorage() {
        return conversationStorage;
    }

    public ConversationChatService getConversationChatService() {
        return conversationChatService;
    }

    public ConversationUiService getConversationUiService() {
        return conversationUiService;
    }

    public void applyNpcProtection(Villager villager) {
        if (villager == null) {
            return;
        }
        villager.setInvulnerable(true);
        villager.setRemoveWhenFarAway(false);
        villager.setPersistent(true);
    }

    public void refreshNpcProtections() {
        if (villagerRegistry == null) {
            return;
        }
        for (World world : Bukkit.getWorlds()) {
            for (Villager villager : world.getEntitiesByClass(Villager.class)) {
                if (villagerRegistry.getProfile(villager.getUniqueId()) != null) {
                    applyNpcProtection(villager);
                }
            }
        }
    }

    public void reloadSettings(boolean reloadFile) {
        if (reloadFile) {
            super.reloadConfig();
        }
        configService.reload();
        providerRegistry.initialize(configService.getProviderSettings());
    }
}
