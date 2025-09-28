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
 * Presents a lightweight inventory-based conversation view and bridges chat
 * capture
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
    private final Map<UUID, ConversationDisplayMode> playerDisplayModes = new ConcurrentHashMap<>();

    public ConversationUiService(MrpPlugin plugin, ConversationChatService chatService) {
        this.plugin = plugin;
        this.chatService = chatService;
        this.sessionManager = plugin.getSessionManager();
        this.villagerRegistry = plugin.getVillagerRegistry();
    }

    public void openConversation(Player player, VillagerProfile profile) {
        openConversation(player, profile, -1);
    }

    public void openConversation(Player player, VillagerProfile profile, int page) {
        if (profile == null) {
            player.sendMessage("找不到该村民的资料");
            return;
        }
        UUID playerId = player.getUniqueId();
        ConversationSession session = sessionManager.getOrCreate(playerId, profile.getVillagerId());
        List<ConversationMessage> history = session.getMessages();
        ConversationDisplayMode mode = getEffectiveDisplayMode(player);
        if (mode == ConversationDisplayMode.BOOK) {
            openViews.remove(playerId);
            openBookConversation(player, profile, history, page);
        } else {
            int perPage = HISTORY_SLOTS.length;
            int totalMessages = history.size();
            int totalPages = totalMessages == 0 ? 1 : (int) Math.ceil(totalMessages / (double) perPage);
            int targetPage = page < 0 ? 0 : page;
            int clampedPage = Math.max(0, Math.min(targetPage, totalPages - 1));

            Inventory inventory = buildInventory(player, profile, history, clampedPage, totalPages);
            player.openInventory(inventory);
            openViews.put(playerId, new ConversationViewContext(profile.getVillagerId(), clampedPage, totalPages));
            sendQuickActions(player, profile, null, null);
        }
    }

    private Inventory buildInventory(Player player, VillagerProfile profile, List<ConversationMessage> history,
            int page, int totalPages) {
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
                        "输入 cancel 或 取消 退出")));

        inventory.setItem(RESTART_SLOT, createControlItem(
                Material.REDSTONE_TORCH,
                "重置对话",
                List.of("清空历史记录并重新开始")));

        inventory.setItem(END_SLOT, createControlItem(
                Material.BARRIER,
                "结束对话",
                List.of("结束会话并关闭界面")));

        fillPadding(inventory);
        return inventory;
    }

    private void openBookConversation(Player player, VillagerProfile profile, List<ConversationMessage> history,
            int page) {
        String villagerName = profile.getName() != null ? profile.getName() : "村民";
        List<BookPageContent> pageData = buildBookPages(history, player.getName(), villagerName);
        if (pageData.isEmpty()) {
            pageData = List.of(new BookPageContent(0, "暂无历史记录。\n\n请在聊天栏直接输入内容开始对话。"));
        }

        int totalPages = pageData.size();
        int targetChrono = page < 0 ? totalPages - 1 : Math.max(0, Math.min(page, totalPages - 1));
        boolean descending = page < 0 || targetChrono == totalPages - 1;
        List<String> renderedPages = renderBookPages(pageData, targetChrono, descending);

        ItemStack book = new ItemStack(Material.WRITTEN_BOOK);
        BookMeta meta = (BookMeta) book.getItemMeta();
        if (meta != null) {
            meta.setTitle("与 " + villagerName + " 对话");
            meta.setAuthor(villagerName);
            meta.setPages(renderedPages);
            book.setItemMeta(meta);
        }

        player.openBook(book);

        chatCaptureTargets.put(player.getUniqueId(), profile.getVillagerId());
        player.sendMessage(ChatColor.YELLOW + "请直接在聊天栏输入要对 " + villagerName + " 说的话（输入 cancel 或 取消 退出）。");
        sendQuickActions(player, profile, targetChrono, totalPages);
    }

    private List<BookPageContent> buildBookPages(List<ConversationMessage> history, String playerName, String villagerName) {
        // 书本实际可见行大约 14 行；我们预留 1 行给“页眉”，内容最多用 13 行
        final int maxLinesPerPage = 13; // 内容行
        // 书本原始字符上限 ~255，预留 ~40 字符给“页眉”（含颜色码与换行）
        final int maxRawCharsPerPage = 255 - 40;

        // （可选）行内折行宽度：从 24 收紧，默认 10 更靠近中文实际宽度
        final int wrapWidth = 10;

        BookPageBuilder builder = new BookPageBuilder(maxLinesPerPage, maxRawCharsPerPage);

        for (int idx = 0; idx < history.size(); idx++) {
            ConversationMessage msg = history.get(idx);
            boolean fromPlayer = msg.getRole() == ProviderMessage.Role.USER;
            String speaker = fromPlayer ? playerName : villagerName;
            String headerBase = ChatColor.GOLD + speaker + ChatColor.RESET;

            List<String> bodyLines = wrapForBook(msg.getContent(), wrapWidth);
            if (bodyLines.isEmpty())
                bodyLines = List.of("");

            int bodyIndex = 0;
            boolean continuation = false;
            while (bodyIndex < bodyLines.size()) {
                String header = headerBase + (continuation ? " (续)" : "") + ":";

                if (!builder.canFit(header))
                    builder.startNewPage();
                builder.addLine(header);

                while (bodyIndex < bodyLines.size()) {
                    String line = bodyLines.get(bodyIndex);
                    if (!builder.canFit(line)) {
                        builder.startNewPage();
                        continuation = true;
                        break;
                    }
                    builder.addLine(line);
                    bodyIndex++;
                    continuation = false;
                }
            }

            // 消息之间空一行
            if (idx < history.size() - 1 && !builder.isCurrentPageEmpty()) {
                if (!builder.canFit(""))
                    builder.startNewPage();
                if (!builder.isCurrentPageEmpty())
                    builder.addLine("");
            }
        }

        List<String> rawPages = builder.build();

        int total = rawPages.size();
        List<BookPageContent> result = new ArrayList<>(total);
        for (int i = 0; i < total; i++) {
            result.add(new BookPageContent(i, rawPages.get(i)));
        }

        plugin.getLogger().info("[BookMode] entries=" + history.size()
                + " pages=" + total
                + " lines=" + builder.getTotalLines()
                + " charsVisible=" + builder.getTotalVisibleChars()
                + " charsRaw=" + builder.getTotalRawChars());
        return result;
    }

    private List<String> renderBookPages(List<BookPageContent> pages, int targetChrono, boolean descendingFromTarget) {
        if (pages.isEmpty()) {
            return Collections.emptyList();
        }
        int total = pages.size();
        int clampedTarget = Math.max(0, Math.min(targetChrono, total - 1));

        List<BookPageContent> ordered = new ArrayList<>(total);
        if (descendingFromTarget) {
            for (int i = clampedTarget; i >= 0; i--) {
                ordered.add(pages.get(i));
            }
            for (int i = total - 1; i > clampedTarget; i--) {
                ordered.add(pages.get(i));
            }
        } else {
            for (int i = clampedTarget; i < total; i++) {
                ordered.add(pages.get(i));
            }
            for (int i = 0; i < clampedTarget; i++) {
                ordered.add(pages.get(i));
            }
        }

        List<String> rendered = new ArrayList<>(total);
        for (int displayIndex = 0; displayIndex < ordered.size(); displayIndex++) {
            BookPageContent page = ordered.get(displayIndex);
            String body = page.body;
            rendered.add(body == null || body.isEmpty() ? "" : body);
        }
        return rendered;
    }

    private List<String> wrapForBook(String content, int wrapWidth) {
        List<String> lines = new ArrayList<>();
        if (content == null || content.isEmpty())
            return lines;

        String[] baseLines = content.replace('\r', ' ').split("\n");
        for (String base : baseLines) {
            String working = base;
            if (working.isEmpty()) {
                lines.add("");
                continue;
            }
            while (visibleLength(working) > wrapWidth) {
                int split = findSplitIndex(working, wrapWidth);
                lines.add(working.substring(0, split));
                working = working.substring(split);
            }
            lines.add(working);
        }
        return lines;
    }

    private static int findSplitIndex(String text, int maxVisible) {
        int count = 0;
        int index = 0;
        while (index < text.length() && count < maxVisible) {
            char c = text.charAt(index);
            if (c == ChatColor.COLOR_CHAR && index + 1 < text.length()) {
                index += 2;
                continue;
            }
            count++;
            index++;
        }
        return Math.max(1, index);
    }

    private static int visibleLength(String text) {
        if (text == null || text.isEmpty()) {
            return 0;
        }
        int count = 0;
        boolean skip = false;
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (skip) {
                skip = false;
                continue;
            }
            if (c == ChatColor.COLOR_CHAR) {
                skip = true;
                continue;
            }
            count++;
        }
        return count;
    }

    private static class BookPageBuilder {
        private final int maxLines; // 内容最大行数（已排除页眉）
        private final int maxRawChars; // 每页原始字符上限（已预留页眉）
        private final List<String> pages = new ArrayList<>();
        private final List<String> currentLines = new ArrayList<>();
        private int currentRawChars = 0; // 按原始字符统计（含§颜色码与换行）
        private int totalLines = 0;
        private int totalVisibleChars = 0;
        private int totalRawChars = 0;

        BookPageBuilder(int maxLines, int maxRawChars) {
            this.maxLines = maxLines;
            this.maxRawChars = maxRawChars;
        }

        boolean canFit(String line) {
            int rawLen = (line == null ? 0 : line.length());
            int newlineCost = currentLines.isEmpty() ? 0 : 1; // '\n'
            if (currentLines.size() >= maxLines)
                return false;
            return currentRawChars + newlineCost + rawLen <= maxRawChars;
        }

        void addLine(String line) {
            String safe = (line == null ? "" : line);
            int rawLen = safe.length();
            int visLen = visibleLength(safe);

            if (!currentLines.isEmpty()) {
                currentRawChars += 1; // '\n'
                totalRawChars += 1;
            }
            currentLines.add(safe);

            currentRawChars += rawLen;
            totalRawChars += rawLen;

            totalVisibleChars += visLen;
            totalLines += 1;
        }

        boolean isCurrentPageEmpty() {
            return currentLines.isEmpty();
        }

        void startNewPage() {
            if (currentLines.isEmpty())
                return;
            pages.add(String.join("\n", currentLines));
            currentLines.clear();
            currentRawChars = 0;
        }

        List<String> build() {
            startNewPage();
            return pages.isEmpty() ? Collections.emptyList() : new ArrayList<>(pages);
        }

        int getTotalLines() {
            return totalLines;
        }

        int getTotalVisibleChars() {
            return totalVisibleChars;
        }

        int getTotalRawChars() {
            return totalRawChars;
        }
    }

    private ItemStack createMessageItem(ConversationMessage message, boolean fromPlayer, String playerName,
            String villagerName) {
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
            if (slot == PREV_SLOT || slot == RESTART_SLOT || slot == TALK_SLOT || slot == END_SLOT
                    || slot == NEXT_SLOT) {
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
        playerDisplayModes.remove(playerId);
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
        Integer page = null;
        Integer totalPages = null;
        ConversationDisplayMode mode = getEffectiveDisplayMode(player);
        if (mode == ConversationDisplayMode.BOOK) {
            ConversationSession session = sessionManager.getOrCreate(player.getUniqueId(), profile.getVillagerId());
            List<BookPageContent> pageData = buildBookPages(session.getMessages(), player.getName(), name);
            totalPages = Math.max(pageData.size(), 1);
            page = totalPages - 1;
        }
        sendQuickActions(player, profile, page, totalPages);
    }

    private void sendQuickActions(Player player, VillagerProfile profile, Integer page, Integer totalPages) {
        ConversationDisplayMode mode = getEffectiveDisplayMode(player);
        TextComponent line = new TextComponent();
        TextComponent prefix = new TextComponent(mode == ConversationDisplayMode.BOOK
            ? "快捷操作："
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

        line.addExtra(space());
        ConversationDisplayMode next = mode == ConversationDisplayMode.BOOK
            ? ConversationDisplayMode.INVENTORY
            : ConversationDisplayMode.BOOK;
        TextComponent toggleBtn = new TextComponent(mode == ConversationDisplayMode.BOOK ? "[切换到面板]" : "[切换到书本]");
        toggleBtn.setColor(ChatColor.AQUA);
        toggleBtn.setClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND,
            "/mrp view " + next.name().toLowerCase()));
        toggleBtn.setHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT,
            new ComponentBuilder("切换为 " + (next == ConversationDisplayMode.BOOK ? "书本模式" : "物品栏模式"))
                .color(ChatColor.YELLOW).create()));
        line.addExtra(toggleBtn);

        player.spigot().sendMessage(line);
    }

    private TextComponent space() {
        return new TextComponent(" ");
    }

    public ConversationDisplayMode setPlayerDisplayMode(Player player, ConversationDisplayMode mode) {
        UUID playerId = player.getUniqueId();
        if (mode == null) {
            playerDisplayModes.remove(playerId);
            return getConfiguredDisplayMode();
        }
        playerDisplayModes.put(playerId, mode);
        return mode;
    }

    public ConversationDisplayMode togglePlayerDisplayMode(Player player) {
        ConversationDisplayMode current = getEffectiveDisplayMode(player);
        ConversationDisplayMode next = current == ConversationDisplayMode.BOOK
            ? ConversationDisplayMode.INVENTORY
            : ConversationDisplayMode.BOOK;
        playerDisplayModes.put(player.getUniqueId(), next);
        return next;
    }

    public ConversationDisplayMode getEffectiveDisplayMode(Player player) {
        return getEffectiveDisplayMode(player.getUniqueId());
    }

    public ConversationDisplayMode getEffectiveDisplayMode(UUID playerId) {
        return playerDisplayModes.getOrDefault(playerId, getConfiguredDisplayMode());
    }

    private ConversationDisplayMode getConfiguredDisplayMode() {
        ConversationSettings settings = plugin.getConfigService().getConversationSettings();
        return settings != null ? settings.getDisplayMode() : ConversationDisplayMode.INVENTORY;
    }

    private static class BookPageContent {
        private final int chronoIndex;
        private final String body;

        BookPageContent(int chronoIndex, String body) {
            this.chronoIndex = chronoIndex;
            this.body = body;
        }
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
