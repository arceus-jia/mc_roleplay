package me.arceus.mrp.conversation;

import me.arceus.mrp.provider.ProviderMessage;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public class ConversationSession {

    private final UUID playerId;
    private final UUID villagerId;
    private final List<ConversationMessage> messages = new ArrayList<>();
    private final Map<String, String> promptVariables = new HashMap<>();
    private boolean welcomeDelivered;

    public ConversationSession(UUID playerId, UUID villagerId) {
        this.playerId = playerId;
        this.villagerId = villagerId;
    }

    void initializeHistory(List<ConversationMessage> history) {
        messages.clear();
        messages.addAll(history);
    }

    void initializePromptVariables(Map<String, String> variables) {
        promptVariables.clear();
        if (variables != null) {
            promptVariables.putAll(variables);
        }
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
        welcomeDelivered = false;
    }

    public Map<String, String> getPromptVariables() {
        return Collections.unmodifiableMap(promptVariables);
    }

    public void setPromptVariable(String key, String value) {
        if (key == null) {
            return;
        }
        if (value == null) {
            promptVariables.remove(key);
        } else {
            promptVariables.put(key, value);
        }
    }

    public String getPromptVariable(String key) {
        return promptVariables.get(key);
    }

    public boolean hasPromptVariable(String key) {
        return promptVariables.containsKey(key);
    }

    public boolean isWelcomeDelivered() {
        return welcomeDelivered;
    }

    public void setWelcomeDelivered(boolean welcomeDelivered) {
        this.welcomeDelivered = welcomeDelivered;
    }
}
