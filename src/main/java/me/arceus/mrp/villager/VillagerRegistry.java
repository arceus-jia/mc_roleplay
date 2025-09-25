package me.arceus.mrp.villager;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import me.arceus.mrp.MrpPlugin;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.logging.Logger;

public class VillagerRegistry {

    private final MrpPlugin plugin;
    private final Gson gson;
    private final Map<UUID, VillagerProfile> profiles = new HashMap<>();
    private final Map<UUID, Path> profileFiles = new HashMap<>();
    private final Map<Integer, UUID> idIndex = new HashMap<>();
    private final Path storageDir;
    private int nextCharacterId = 1;

    public VillagerRegistry(MrpPlugin plugin) {
        this.plugin = plugin;
        this.gson = new GsonBuilder().setPrettyPrinting().create();
        this.storageDir = plugin.getDataFolder().toPath().resolve("villagers");
    }

    public void loadVillagers() {
        Logger logger = plugin.getLogger();
        profiles.clear();
        profileFiles.clear();
        idIndex.clear();
        nextCharacterId = 1;
        final boolean[] needsResave = {false};
        try {
            Files.createDirectories(storageDir);
            try (var stream = Files.list(storageDir)) {
                stream.filter(path -> path.getFileName().toString().endsWith(".json"))
                    .forEach(path -> {
                        if (readProfile(path, logger)) {
                            needsResave[0] = true;
                        }
                    });
            }
            logger.info("已加载 " + profiles.size() + " 个村民配置");
            if (needsResave[0]) {
                logger.info("检测到缺失的角色 ID，正在重新保存配置文件...");
                saveVillagers();
            }
        } catch (IOException e) {
            logger.severe("加载村民配置失败: " + e.getMessage());
        }
    }

    public void saveVillagers() {
        Logger logger = plugin.getLogger();
        try {
            Files.createDirectories(storageDir);
            for (VillagerProfile profile : profiles.values()) {
                Path target = storageDir.resolve(buildFileName(profile));
                Path previous = profileFiles.put(profile.getVillagerId(), target);
                idIndex.put(profile.getCharacterId(), profile.getVillagerId());
                try (Writer writer = Files.newBufferedWriter(target, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING)) {
                    gson.toJson(profile, writer);
                }
                if (previous != null && !previous.equals(target)) {
                    try {
                        Files.deleteIfExists(previous);
                    } catch (IOException ex) {
                        logger.warning("删除旧村民文件失败 " + previous + ": " + ex.getMessage());
                    }
                }
            }
        } catch (IOException e) {
            logger.severe("保存村民配置失败: " + e.getMessage());
        }
    }

    public VillagerProfile getProfile(UUID villagerId) {
        return profiles.get(villagerId);
    }

    public VillagerProfile getProfileByCharacterId(int characterId) {
        UUID uuid = idIndex.get(characterId);
        return uuid != null ? profiles.get(uuid) : null;
    }

    public Collection<VillagerProfile> getProfiles() {
        return profiles.values();
    }

    public void registerProfile(VillagerProfile profile) {
        boolean assigned = ensureCharacterId(profile);
        if (!assigned) {
            nextCharacterId = Math.max(nextCharacterId, profile.getCharacterId() + 1);
        }
        profiles.put(profile.getVillagerId(), profile);
        profileFiles.put(profile.getVillagerId(), storageDir.resolve(buildFileName(profile)));
    }

    public void removeProfile(UUID villagerId) {
        profiles.remove(villagerId);
        Path file = profileFiles.remove(villagerId);
        if (file == null) {
            file = storageDir.resolve(villagerId + ".json");
        }
        try {
            Files.deleteIfExists(file);
        } catch (IOException e) {
            plugin.getLogger().warning("删除村民配置文件失败 " + file + ": " + e.getMessage());
        }
    }

    public boolean reloadProfile(UUID villagerId) {
        Path path = locateProfilePath(villagerId);
        if (path == null) {
            plugin.getLogger().warning("找不到村民配置文件: " + villagerId);
            return false;
        }
        try (Reader reader = Files.newBufferedReader(path)) {
            VillagerProfile profile = gson.fromJson(reader, VillagerProfile.class);
            if (profile == null) {
                return false;
            }
            VillagerProfile previous = profiles.get(villagerId);
            if (previous != null) {
                idIndex.remove(previous.getCharacterId());
            }
            boolean assigned = ensureCharacterId(profile);
            nextCharacterId = Math.max(nextCharacterId, profile.getCharacterId() + 1);
            profiles.put(profile.getVillagerId(), profile);
            profileFiles.put(profile.getVillagerId(), path);
            idIndex.put(profile.getCharacterId(), profile.getVillagerId());
            if (assigned) {
                saveVillagers();
            }
            return true;
        } catch (IOException e) {
            plugin.getLogger().warning("重载村民配置失败 " + path + ": " + e.getMessage());
            return false;
        }
    }

    public Collection<VillagerProfile> findByName(String keyword) {
        String lower = keyword.toLowerCase();
        return profiles.values().stream()
            .filter(profile -> profile.getName() != null && profile.getName().toLowerCase().contains(lower))
            .toList();
    }

    private boolean readProfile(Path path, Logger logger) {
        try (Reader reader = Files.newBufferedReader(path)) {
            VillagerProfile profile = gson.fromJson(reader, VillagerProfile.class);
            if (profile != null) {
                boolean assigned = ensureCharacterId(profile);
                profiles.put(profile.getVillagerId(), profile);
                profileFiles.put(profile.getVillagerId(), path);
                idIndex.put(profile.getCharacterId(), profile.getVillagerId());
                return assigned;
            }
        } catch (IOException e) {
            logger.warning("读取村民配置失败 " + path + ": " + e.getMessage());
        }
        return false;
    }

    private Path locateProfilePath(UUID villagerId) {
        Path stored = profileFiles.get(villagerId);
        if (stored != null && Files.exists(stored)) {
            return stored;
        }

        Path fallback = storageDir.resolve(villagerId + ".json");
        if (Files.exists(fallback)) {
            profileFiles.put(villagerId, fallback);
            return fallback;
        }

        try (var stream = Files.list(storageDir)) {
            return stream
                .filter(path -> path.getFileName().toString().contains(villagerId.toString()))
                .findFirst()
                .map(path -> {
                    profileFiles.put(villagerId, path);
                    return path;
                })
                .orElse(null);
        } catch (IOException e) {
            plugin.getLogger().warning("定位村民配置文件失败 " + villagerId + ": " + e.getMessage());
            return null;
        }
    }

    private boolean ensureCharacterId(VillagerProfile profile) {
        int currentId = profile.getCharacterId();
        if (currentId > 0) {
            UUID existing = idIndex.get(currentId);
            if (existing == null || existing.equals(profile.getVillagerId())) {
                idIndex.put(currentId, profile.getVillagerId());
                nextCharacterId = Math.max(nextCharacterId, currentId + 1);
                return false;
            }
        }

        while (idIndex.containsKey(nextCharacterId)) {
            nextCharacterId++;
        }
        profile.setCharacterId(nextCharacterId);
        idIndex.put(nextCharacterId, profile.getVillagerId());
        nextCharacterId++;
        return true;
    }

    private String buildFileName(VillagerProfile profile) {
        String base = profile.getName();
        if (base == null || base.isBlank()) {
            base = "villager";
        }
        base = base.trim().toLowerCase();
        base = base.replaceAll("[^a-z0-9-_]", "_");
        base = base.replaceAll("_+", "_");
        if (base.length() > 32) {
            base = base.substring(0, 32);
        }
        if (base.isEmpty()) {
            base = "villager";
        }
        return String.format("%05d_%s.json", profile.getCharacterId(), base);
    }
}
