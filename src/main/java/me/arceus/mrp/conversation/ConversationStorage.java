package me.arceus.mrp.conversation;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import me.arceus.mrp.MrpPlugin;
import me.arceus.mrp.provider.ProviderMessage;
import me.arceus.mrp.villager.VillagerProfile;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.logging.Logger;

public class ConversationStorage {

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    private final MrpPlugin plugin;
    private final Path baseDir;

    public ConversationStorage(MrpPlugin plugin) {
        this.plugin = plugin;
        this.baseDir = plugin.getDataFolder().toPath().resolve("conversations");
    }

    public ConversationSnapshot loadSnapshot(UUID playerId, UUID villagerId) {
        Path file = conversationFile(playerId, villagerId);
        Path legacyFile = baseDir.resolve(villagerId.toString()).resolve(playerId.toString() + ".json");
        if (!Files.exists(file) && Files.exists(legacyFile)) {
            file = legacyFile;
        }
        if (!Files.exists(file)) {
            return ConversationSnapshot.empty();
        }
        try (Reader reader = Files.newBufferedReader(file)) {
            JsonElement root = JsonParser.parseReader(reader);
            if (root == null || root.isJsonNull()) {
                return ConversationSnapshot.empty();
            }
            if (root.isJsonArray()) {
                MessageRecord[] records = GSON.fromJson(root, MessageRecord[].class);
                return new ConversationSnapshot(toMessages(records), Collections.emptyMap(), false);
            }
            if (root.isJsonObject()) {
                StoredConversation stored = GSON.fromJson(root, StoredConversation.class);
                List<ConversationMessage> messages = toMessages(stored.messages);
                Map<String, String> variables = stored.promptVariables != null
                    ? new HashMap<>(stored.promptVariables)
                    : Collections.emptyMap();
                boolean welcomeDelivered = stored.welcomeDelivered != null && stored.welcomeDelivered;
                return new ConversationSnapshot(messages, variables, welcomeDelivered);
            }
            return ConversationSnapshot.empty();
        } catch (IOException e) {
            plugin.getLogger().warning("读取对话历史失败: " + e.getMessage());
            return ConversationSnapshot.empty();
        }
    }

    public void saveHistory(ConversationSession session) {
        saveSnapshot(session);
    }

    private void saveSnapshot(ConversationSession session) {
        UUID playerId = session.getPlayerId();
        UUID villagerId = session.getVillagerId();
        List<ConversationMessage> messages = session.getMessages();
        Map<String, String> promptVariables = session.getPromptVariables();

        Path file = conversationFile(playerId, villagerId);
        Path legacyFile = baseDir.resolve(villagerId.toString()).resolve(playerId.toString() + ".json");
        try {
            Files.createDirectories(file.getParent());
            try (Writer writer = Files.newBufferedWriter(file, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING)) {
                MessageRecord[] records = new MessageRecord[messages.size()];
                for (int i = 0; i < messages.size(); i++) {
                    ConversationMessage message = messages.get(i);
                    records[i] = MessageRecord.from(message);
                }
                StoredConversation stored = new StoredConversation();
                stored.messages = new ArrayList<>(Arrays.asList(records));
                if (promptVariables != null && !promptVariables.isEmpty()) {
                    stored.promptVariables = new HashMap<>(promptVariables);
                }
                stored.welcomeDelivered = session.isWelcomeDelivered();
                GSON.toJson(stored, writer);
            }
            if (!file.equals(legacyFile) && Files.exists(legacyFile)) {
                Files.deleteIfExists(legacyFile);
                Path legacyParent = legacyFile.getParent();
                if (legacyParent != null && Files.exists(legacyParent)) {
                    try (var stream = Files.list(legacyParent)) {
                        if (!stream.findAny().isPresent()) {
                            Files.deleteIfExists(legacyParent);
                        }
                    }
                }
            }
        } catch (IOException e) {
            plugin.getLogger().warning("写入对话历史失败: " + e.getMessage());
        }
    }

    public static class ConversationSnapshot {
        private final List<ConversationMessage> messages;
        private final Map<String, String> promptVariables;
        private final boolean welcomeDelivered;

        ConversationSnapshot(List<ConversationMessage> messages, Map<String, String> promptVariables, boolean welcomeDelivered) {
            this.messages = messages != null
                ? Collections.unmodifiableList(new ArrayList<>(messages))
                : Collections.emptyList();
            this.promptVariables = promptVariables != null
                ? Collections.unmodifiableMap(new HashMap<>(promptVariables))
                : Collections.emptyMap();
            this.welcomeDelivered = welcomeDelivered;
        }

        public static ConversationSnapshot empty() {
            return new ConversationSnapshot(Collections.emptyList(), Collections.emptyMap(), false);
        }

        public List<ConversationMessage> messages() {
            return messages;
        }

        public Map<String, String> promptVariables() {
            return promptVariables;
        }

        public boolean welcomeDelivered() {
            return welcomeDelivered;
        }
    }

    private static class StoredConversation {
        List<MessageRecord> messages;
        Map<String, String> promptVariables;
        Boolean welcomeDelivered;
    }

    private List<ConversationMessage> toMessages(MessageRecord[] records) {
        if (records == null || records.length == 0) {
            return Collections.emptyList();
        }
        return toMessages(Arrays.asList(records));
    }

    private List<ConversationMessage> toMessages(List<MessageRecord> records) {
        if (records == null || records.isEmpty()) {
            return Collections.emptyList();
        }
        List<ConversationMessage> messages = new ArrayList<>(records.size());
        for (MessageRecord record : records) {
            if (record == null) {
                continue;
            }
            ProviderMessage.Role role;
            try {
                role = ProviderMessage.Role.valueOf(record.role);
            } catch (IllegalArgumentException | NullPointerException ex) {
                role = ProviderMessage.Role.ASSISTANT;
            }
            Instant timestamp;
            try {
                timestamp = Instant.parse(record.timestamp);
            } catch (Exception ex) {
                timestamp = Instant.now();
            }
            messages.add(new ConversationMessage(role, record.content, timestamp));
        }
        return messages;
    }

    public boolean clearHistory(UUID playerId, UUID villagerId) {
        Path file = conversationFile(playerId, villagerId);
        Path legacyFile = baseDir.resolve(villagerId.toString()).resolve(playerId.toString() + ".json");
        boolean deleted = false;
        try {
            deleted = Files.deleteIfExists(file) || deleted;
            Path parent = file.getParent();
            if (parent != null && Files.exists(parent)) {
                try (var stream = Files.list(parent)) {
                    if (!stream.findAny().isPresent()) {
                        Files.deleteIfExists(parent);
                    }
                }
            }

            if (!file.equals(legacyFile) && Files.exists(legacyFile)) {
                deleted = Files.deleteIfExists(legacyFile) || deleted;
                Path legacyParent = legacyFile.getParent();
                if (legacyParent != null && Files.exists(legacyParent)) {
                    try (var stream = Files.list(legacyParent)) {
                        if (!stream.findAny().isPresent()) {
                            Files.deleteIfExists(legacyParent);
                        }
                    }
                }
            }
            return deleted;
        } catch (IOException e) {
            plugin.getLogger().warning("删除对话历史失败: " + e.getMessage());
            return false;
        }
    }

    public void clearAllForVillager(UUID villagerId) {
        Path dir = conversationDirectory(villagerId);
        if (!Files.exists(dir)) {
            if (!Files.exists(baseDir)) {
                return;
            }
            try (var stream = Files.list(baseDir)) {
                dir = stream
                    .filter(path -> Files.isDirectory(path) && path.getFileName().toString().contains(villagerId.toString()))
                    .findFirst()
                    .orElse(dir);
            } catch (IOException e) {
                plugin.getLogger().warning("清理村民对话历史时扫描目录失败: " + e.getMessage());
            }
        }
        if (!Files.exists(dir)) {
            return;
        }
        try (var stream = Files.walk(dir)) {
            stream.sorted((a, b) -> b.getNameCount() - a.getNameCount())
                .forEach(path -> {
                    try {
                        Files.deleteIfExists(path);
                    } catch (IOException e) {
                        Logger logger = plugin.getLogger();
                        logger.warning("删除对话历史文件失败 " + path + ": " + e.getMessage());
                    }
                });
        } catch (IOException e) {
            plugin.getLogger().warning("清理村民对话历史失败: " + e.getMessage());
        }
    }

    private Path conversationFile(UUID playerId, UUID villagerId) {
        return conversationDirectory(villagerId).resolve(playerId.toString() + ".json");
    }

    private Path conversationDirectory(UUID villagerId) {
        VillagerProfile profile = plugin.getVillagerRegistry().getProfile(villagerId);
        String dirName;
        if (profile != null && profile.getCharacterId() > 0) {
            dirName = String.format("%05d_%s", profile.getCharacterId(), villagerId);
        } else {
            dirName = villagerId.toString();
        }
        return baseDir.resolve(dirName);
    }

    public void shutdown() {
        // currently no async resources to close; placeholder for future use
    }

    private static class MessageRecord {
        String role;
        String content;
        String timestamp;

        static MessageRecord from(ConversationMessage message) {
            MessageRecord record = new MessageRecord();
            record.role = message.getRole().name();
            record.content = message.getContent();
            record.timestamp = message.getTimestamp().toString();
            return record;
        }
    }
}
