package me.arceus.mrp.conversation;

import me.arceus.mrp.MrpPlugin;
import me.arceus.mrp.config.ConversationSettings;
import me.arceus.mrp.config.ProviderConfig;
import me.arceus.mrp.config.ProviderSettings;
import me.arceus.mrp.provider.LLMProvider;
import me.arceus.mrp.provider.ProviderMessage;
import me.arceus.mrp.provider.ProviderRequest;
import me.arceus.mrp.provider.ProviderResponse;
import me.arceus.mrp.prompt.PromptService;
import me.arceus.mrp.villager.VillagerProfile;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Handles end-to-end chat flow between a player and a villager profile,
 * including prompt construction, provider invocation, logging, and
 * guarding against concurrent requests per player.
 */
public class ConversationChatService {

    private final MrpPlugin plugin;
    private final ConversationSessionManager sessionManager;
    private final PromptService promptService;
    private final Set<UUID> pendingPlayers = Collections.newSetFromMap(new ConcurrentHashMap<>());

    public ConversationChatService(MrpPlugin plugin) {
        this.plugin = plugin;
        this.sessionManager = plugin.getSessionManager();
        this.promptService = plugin.getPromptService();
    }

    public boolean isProcessing(UUID playerId) {
        return pendingPlayers.contains(playerId);
    }

    public CompletableFuture<String> sendPlayerMessage(Player player, VillagerProfile profile, String playerInput) {
        UUID playerId = player.getUniqueId();
        CompletableFuture<String> result = new CompletableFuture<>();

        if (!pendingPlayers.add(playerId)) {
            player.sendMessage("村民正在思考，请稍候再试。");
            result.completeExceptionally(new IllegalStateException("conversation in progress"));
            return result;
        }

        if (profile == null) {
            pendingPlayers.remove(playerId);
            player.sendMessage("找不到该村民的资料");
            result.completeExceptionally(new IllegalStateException("villager profile missing"));
            return result;
        }

        LLMProvider provider = plugin.getProviderRegistry().getDefaultProvider();
        if (provider == null) {
            pendingPlayers.remove(playerId);
            player.sendMessage("当前未配置可用的 Provider");
            result.completeExceptionally(new IllegalStateException("provider not configured"));
            return result;
        }

        ConversationSession session = sessionManager.getOrCreate(playerId, profile.getVillagerId());
        sessionManager.appendMessage(session, ProviderMessage.Role.USER, playerInput);
        plugin.getConversationLogger().log(
            profile.getVillagerId(),
            profile.getName(),
            playerId,
            player.getName(),
            ProviderMessage.Role.USER,
            playerInput
        );

        String systemPrompt = promptService.buildSystemPrompt(profile, player.getName());

        List<ProviderMessage> messages = new ArrayList<>();
        messages.add(new ProviderMessage(ProviderMessage.Role.SYSTEM, systemPrompt));
        messages.addAll(sessionManager.buildPromptMessages(session));

        plugin.getLogger().info("[LLM Request] provider=" + provider.getName()
            + " villager=" + profile.getName()
            + " player=" + player.getName());
        messages.forEach(msg -> plugin.getLogger().info(" - " + msg.getRole() + ": " + msg.getContent()));

        ConversationSettings convSettings = plugin.getConfigService().getConversationSettings();
        int maxTokens = convSettings != null ? convSettings.getMaxResponseTokens() : 512;

        ProviderSettings providerSettings = plugin.getConfigService().getProviderSettings();
        ProviderConfig providerConfig = providerSettings != null ? providerSettings.getProvider(provider.getName()) : null;
        double temperature = providerConfig != null ? providerConfig.getTemperature() : 0.8D;

        ProviderRequest request = new ProviderRequest(messages, maxTokens, temperature);

        player.sendMessage("村民正在思考...");

        CompletableFuture<ProviderResponse> future;
        try {
            future = provider.generate(request);
        } catch (Exception e) {
            pendingPlayers.remove(playerId);
            player.sendMessage("村民思考失败: " + e.getMessage());
            result.completeExceptionally(e);
            return result;
        }

        future.whenComplete((response, throwable) -> Bukkit.getScheduler().runTask(plugin, () -> {
            pendingPlayers.remove(playerId);

            if (throwable != null) {
                player.sendMessage("村民思考失败: " + throwable.getMessage());
                result.completeExceptionally(throwable);
                return;
            }

            String reply = response.getContent();
            if (reply == null || reply.isBlank()) {
                reply = "(沉默)";
            }

            sessionManager.appendMessage(session, ProviderMessage.Role.ASSISTANT, reply);
            plugin.getConversationLogger().log(
                profile.getVillagerId(),
                profile.getName(),
                playerId,
                player.getName(),
                ProviderMessage.Role.ASSISTANT,
                reply
            );

            plugin.getLogger().info("[LLM Response] villager=" + profile.getName() + " -> " + reply);
            String displayName = profile.getName() != null ? profile.getName() : "村民";
            player.sendMessage(displayName + ": " + reply);
            result.complete(reply);
        }));

        return result;
    }
}
