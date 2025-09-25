package me.arceus.mrp.ui;

import me.arceus.mrp.MrpPlugin;
import me.arceus.mrp.conversation.ConversationChatService;
import me.arceus.mrp.conversation.ConversationMessage;
import me.arceus.mrp.conversation.ConversationSession;
import me.arceus.mrp.conversation.ConversationSessionManager;
import me.arceus.mrp.provider.ProviderMessage;
import me.arceus.mrp.villager.VillagerProfile;
import me.arceus.mrp.villager.VillagerRegistry;
import net.md_5.bungee.api.ChatColor;
import net.md_5.bungee.api.chat.ComponentBuilder;
import net.md_5.bungee.api.chat.HoverEvent;
import net.md_5.bungee.api.chat.TextComponent;
import net.md_5.bungee.api.chat.ClickEvent;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.player.AsyncPlayerChatEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Presents a lightweight inventory-based conversation view and bridges chat capture
 * back into the shared conversation pipeline.
 */
public class ConversationUiService implements Listener {

    private static final int INVENTORY_SIZE = 27;
    private static final int PREV_SLOT = 18;
    private static final int RESTART_SLOT = 20;
    private static final int TALK_SLOT = 22;
    private static final int END_SLOT = 24;
    private static final int NEXT_SLOT = 26;
    private static final int[] HISTORY_SLOTS = {
        0, 1, 2, 3, 4, 5, 6, 7, 8,
        9, 10, 11, 12, 13, 14, 15, 16, 17
    };

    private final MrpPlugin plugin;
    private final ConversationChatService chatService;
    private final ConversationSessionManager sessionManager;
    private final VillagerRegistry villagerRegistry;

    private final Map<UUID, ConversationViewContext> openViews = new ConcurrentHashMap<>();
    private final Map<UUID, UUID> chatCaptureTargets = new ConcurrentHashMap<>();

    public ConversationUiService(MrpPlugin plugin, ConversationChatService chatService) {
        this.plugin = plugin;
        this.chatService = chatService;
        this.sessionManager = plugin.getSessionManager();
        this.villagerRegistry = plugin.getVillagerRegistry();
    }

    public void openConversation(Player player, VillagerProfile profile) {
        openConversation(player, profile, 0);
    }

    private void openConversation(Player player, VillagerProfile profile, int page) {
        if (profile == null) {
            player.sendMessage("找不到该村民的资料");
            return;
        }
        UUID playerId = player.getUniqueId();
        ConversationSession session = sessionManager.getOrCreate(playerId, profile.getVillagerId());
        List<ConversationMessage> history = session.getMessages();
        int perPage = HISTORY_SLOTS.length;
        int totalMessages = history.size();
        int totalPages = totalMessages == 0 ? 1 : (int) Math.ceil(totalMessages / (double) perPage);
        int clampedPage = Math.max(0, Math.min(page, totalPages - 1));

        Inventory inventory = buildInventory(player, profile, history, clampedPage, totalPages);
        player.openInventory(inventory);
        openViews.put(playerId, new ConversationViewContext(profile.getVillagerId(), clampedPage, totalPages));
    }

    private Inventory buildInventory(Player player, VillagerProfile profile, List<ConversationMessage> history, int page, int totalPages) {
        String villagerName = profile.getName() != null ? profile.getName() : "村民";
        String title = "与 " + villagerName + " 对话";
        if (title.length() > 32) {
            title = title.substring(0, 32);
        }

        Inventory inventory = Bukkit.createInventory(null, INVENTORY_SIZE, title);
        int perPage = HISTORY_SLOTS.length;
        int totalMessages = history.size();

        int start = Math.max(totalMessages - perPage * (page + 1), 0);
        int end = Math.max(totalMessages - perPage * page, 0);
        int index = 0;
        for (int slot : HISTORY_SLOTS) {
            int offset = start + index;
            if (offset >= end) {
                break;
            }
            ConversationMessage message = history.get(offset);
            boolean fromPlayer = message.getRole() == ProviderMessage.Role.USER;
            inventory.setItem(slot, createMessageItem(message, fromPlayer, player.getName(), villagerName));
            index++;
        }

        inventory.setItem(PREV_SLOT, createPageButton(page > 0, "上一页", page, totalPages));
        inventory.setItem(NEXT_SLOT, createPageButton(page < totalPages - 1, "下一页", page, totalPages));
        inventory.setItem(TALK_SLOT, createControlItem(
            Material.WRITABLE_BOOK,
            "开始对话",
            List.of(
                "关闭界面后在聊天栏输入",
                "输入 cancel 或 取消 退出"
            )
        ));

        inventory.setItem(RESTART_SLOT, createControlItem(
            Material.REDSTONE_TORCH,
            "重置对话",
            List.of("清空历史记录并重新开始")
        ));

        inventory.setItem(END_SLOT, createControlItem(
            Material.BARRIER,
            "结束对话",
            List.of("结束会话并关闭界面")
        ));

        fillPadding(inventory);
        return inventory;
    }

    private ItemStack createMessageItem(ConversationMessage message, boolean fromPlayer, String playerName, String villagerName) {
        Material material = fromPlayer ? Material.PAPER : Material.EMERALD;
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            String speaker = fromPlayer ? playerName : villagerName;
            String content = message.getContent().replace('\n', ' ');
            String preview = content.length() > 16 ? content.substring(0, 16) + "..." : content;
            meta.setDisplayName(speaker + ": " + preview);
            List<String> lore = wrapLore(message.getContent());
            meta.setLore(lore);
            item.setItemMeta(meta);
        }
        return item;
    }

    private ItemStack createPageButton(boolean enabled, String name, int page, int totalPages) {
        Material material = enabled ? Material.ARROW : Material.GRAY_DYE;
        String pageInfo = "当前页: " + (page + 1) + "/" + Math.max(totalPages, 1);
        List<String> lore = enabled
            ? List.of("点击查看", pageInfo)
            : List.of("没有更多记录", pageInfo);
        return createControlItem(material, name, lore);
    }

    private ItemStack createControlItem(Material material, String name, List<String> lore) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(name);
            meta.setLore(lore);
            item.setItemMeta(meta);
        }
        return item;
    }

    private void fillPadding(Inventory inventory) {
        ItemStack filler = createControlItem(Material.GRAY_STAINED_GLASS_PANE, " ", Collections.emptyList());
        for (int slot = 18; slot < INVENTORY_SIZE; slot++) {
            if (slot == PREV_SLOT || slot == RESTART_SLOT || slot == TALK_SLOT || slot == END_SLOT || slot == NEXT_SLOT) {
                continue;
            }
            if (inventory.getItem(slot) == null) {
                inventory.setItem(slot, filler);
            }
        }
    }

    private List<String> wrapLore(String content) {
        List<String> lines = new ArrayList<>();
        if (content == null || content.isEmpty()) {
            return lines;
        }
        String normalised = content.replace("\r", "");
        for (String part : normalised.split("\n")) {
            int index = 0;
            while (index < part.length()) {
                int end = Math.min(index + 24, part.length());
                lines.add(part.substring(index, end));
                index = end;
            }
        }
        return lines;
    }

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) {
            return;
        }
        int rawSlot = event.getRawSlot();
        if (rawSlot < 0 || rawSlot >= event.getView().getTopInventory().getSize()) {
            return;
        }

        ConversationViewContext context = openViews.get(player.getUniqueId());
        if (context == null) {
            return;
        }

        event.setCancelled(true);

        UUID villagerId = context.getVillagerId();

        if (rawSlot == PREV_SLOT) {
            if (context.getPage() > 0) {
                VillagerProfile profile = villagerRegistry.getProfile(villagerId);
                if (profile != null) {
                    openConversation(player, profile, context.getPage() - 1);
                }
            }
            return;
        }
        if (rawSlot == NEXT_SLOT) {
            if (context.getPage() < context.getTotalPages() - 1) {
                VillagerProfile profile = villagerRegistry.getProfile(villagerId);
                if (profile != null) {
                    openConversation(player, profile, context.getPage() + 1);
                }
            }
            return;
        }
        if (rawSlot == RESTART_SLOT) {
            handleRestartButton(player, villagerId);
            return;
        }
        if (rawSlot == TALK_SLOT) {
            handleTalkButton(player, villagerId);
            return;
        }
        if (rawSlot == END_SLOT) {
            handleEndButton(player, villagerId);
        }
    }

    private void handleTalkButton(Player player, UUID villagerId) {
        VillagerProfile profile = villagerRegistry.getProfile(villagerId);
        if (profile == null) {
            player.sendMessage("找不到该村民的资料");
            player.closeInventory();
            return;
        }
        chatCaptureTargets.put(player.getUniqueId(), villagerId);
        player.closeInventory();
        String name = profile.getName() != null ? profile.getName() : "村民";
        player.sendMessage("请在聊天栏输入要对 " + name + " 说的话（输入 cancel 或 取消 退出）。");
    }

    private void handleRestartButton(Player player, UUID villagerId) {
        VillagerProfile profile = villagerRegistry.getProfile(villagerId);
        if (profile == null) {
            player.sendMessage("找不到该村民的资料");
            player.closeInventory();
            return;
        }

        chatCaptureTargets.remove(player.getUniqueId());
        boolean cleared = sessionManager.clearSessionForPlayer(player.getUniqueId(), villagerId);
        String name = profile.getName() != null ? profile.getName() : "村民";
        if (cleared) {
            player.sendMessage("已重置你与 " + name + " 的对话。");
        } else {
            player.sendMessage("当前没有可重置的历史。");
        }
        openConversation(player, profile);
    }

    private void handleEndButton(Player player, UUID villagerId) {
        sessionManager.endSession(player.getUniqueId(), villagerId);
        chatCaptureTargets.remove(player.getUniqueId());
        player.closeInventory();
        VillagerProfile profile = villagerRegistry.getProfile(villagerId);
        String name = profile != null && profile.getName() != null ? profile.getName() : "村民";
        player.sendMessage("你结束了与 " + name + " 的对话。");
    }

    @EventHandler
    public void onInventoryClose(InventoryCloseEvent event) {
        if (!(event.getPlayer() instanceof Player player)) {
            return;
        }
        openViews.remove(player.getUniqueId());
    }

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        UUID playerId = event.getPlayer().getUniqueId();
        openViews.remove(playerId);
        chatCaptureTargets.remove(playerId);
    }

    @EventHandler
    public void onPlayerChat(AsyncPlayerChatEvent event) {
        Player player = event.getPlayer();
        UUID villagerId = chatCaptureTargets.get(player.getUniqueId());
        if (villagerId == null) {
            return;
        }

        event.setCancelled(true);
        String message = event.getMessage().trim();

        Bukkit.getScheduler().runTask(plugin, () -> handleCapturedChat(player, villagerId, message));
    }

    private void handleCapturedChat(Player player, UUID villagerId, String message) {
        UUID tracked = chatCaptureTargets.get(player.getUniqueId());
        if (tracked == null || !tracked.equals(villagerId)) {
            return;
        }

        VillagerProfile profile = villagerRegistry.getProfile(villagerId);
        if (profile == null) {
            player.sendMessage("找不到该村民的资料");
            chatCaptureTargets.remove(player.getUniqueId());
            return;
        }

        if (message.equalsIgnoreCase("cancel") || message.equalsIgnoreCase("取消")) {
            player.sendMessage("已取消对话输入。");
            chatCaptureTargets.remove(player.getUniqueId());
            openConversation(player, profile);
            return;
        }

        if (chatService.isProcessing(player.getUniqueId())) {
            player.sendMessage("村民正在思考，请稍候再试。");
            openConversation(player, profile);
            return;
        }

        CompletableFuture<String> future = chatService.sendPlayerMessage(player, profile, message);
        future.whenComplete((reply, throwable) -> Bukkit.getScheduler().runTask(plugin, () -> {
            if (!player.isOnline()) {
                chatCaptureTargets.remove(player.getUniqueId());
                return;
            }
            if (throwable == null) {
                VillagerProfile refreshed = villagerRegistry.getProfile(villagerId);
                if (refreshed == null) {
                    refreshed = profile;
                }
                sendHistoryShortcut(player, refreshed);
                chatCaptureTargets.put(player.getUniqueId(), villagerId);
            } else {
                chatCaptureTargets.put(player.getUniqueId(), villagerId);
            }
        }));
    }

    private void sendHistoryShortcut(Player player, VillagerProfile profile) {
        String name = profile.getName() != null ? profile.getName() : "村民";
        TextComponent prefix = new TextComponent(name + " 的回复已更新 ");
        prefix.setColor(ChatColor.GRAY);

        TextComponent button = new TextComponent("[点击查看历史]");
        button.setColor(ChatColor.GOLD);
        button.setBold(true);
        button.setClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, "/mrp history"));
        button.setHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT,
            new ComponentBuilder("打开会话面板").color(ChatColor.YELLOW).create()));

        TextComponent suffix = new TextComponent("，或再次右键该村民。");
        suffix.setColor(ChatColor.GRAY);

        TextComponent message = new TextComponent();
        message.addExtra(prefix);
        message.addExtra(button);
        message.addExtra(suffix);
        player.spigot().sendMessage(message);
    }

    private static class ConversationViewContext {
        private final UUID villagerId;
        private final int page;
        private final int totalPages;

        ConversationViewContext(UUID villagerId, int page, int totalPages) {
            this.villagerId = villagerId;
            this.page = page;
            this.totalPages = totalPages;
        }

        UUID getVillagerId() {
            return villagerId;
        }

        int getPage() {
            return page;
        }

        int getTotalPages() {
            return totalPages;
        }
    }
}
