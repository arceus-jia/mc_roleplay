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
import me.arceus.mrp.villager.VillagerPromptOverride;
import me.arceus.mrp.villager.VillagerRewardOption;
import me.arceus.mrp.villager.VillagerSuccessBehavior;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;
import java.util.stream.Collectors;

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
    private static final List<String> DEFAULT_SUCCESS_TRIGGERS = List.of("SUCCESS");

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
        ensurePromptVariables(profile, session);
        sessionManager.appendMessage(session, ProviderMessage.Role.USER, playerInput);
        plugin.getConversationLogger().log(
            profile.getVillagerId(),
            profile.getName(),
            playerId,
            player.getName(),
            ProviderMessage.Role.USER,
            playerInput
        );

        String systemPrompt = promptService.buildSystemPrompt(profile, session, player.getName());

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
            String trimmed = reply != null ? reply.trim() : "";
            boolean isSuccess = isSuccessReply(trimmed, profile);

            if (isSuccess) {
                String successMessage = handleSuccessResponse(player, profile, session, trimmed);
                result.complete(successMessage);
                return;
            }

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

    public void ensurePromptVariables(VillagerProfile profile, ConversationSession session) {
        if (profile == null || session == null) {
            return;
        }
        VillagerPromptOverride override = profile.getPromptOverride();
        if (override == null) {
            return;
        }

        if (override.hasVariables()) {
            for (Map.Entry<String, String> entry : override.getVariables().entrySet()) {
                String key = entry.getKey();
                String value = entry.getValue();
                if (key == null || value == null || value.isBlank()) {
                    continue;
                }
                if (!session.hasPromptVariable(key)) {
                    session.setPromptVariable(key, value);
                }
            }
        }

        if (override.hasVariableCandidates()) {
            for (Map.Entry<String, List<String>> entry : override.getVariableCandidates().entrySet()) {
                String key = entry.getKey();
                if (key == null || session.hasPromptVariable(key)) {
                    continue;
                }
                String selected = pickCandidate(entry.getValue());
                if (selected != null) {
                    session.setPromptVariable(key, selected);
                }
            }
        }
    }

    private String pickCandidate(List<String> candidates) {
        if (candidates == null || candidates.isEmpty()) {
            return null;
        }
        List<String> pool = new ArrayList<>(candidates.size());
        for (String candidate : candidates) {
            if (candidate != null && !candidate.isBlank()) {
                pool.add(candidate);
            }
        }
        if (pool.isEmpty()) {
            return null;
        }
        int index = ThreadLocalRandom.current().nextInt(pool.size());
        return pool.get(index);
    }

    private boolean isSuccessReply(String reply, VillagerProfile profile) {
        if (reply == null) {
            return false;
        }
        String normalized = reply.trim();
        VillagerPromptOverride override = profile != null ? profile.getPromptOverride() : null;
        VillagerSuccessBehavior success = override != null ? override.getSuccess() : null;
        List<String> triggers = success != null ? success.getTriggers() : DEFAULT_SUCCESS_TRIGGERS;
        for (String trigger : triggers) {
            if (trigger != null && normalized.equalsIgnoreCase(trigger.trim())) {
                return true;
            }
        }
        if (success == null || success.getMessage() == null) {
            return false;
        }
        return normalized.equalsIgnoreCase(success.getMessage());
    }

    private String handleSuccessResponse(Player player, VillagerProfile profile, ConversationSession session, String rawReply) {
        VillagerPromptOverride override = profile.getPromptOverride();
        VillagerSuccessBehavior success = override != null ? override.getSuccess() : null;

        String successMessage = success != null && success.getMessage() != null && !success.getMessage().isBlank()
            ? success.getMessage().trim()
            : "恭喜你回答正确！";

        sessionManager.appendMessage(session, ProviderMessage.Role.ASSISTANT, successMessage);
        plugin.getConversationLogger().log(
            profile.getVillagerId(),
            profile.getName(),
            player.getUniqueId(),
            player.getName(),
            ProviderMessage.Role.ASSISTANT,
            successMessage
        );

        String displayName = profile.getName() != null ? profile.getName() : "村民";
        player.sendMessage(displayName + ": " + successMessage);

        if (success != null && success.hasRewards()) {
            grantReward(player, success.getRewardPool(), displayName);
        }

        if (success == null || success.shouldResetConversation()) {
            sessionManager.clearSessionForPlayer(player.getUniqueId(), profile.getVillagerId());
        }

        return successMessage;
    }

    private void grantReward(Player player, List<VillagerRewardOption> rewardPool, String villagerName) {
        List<VillagerRewardOption> pool = rewardPool.stream()
            .filter(option -> option != null && (!option.getCommands().isEmpty() || !option.getMessages().isEmpty()))
            .collect(Collectors.toList());
        if (pool.isEmpty()) {
            return;
        }

        VillagerRewardOption option = pickWeightedReward(pool);
        if (option == null) {
            return;
        }

        if (!option.getMessages().isEmpty()) {
            for (String raw : option.getMessages()) {
                if (raw == null || raw.isBlank()) {
                    continue;
                }
                String formatted = raw
                    .replace("{player}", player.getName())
                    .replace("{villager}", villagerName);
                player.sendMessage(formatted);
            }
        }

        option.getCommands().forEach(cmd -> {
            if (cmd == null || cmd.isBlank()) {
                return;
            }
            String formatted = cmd
                .replace("{player}", player.getName())
                .replace("{uuid}", player.getUniqueId().toString())
                .replace("{villager}", villagerName);
            Bukkit.dispatchCommand(Bukkit.getConsoleSender(), formatted);
        });
    }

    private VillagerRewardOption pickWeightedReward(List<VillagerRewardOption> pool) {
        double totalWeight = 0D;
        List<VillagerRewardOption> weighted = new ArrayList<>(pool.size());
        for (VillagerRewardOption option : pool) {
            double weight = option.getWeight();
            if (weight <= 0D) {
                continue;
            }
            totalWeight += weight;
            weighted.add(option);
        }

        if (!weighted.isEmpty() && totalWeight > 0D) {
            double random = ThreadLocalRandom.current().nextDouble(totalWeight);
            double cumulative = 0D;
            for (VillagerRewardOption option : weighted) {
                cumulative += option.getWeight();
                if (random < cumulative) {
                    return option;
                }
            }
            return weighted.get(weighted.size() - 1);
        }

        if (!pool.isEmpty()) {
            return pool.get(ThreadLocalRandom.current().nextInt(pool.size()));
        }
        return null;
    }
}
