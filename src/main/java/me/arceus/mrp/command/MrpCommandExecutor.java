package me.arceus.mrp.command;

import me.arceus.mrp.MrpPlugin;
import me.arceus.mrp.conversation.ConversationChatService;
import me.arceus.mrp.conversation.ConversationSessionManager;
import me.arceus.mrp.villager.VillagerProfile;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import org.bukkit.entity.Villager;
import org.bukkit.util.RayTraceResult;
import org.bukkit.util.Vector;

import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

public class MrpCommandExecutor implements CommandExecutor {

    private final MrpPlugin plugin;

    public MrpCommandExecutor(MrpPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length == 0) {
            return handleHelp(sender, label);
        }

        String sub = args[0].toLowerCase(Locale.ROOT);
        String[] subArgs = Arrays.copyOfRange(args, 1, args.length);

        switch (sub) {
            case "create":
                return handleCreate(sender, label, subArgs);
            case "say":
                return handleSay(sender, subArgs);
            case "history":
                return handleHistory(sender, subArgs);
            case "reload":
                return handleReload(sender);
            case "list":
                return handleList(sender, subArgs);
            case "end":
                return handleEnd(sender);
            case "character":
                return handleCharacter(sender, subArgs);
            case "help":
            default:
                return handleHelp(sender, label);
        }
    }

    private boolean handleCreate(CommandSender sender, String label, String[] args) {
        if (!requireAdmin(sender)) {
            return true;
        }
        if (!(sender instanceof Player player)) {
            sender.sendMessage("只有玩家可以执行该命令");
            return true;
        }
        if (args.length < 2) {
            sender.sendMessage("用法: /" + label + " create <名称> <描述...>");
            return true;
        }

        String name = args[0];
        String description = String.join(" ", Arrays.copyOfRange(args, 1, args.length));

        Location spawnLocation = player.getLocation();
        Villager villager = (Villager) player.getWorld().spawnEntity(spawnLocation, EntityType.VILLAGER);
        villager.setCustomName(name);
        villager.setCustomNameVisible(true);
        plugin.applyNpcProtection(villager);

        VillagerProfile profile = new VillagerProfile(
            villager.getUniqueId(),
            0,
            name,
            description,
            "",
            null
        );

        plugin.getVillagerRegistry().registerProfile(profile);
        plugin.getVillagerRegistry().saveVillagers();

        VillagerProfile registered = plugin.getVillagerRegistry().getProfile(villager.getUniqueId());
        player.sendMessage("已创建村民 [ID " + registered.getCharacterId() + "] " + villagerDisplayName(registered) + " (" + villager.getUniqueId() + ")");
        return true;
    }

    private boolean handleSay(CommandSender sender, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("只有玩家可以与村民对话");
            return true;
        }
        if (args.length == 0) {
            sender.sendMessage("用法: /mrp say <内容>");
            return true;
        }

        ConversationSessionManager sessionManager = plugin.getSessionManager();
        if (sessionManager == null) {
            sender.sendMessage("系统尚未初始化完成");
            return true;
        }

        UUID villagerId = sessionManager.getActiveVillager(player.getUniqueId());
        if (villagerId == null) {
            sender.sendMessage("请先右键与某位村民交互开始对话");
            return true;
        }

        VillagerProfile profile = plugin.getVillagerRegistry().getProfile(villagerId);
        if (profile == null) {
            sender.sendMessage("找不到该村民的资料");
            return true;
        }

        ConversationChatService chatService = plugin.getConversationChatService();
        if (chatService == null) {
            sender.sendMessage("系统尚未初始化完成");
            return true;
        }

        if (chatService.isProcessing(player.getUniqueId())) {
            sender.sendMessage("村民正在思考，请稍候再试。");
            return true;
        }

        String playerInput = String.join(" ", args);
        chatService.sendPlayerMessage(player, profile, playerInput);

        return true;
    }

    private boolean handleHistory(CommandSender sender, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("该命令仅限玩家使用");
            return true;
        }

        ConversationSessionManager sessionManager = plugin.getSessionManager();
        if (sessionManager == null) {
            sender.sendMessage("系统尚未初始化完成");
            return true;
        }

        UUID villagerId = sessionManager.getActiveVillager(player.getUniqueId());
        if (villagerId == null) {
            sender.sendMessage("当前没有进行中的村民对话。");
            return true;
        }

        VillagerProfile profile = plugin.getVillagerRegistry().getProfile(villagerId);
        if (profile == null) {
            sender.sendMessage("找不到该村民的资料");
            return true;
        }

        int page = 0;
        if (args.length > 0) {
            try {
                int parsed = Integer.parseInt(args[0]);
                if (parsed > 1) {
                    page = parsed - 1;
                }
            } catch (NumberFormatException ignored) {
                sender.sendMessage("页码应为数字。");
            }
        }

        plugin.getConversationUiService().openConversation(player, profile, page);
        return true;
    }

    private boolean handleReload(CommandSender sender) {
        if (!requireAdmin(sender)) {
            return true;
        }
        plugin.reloadSettings(true);
        sender.sendMessage("MRP 配置已重载。");
        return true;
    }

    private boolean handleList(CommandSender sender, String[] args) {
        String keyword = args.length > 0 ? String.join(" ", args).toLowerCase(Locale.ROOT) : null;
        List<VillagerProfile> profiles = plugin.getVillagerRegistry().getProfiles().stream()
            .filter(profile -> keyword == null
                || (profile.getName() != null && profile.getName().toLowerCase(Locale.ROOT).contains(keyword))
                || (profile.getDescription() != null && profile.getDescription().toLowerCase(Locale.ROOT).contains(keyword)))
            .sorted((a, b) -> Integer.compare(a.getCharacterId(), b.getCharacterId()))
            .toList();

        if (profiles.isEmpty()) {
            sender.sendMessage(keyword == null ? "尚未注册村民" : "没有匹配的村民");
            return true;
        }

        sender.sendMessage("当前村民列表 (" + profiles.size() + "):");
        profiles.forEach(profile -> sender.sendMessage(
            "- " + formatVillagerLabel(profile) + " (" + profile.getVillagerId() + ")"
        ));
        return true;
    }

    private boolean handleEnd(CommandSender sender) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("该命令仅限玩家使用");
            return true;
        }

        boolean ended = plugin.getSessionManager().endSessionForPlayer(player.getUniqueId());
        if (ended) {
            sender.sendMessage("已结束当前村民对话。");
        } else {
            sender.sendMessage("当前没有进行中的对话。");
        }
        return true;
    }

    private boolean handleCharacter(CommandSender sender, String[] args) {
        if (args.length == 0) {
            sender.sendMessage("用法: /mrp character <reload|clear|tp|freeze|delete> ...");
            return true;
        }

        String action = args[0].toLowerCase(Locale.ROOT);
        String token = args.length > 1 ? String.join(" ", Arrays.copyOfRange(args, 1, args.length)) : null;

        switch (action) {
            case "reload":
                return handleCharacterReload(sender, token);
            case "clear":
                return handleCharacterClear(sender, token);
            case "tp":
                return handleCharacterTeleport(sender, token);
            case "freeze":
                return handleCharacterFreeze(sender, args);
            case "delete":
                return handleCharacterDelete(sender, token);
            default:
                sender.sendMessage("未知的 character 子命令: " + action);
                sender.sendMessage("用法: /mrp character <reload|clear|tp|freeze|delete> ...");
                return true;
        }
    }

    private boolean handleCharacterReload(CommandSender sender, String token) {
        if (!requireAdmin(sender)) {
            return true;
        }
        VillagerProfile profile = resolveVillager(sender, token, sender instanceof Player);
        if (profile == null) {
            return true;
        }
        boolean success = plugin.getVillagerRegistry().reloadProfile(profile.getVillagerId());
        if (success) {
            VillagerProfile updated = plugin.getVillagerRegistry().getProfile(profile.getVillagerId());
            sender.sendMessage("已重载村民配置: " + formatVillagerLabel(updated));
        } else {
            sender.sendMessage("重载失败，检查配置文件是否存在且内容正确。");
        }
        return true;
    }

    private boolean handleCharacterClear(CommandSender sender, String token) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("该命令仅限玩家使用");
            return true;
        }
        VillagerProfile profile = resolveVillager(sender, token, true);
        if (profile == null) {
            return true;
        }
        boolean removed = plugin.getSessionManager().clearSessionForPlayer(player.getUniqueId(), profile.getVillagerId());
        if (removed) {
            sender.sendMessage("已清空你与 " + formatVillagerLabel(profile) + " 的会话历史。");
        } else {
            sender.sendMessage("当前没有与 " + formatVillagerLabel(profile) + " 的会话记录。");
        }
        return true;
    }

    private boolean handleCharacterTeleport(CommandSender sender, String token) {
        if (!requireAdmin(sender)) {
            return true;
        }
        if (!(sender instanceof Player player)) {
            sender.sendMessage("该命令仅限玩家使用");
            return true;
        }

        VillagerProfile profile = resolveVillager(sender, token, true);
        if (profile == null) {
            return true;
        }

        Entity entity = Bukkit.getEntity(profile.getVillagerId());
        if (!(entity instanceof Villager villager)) {
            sender.sendMessage("未在世界中找到该村民实体，确保它已被加载。");
            return true;
        }

        var targetLocation = player.getLocation().clone();
        sender.sendMessage("正在尝试传送 " + formatVillagerLabel(profile) + " ...");

        var world = targetLocation.getWorld();
        if (world != null) {
            int chunkX = targetLocation.getBlockX() >> 4;
            int chunkZ = targetLocation.getBlockZ() >> 4;
            if (!world.isChunkLoaded(chunkX, chunkZ)) {
                world.getChunkAt(chunkX, chunkZ);
            }
        }

        boolean success = villager.teleport(targetLocation);
        plugin.applyNpcProtection(villager);
        if (success) {
            sender.sendMessage("已将 " + formatVillagerLabel(profile) + " 传送至当前位置。");
        } else {
            sender.sendMessage("传送失败，目标区块可能未加载。");
        }
        return true;
    }

    private boolean handleCharacterFreeze(CommandSender sender, String[] args) {
        if (!requireAdmin(sender)) {
            return true;
        }
        if (args.length < 3) {
            sender.sendMessage("用法: /mrp character freeze <名称|ID|UUID> <on|off>");
            return true;
        }

        String stateToken = args[args.length - 1].toLowerCase(Locale.ROOT);
        boolean freeze;
        if (stateToken.equals("on") || stateToken.equals("true") || stateToken.equals("yes") || stateToken.equals("enable")) {
            freeze = true;
        } else if (stateToken.equals("off") || stateToken.equals("false") || stateToken.equals("no") || stateToken.equals("disable")) {
            freeze = false;
        } else {
            sender.sendMessage("状态参数仅支持 on/off。");
            return true;
        }

        String targetToken = String.join(" ", Arrays.copyOfRange(args, 1, args.length - 1));
        VillagerProfile profile = resolveVillager(sender, targetToken, sender instanceof Player);
        if (profile == null) {
            return true;
        }

        profile.setFreezeAi(freeze);
        plugin.getVillagerRegistry().saveVillagers();

        Entity entity = Bukkit.getEntity(profile.getVillagerId());
        if (entity instanceof Villager villager) {
            plugin.applyNpcProtection(villager);
        }

        sender.sendMessage("已将 " + formatVillagerLabel(profile) + " 的行为状态设置为 " + (freeze ? "静止" : "可移动") + "。");
        return true;
    }

    private boolean handleCharacterDelete(CommandSender sender, String token) {
        if (!requireAdmin(sender)) {
            return true;
        }
        VillagerProfile profile = resolveVillager(sender, token, sender instanceof Player);
        if (profile == null) {
            return true;
        }

        UUID villagerId = profile.getVillagerId();
        plugin.getSessionManager().clearSessionsForVillager(villagerId);
        plugin.getVillagerRegistry().removeProfile(villagerId);
        plugin.getVillagerRegistry().saveVillagers();

        Entity entity = Bukkit.getEntity(villagerId);
        if (entity instanceof Villager) {
            entity.remove();
        }

        sender.sendMessage("已删除村民 " + formatVillagerLabel(profile) + " (" + villagerId + ")");
        return true;
    }

    private boolean handleHelp(CommandSender sender, String label) {
        sender.sendMessage("MRP 可用命令：");
        sender.sendMessage("/" + label + " create <名称> <描述...> - 创建村民 (管理员)");
        sender.sendMessage("/" + label + " say <内容> - 向最近交互的村民发送消息");
        sender.sendMessage("/" + label + " history [页码] - 打开当前村民的对话面板");
        sender.sendMessage("/" + label + " list [关键字] - 查看已注册村民列表");
        sender.sendMessage("/" + label + " end - 结束当前村民对话");
        sender.sendMessage("/" + label + " reload - 重载全局配置 (管理员)");
        sender.sendMessage("/" + label + " character reload <名称|ID|UUID> - 重载村民配置 (管理员)");
        sender.sendMessage("/" + label + " character clear [名称|ID|UUID] - 清空与村民的对话历史");
        sender.sendMessage("/" + label + " character tp [名称|ID|UUID] - 将村民传送到你身边");
        sender.sendMessage("/" + label + " character freeze <名称|ID|UUID> <on|off> - 切换村民是否原地静止");
        sender.sendMessage("/" + label + " character delete <名称|ID|UUID> - 删除村民 (管理员)");
        sender.sendMessage("/" + label + " help - 查看命令帮助");
        return true;
    }

    private VillagerProfile resolveVillager(CommandSender sender, String token, boolean allowFallbackToTarget) {
        if (token != null && !token.isBlank()) {
            try {
                int numericId = Integer.parseInt(token);
                VillagerProfile byId = plugin.getVillagerRegistry().getProfileByCharacterId(numericId);
                if (byId != null) {
                    return byId;
                }
            } catch (NumberFormatException ignored) {
                // not an integer, continue parsing
            }
            UUID id = parseUuid(token);
            if (id != null) {
                VillagerProfile profile = plugin.getVillagerRegistry().getProfile(id);
                if (profile == null) {
                    sender.sendMessage("未找到 UUID 对应的村民: " + token);
                    return null;
                }
                return profile;
            }

            List<VillagerProfile> matches = plugin.getVillagerRegistry().findByName(token).stream()
                .sorted((a, b) -> Integer.compare(a.getCharacterId(), b.getCharacterId()))
                .toList();

            if (matches.isEmpty()) {
                sender.sendMessage("未找到名称包含 '" + token + "' 的村民。");
                return null;
            }
            if (matches.size() > 1) {
                sender.sendMessage("匹配到多个村民，请使用 UUID 指定：");
                matches.forEach(match -> sender.sendMessage(
                    "- " + formatVillagerLabel(match) + " (" + match.getVillagerId() + ")"
                ));
                return null;
            }
            return matches.get(0);
        }

        if (!allowFallbackToTarget || !(sender instanceof Player player)) {
            sender.sendMessage("请提供村民名称或 UUID");
            return null;
        }

        Villager target = getTargetVillager(player, 5);
        if (target != null) {
            VillagerProfile profile = plugin.getVillagerRegistry().getProfile(target.getUniqueId());
            if (profile != null) {
                return profile;
            }
        }

        UUID active = plugin.getSessionManager().getActiveVillager(player.getUniqueId());
        if (active != null) {
            VillagerProfile profile = plugin.getVillagerRegistry().getProfile(active);
            if (profile != null) {
                return profile;
            }
        }

        sender.sendMessage("未找到目标村民，请提供名称或 UUID。");
        return null;
    }

    private Villager getTargetVillager(Player player, int range) {
        Vector direction = player.getEyeLocation().getDirection();
        RayTraceResult hit = player.getWorld().rayTraceEntities(
            player.getEyeLocation(),
            direction,
            range,
            entity -> entity instanceof Villager && !entity.getUniqueId().equals(player.getUniqueId())
        );

        if (hit != null && hit.getHitEntity() instanceof Villager villager) {
            return villager;
        }

        return player.getWorld().getNearbyEntities(player.getLocation(), range, range, range, e -> e instanceof Villager)
            .stream()
            .map(entity -> (Villager) entity)
            .min(Comparator.comparingDouble(entity -> entity.getLocation().distanceSquared(player.getLocation())))
            .orElse(null);
    }

    private UUID parseUuid(String token) {
        try {
            return UUID.fromString(token);
        } catch (IllegalArgumentException ex) {
            return null;
        }
    }

    private boolean requireAdmin(CommandSender sender) {
        if (!(sender instanceof Player) || sender.hasPermission("mrp.admin")) {
            return true;
        }
        sender.sendMessage("你没有权限执行该命令。");
        return false;
    }

    private String villagerDisplayName(VillagerProfile profile) {
        String name = profile.getName();
        if (name == null || name.isBlank()) {
            return "村民#" + profile.getCharacterId();
        }
        return name;
    }

    private String formatVillagerLabel(VillagerProfile profile) {
        return "[ID " + profile.getCharacterId() + "] " + villagerDisplayName(profile);
    }
}
