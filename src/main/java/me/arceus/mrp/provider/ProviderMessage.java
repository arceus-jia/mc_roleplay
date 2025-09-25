package me.arceus.mrp.provider;

public class ProviderMessage {

    public enum Role {
        SYSTEM,
        USER,
        ASSISTANT
    }

    private final Role role;
    private final String content;

    public ProviderMessage(Role role, String content) {
        this.role = role;
        this.content = content;
    }

    public Role getRole() {
        return role;
    }

    public String getContent() {
        return content;
    }
}
