package me.arceus.mrp.logging;

import me.arceus.mrp.MrpPlugin;
import me.arceus.mrp.provider.ProviderMessage;

import java.io.IOException;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

public class ConversationLogger {

    private static final DateTimeFormatter TIMESTAMP = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private final MrpPlugin plugin;
    private final Path logDirectory;
    private final ExecutorService executor;

    public ConversationLogger(MrpPlugin plugin) {
        this.plugin = plugin;
        this.logDirectory = plugin.getDataFolder().toPath().resolve("logs");
        this.executor = Executors.newSingleThreadExecutor(r -> {
            Thread thread = new Thread(r, "mrp-conversation-logger");
            thread.setDaemon(true);
            return thread;
        });
    }

    public void log(UUID villagerId,
                    String villagerName,
                    UUID playerId,
                    String playerName,
                    ProviderMessage.Role role,
                    String message) {
        if (message == null) {
            return;
        }
        executor.execute(() -> writeEntry(villagerId, villagerName, playerId, playerName, role, message));
    }

    private void writeEntry(UUID villagerId,
                            String villagerName,
                            UUID playerId,
                            String playerName,
                            ProviderMessage.Role role,
                            String message) {
        try {
            Files.createDirectories(logDirectory);
            Path file = logDirectory.resolve(buildFileName(villagerId, villagerName));
            String timestamp = LocalDateTime.now().format(TIMESTAMP);
            StringBuilder builder = new StringBuilder();
            builder.append("[")
                .append(timestamp)
                .append("] [")
                .append(role)
                .append("] player=")
                .append(playerName)
                .append(" (")
                .append(playerId)
                .append(") message=")
                .append(message.replace('\n', ' '))
                .append(System.lineSeparator());

            try (Writer writer = Files.newBufferedWriter(file,
                StandardOpenOption.CREATE,
                StandardOpenOption.APPEND)) {
                writer.write(builder.toString());
            }
        } catch (IOException e) {
            plugin.getLogger().warning("写入对话日志失败: " + e.getMessage());
        }
    }

    private String buildFileName(UUID villagerId, String villagerName) {
        String base = villagerName != null ? villagerName.trim().toLowerCase() : "villager";
        if (base.isEmpty()) {
            base = "villager";
        }
        base = base.replaceAll("[^a-z0-9-_]", "_");
        base = base.replaceAll("_+", "_");
        if (base.length() > 32) {
            base = base.substring(0, 32);
        }
        if (base.isEmpty()) {
            base = "villager";
        }
        return base + "_" + villagerId + ".log";
    }

    public void shutdown() {
        executor.shutdown();
        try {
            if (!executor.awaitTermination(3, TimeUnit.SECONDS)) {
                executor.shutdownNow();
            }
        } catch (InterruptedException e) {
            executor.shutdownNow();
            Thread.currentThread().interrupt();
        }
    }
}
