package me.arceus.mrp.ui;

import me.arceus.mrp.MrpPlugin;
import me.arceus.mrp.conversation.ConversationChatService;
import me.arceus.mrp.conversation.ConversationMessage;
import me.arceus.mrp.conversation.ConversationSession;
import me.arceus.mrp.conversation.ConversationSessionManager;
import me.arceus.mrp.config.ConversationDisplayMode;
import me.arceus.mrp.config.ConversationSettings;
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
import org.bukkit.inventory.meta.BookMeta;

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

    public void openConversation(Player player, VillagerProfile profile, int page) {
        if (profile == null) {
            player.sendMessage("找不到该村民的资料");
            return;
        }
        UUID playerId = player.getUniqueId();
        ConversationSession session = sessionManager.getOrCreate(playerId, profile.getVillagerId());
        List<ConversationMessage> history = session.getMessages();
        ConversationDisplayMode mode = getDisplayMode();
        if (mode == ConversationDisplayMode.BOOK) {
            openViews.remove(playerId);
            openBookConversation(player, profile, history, page);
        } else {
            int perPage = HISTORY_SLOTS.length;
            int totalMessages = history.size();
            int totalPages = totalMessages == 0 ? 1 : (int) Math.ceil(totalMessages / (double) perPage);
            int clampedPage = Math.max(0, Math.min(page, totalPages - 1));

            Inventory inventory = buildInventory(player, profile, history, clampedPage, totalPages);
            player.openInventory(inventory);
            openViews.put(playerId, new ConversationViewContext(profile.getVillagerId(), clampedPage, totalPages));
            sendQuickActions(player, profile, null, null);
        }
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

    private void openBookConversation(Player player, VillagerProfile profile, List<ConversationMessage> history, int page) {
        String villagerName = profile.getName() != null ? profile.getName() : "村民";
        List<String> pages = buildBookPages(history, player.getName(), villagerName);
        if (pages.isEmpty()) {
            pages = List.of("暂无历史记录。\n\n请在聊天栏直接输入内容开始对话。");
        }

        int totalPages = pages.size();
        int clampedPage = Math.max(0, Math.min(page, totalPages - 1));

        ItemStack book = new ItemStack(Material.WRITTEN_BOOK);
        BookMeta meta = (BookMeta) book.getItemMeta();
        if (meta != null) {
            meta.setTitle("与 " + villagerName + " 对话");
            meta.setAuthor(villagerName);
            meta.setPages(pages);
            book.setItemMeta(meta);
        }

        openBookAtPage(player, book, clampedPage);

        chatCaptureTargets.put(player.getUniqueId(), profile.getVillagerId());
        player.sendMessage(ChatColor.YELLOW + "请直接在聊天栏输入要对 " + villagerName + " 说的话（输入 cancel 或 取消 退出）。");
        sendQuickActions(player, profile, clampedPage, totalPages);
    }

    private void openBookAtPage(Player player, ItemStack originalBook, int page) {
        if (!(originalBook.getItemMeta() instanceof BookMeta meta)) {
            player.openBook(originalBook);
            return;
        }
        List<String> pages = new ArrayList<>(meta.getPages());
        if (pages.isEmpty()) {
            player.openBook(originalBook);
            return;
        }
        int target = Math.max(0, Math.min(page, pages.size() - 1));
        if (target > 0) {
            Collections.rotate(pages, -target);
        }
        BookMeta viewMeta = (BookMeta) meta.clone();
        viewMeta.setPages(pages);
        ItemStack view = new ItemStack(Material.WRITTEN_BOOK);
        view.setItemMeta(viewMeta);
        player.openBook(view);
    }

    private List<String> buildBookPages(List<ConversationMessage> history, String playerName, String villagerName) {
        final int maxLines = 12;
        final int maxChars = 240;

        List<String> pages = new ArrayList<>();
        StringBuilder builder = new StringBuilder();
        int lineCount = 0;

        for (int idx = history.size() - 1; idx >= 0; idx--) {
            ConversationMessage message = history.get(idx);
            boolean fromPlayer = message.getRole() == ProviderMessage.Role.USER;
            String speaker = fromPlayer ? playerName : villagerName;
            String content = message.getContent() != null ? message.getContent() : "";

            List<String> entryLines = new ArrayList<>();
            entryLines.add(ChatColor.GOLD + speaker + ChatColor.RESET + ":");
            entryLines.addAll(wrapForBook(content));
            entryLines.add("");

            for (String line : entryLines) {
                if (lineCount >= maxLines || builder.length() + line.length() > maxChars) {
                    pages.add(trimTrailingNewline(builder));
                    builder = new StringBuilder();
                    lineCount = 0;
                }
                builder.append(line);
                builder.append('\n');
                lineCount++;
            }
        }

        if (builder.length() > 0) {
            pages.add(trimTrailingNewline(builder));
        }

        int total = pages.size();
        List<String> withHeaders = new ArrayList<>(total);
        for (int i = 0; i < total; i++) {
            String header = ChatColor.DARK_AQUA + "第 " + (i + 1) + "/" + total + " 页" + (i == 0 ? " · 最新" : "") + ChatColor.RESET + "\n";
            withHeaders.add(header + pages.get(i));
        }
        return withHeaders;
    }

    private List<String> wrapForBook(String content) {
        List<String> result = new ArrayList<>();
        if (content == null || content.isEmpty()) {
            result.add("");
            return result;
        }
        String[] baseLines = content.replace('\r', ' ').split("\n");
        for (String base : baseLines) {
            String line = base.trim();
            if (line.isEmpty()) {
                result.add("");
                continue;
            }
            while (line.length() > 18) {
                result.add(line.substring(0, 18));
                line = line.substring(18);
            }
            result.add(line);
        }
        return result;
    }

    private String trimTrailingNewline(StringBuilder builder) {
        if (builder.length() == 0) {
            return "";
        }
        int len = builder.length();
        if (builder.charAt(len - 1) == '\n') {
            builder.deleteCharAt(len - 1);
        }
        String content = builder.toString();
        builder.setLength(0);
        return content;
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
        if (getDisplayMode() != ConversationDisplayMode.INVENTORY) {
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
        if (getDisplayMode() != ConversationDisplayMode.INVENTORY) {
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

        Integer page = null;
        Integer totalPages = null;
        if (getDisplayMode() == ConversationDisplayMode.BOOK) {
            ConversationSession session = sessionManager.getOrCreate(player.getUniqueId(), profile.getVillagerId());
            List<String> pages = buildBookPages(session.getMessages(), player.getName(), name);
            totalPages = Math.max(pages.size(), 1);
            page = 0;
        }
        sendQuickActions(player, profile, page, totalPages);
    }

    private ConversationDisplayMode getDisplayMode() {
        ConversationSettings settings = plugin.getConfigService().getConversationSettings();
        return settings != null ? settings.getDisplayMode() : ConversationDisplayMode.INVENTORY;
    }

    private void sendQuickActions(Player player, VillagerProfile profile, Integer page, Integer totalPages) {
        ConversationDisplayMode mode = getDisplayMode();
        TextComponent line = new TextComponent();
        TextComponent prefix = new TextComponent(mode == ConversationDisplayMode.BOOK
            ? "快捷操作（书本可直接翻页）："
            : "快捷操作：");
        prefix.setColor(ChatColor.GRAY);
        line.addExtra(prefix);

        TextComponent historyBtn = new TextComponent("[查看历史]");
        historyBtn.setColor(ChatColor.GOLD);
        String historyCommand = "/mrp history";
        if (page != null) {
            historyCommand += " " + (page + 1);
        }
        historyBtn.setClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, historyCommand));
        historyBtn.setHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT,
            new ComponentBuilder(mode == ConversationDisplayMode.BOOK ? "刷新书本页面" : "打开会话面板")
                .color(ChatColor.YELLOW).create()));
        line.addExtra(historyBtn);
        line.addExtra(space());

        TextComponent endBtn = new TextComponent("[结束对话]");
        endBtn.setColor(ChatColor.RED);
        endBtn.setClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, "/mrp end"));
        endBtn.setHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT,
            new ComponentBuilder("结束当前对话").color(ChatColor.YELLOW).create()));
        line.addExtra(endBtn);
        line.addExtra(space());

        String clearTarget = profile.getCharacterId() > 0
            ? String.valueOf(profile.getCharacterId())
            : profile.getVillagerId().toString();

        TextComponent resetBtn = new TextComponent("[重置对话]");
        resetBtn.setColor(ChatColor.GOLD);
        resetBtn.setClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND,
            "/mrp character clear " + clearTarget));
        resetBtn.setHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT,
            new ComponentBuilder("清空与你的历史记录").color(ChatColor.YELLOW).create()));
        line.addExtra(resetBtn);

        if (mode == ConversationDisplayMode.BOOK && totalPages != null) {
            line.addExtra(space());
            TextComponent hint = new TextComponent("(页数 " + (page != null ? page + 1 : 1) + "/" + totalPages + ")");
            hint.setColor(ChatColor.GRAY);
            line.addExtra(hint);
        }

        player.spigot().sendMessage(line);
    }

    private TextComponent space() {
        return new TextComponent(" ");
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
