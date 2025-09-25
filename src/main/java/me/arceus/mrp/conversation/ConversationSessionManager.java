package me.arceus.mrp.conversation;

import me.arceus.mrp.MrpPlugin;
import me.arceus.mrp.config.ConversationSettings;
import me.arceus.mrp.provider.ProviderMessage;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public class ConversationSessionManager {

    private final MrpPlugin plugin;
    private final ConversationStorage storage;
    private final Map<String, ConversationSession> sessions = new HashMap<>();
    private final Map<UUID, UUID> activeVillager = new HashMap<>();

    public ConversationSessionManager(MrpPlugin plugin, ConversationStorage storage) {
        this.plugin = plugin;
        this.storage = storage;
    }

    public ConversationSession getOrCreate(UUID playerId, UUID villagerId) {
        String key = buildKey(playerId, villagerId);
        ConversationSession session = sessions.get(key);
        if (session == null) {
            session = new ConversationSession(playerId, villagerId);
            List<ConversationMessage> history = storage.loadHistory(playerId, villagerId);
            if (!history.isEmpty()) {
                session.initializeHistory(history);
            }
            sessions.put(key, session);
        }
        activeVillager.put(playerId, villagerId);
        return session;
    }

    public ConversationSession getSession(UUID playerId, UUID villagerId) {
        return sessions.get(buildKey(playerId, villagerId));
    }

    public void endSession(UUID playerId, UUID villagerId) {
        sessions.remove(buildKey(playerId, villagerId));

        UUID current = activeVillager.get(playerId);
        if (current != null && current.equals(villagerId)) {
            activeVillager.remove(playerId);
        }
    }

    public boolean endSessionForPlayer(UUID playerId) {
        UUID villagerId = activeVillager.remove(playerId);
        if (villagerId == null) {
            return false;
        }
        sessions.remove(buildKey(playerId, villagerId));
        return true;
    }

    public boolean clearSessionForPlayer(UUID playerId, UUID villagerId) {
        String key = buildKey(playerId, villagerId);
        ConversationSession session = sessions.get(key);
        if (session != null) {
            session.clearMessages();
        }
        boolean removed = storage.clearHistory(playerId, villagerId);
        sessions.remove(key);
        UUID current = activeVillager.get(playerId);
        if (current != null && current.equals(villagerId)) {
            activeVillager.remove(playerId);
        }
        return removed || session != null;
    }

    public UUID getActiveVillager(UUID playerId) {
        return activeVillager.get(playerId);
    }

    public List<ProviderMessage> buildPromptMessages(ConversationSession session) {
        ConversationSettings settings = plugin.getConfigService().getConversationSettings();
        int window = settings != null ? settings.getContextWindow() : 8;
        List<ConversationMessage> history = session.getMessages();
        int start = Math.max(history.size() - window, 0);

        List<ProviderMessage> result = new ArrayList<>();
        for (int i = start; i < history.size(); i++) {
            ConversationMessage message = history.get(i);
            result.add(new ProviderMessage(message.getRole(), message.getContent()));
        }
        return result;
    }

    public void shutdown() {
        sessions.clear();
        activeVillager.clear();
    }

    public void clearSessionsForVillager(UUID villagerId) {
        sessions.entrySet().removeIf(entry -> entry.getKey().endsWith(":" + villagerId));
        activeVillager.entrySet().removeIf(entry -> villagerId.equals(entry.getValue()));
        storage.clearAllForVillager(villagerId);
    }

    public ConversationMessage appendMessage(ConversationSession session, ProviderMessage.Role role, String content) {
        session.appendMessage(role, content);
        storage.saveHistory(session);
        List<ConversationMessage> messages = session.getMessages();
        return messages.isEmpty() ? null : messages.get(messages.size() - 1);
    }

    private String buildKey(UUID playerId, UUID villagerId) {
        return playerId + ":" + villagerId;
    }
}
