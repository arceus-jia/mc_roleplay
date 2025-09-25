package me.arceus.mrp.conversation;

import me.arceus.mrp.provider.ProviderMessage;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

public class ConversationSession {

    private final UUID playerId;
    private final UUID villagerId;
    private final List<ConversationMessage> messages = new ArrayList<>();

    public ConversationSession(UUID playerId, UUID villagerId) {
        this.playerId = playerId;
        this.villagerId = villagerId;
    }

    void initializeHistory(List<ConversationMessage> history) {
        messages.clear();
        messages.addAll(history);
    }

    public UUID getPlayerId() {
        return playerId;
    }

    public UUID getVillagerId() {
        return villagerId;
    }

    public void appendMessage(ProviderMessage.Role role, String content) {
        messages.add(new ConversationMessage(role, content));
    }

    public List<ConversationMessage> getMessages() {
        return Collections.unmodifiableList(messages);
    }

    void clearMessages() {
        messages.clear();
    }
}
