package me.arceus.mrp.conversation;

import me.arceus.mrp.provider.ProviderMessage;

public class ConversationMessage {

    private final ProviderMessage.Role role;
    private final String content;
    private final java.time.Instant timestamp;

    public ConversationMessage(ProviderMessage.Role role, String content) {
        this(role, content, java.time.Instant.now());
    }

    public ConversationMessage(ProviderMessage.Role role, String content, java.time.Instant timestamp) {
        this.role = role;
        this.content = content;
        this.timestamp = timestamp == null ? java.time.Instant.now() : timestamp;
    }

    public ProviderMessage.Role getRole() {
        return role;
    }

    public String getContent() {
        return content;
    }

    public java.time.Instant getTimestamp() {
        return timestamp;
    }
}
