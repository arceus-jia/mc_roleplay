package me.arceus.mrp.prompt;

import me.arceus.mrp.MrpPlugin;
import me.arceus.mrp.config.PromptSettings;
import me.arceus.mrp.villager.VillagerProfile;

import java.util.List;
import java.util.StringJoiner;

public class PromptService {

    private static final String FALLBACK_TEMPLATE = "你正在扮演{name}，正与玩家{user}对话。村民简介：{description}";

    private final MrpPlugin plugin;

    public PromptService(MrpPlugin plugin) {
        this.plugin = plugin;
    }

    public String buildSystemPrompt(VillagerProfile profile, String playerName) {
        PromptSettings settings = plugin.getConfigService().getPromptSettings();
        String template = settings != null ? settings.getSystemTemplate() : null;
        if (template == null || template.isBlank()) {
            template = FALLBACK_TEMPLATE;
        }

        String villagerName = profile != null && profile.getName() != null ? profile.getName() : "村民";
        String description = profile != null && profile.getDescription() != null ? profile.getDescription() : "这位村民还没有简介";
        String persona = profile != null && profile.getPersona() != null ? profile.getPersona() : "";

        String rendered = template
            .replace("{name}", villagerName)
            .replace("{user}", playerName != null ? playerName : "玩家")
            .replace("{description}", description)
            .replace("{persona}", persona);

        List<String> extraNotes = settings != null ? settings.getExtraNotes() : List.of();
        if (!extraNotes.isEmpty()) {
            StringJoiner joiner = new StringJoiner("\n");
            extraNotes.forEach(joiner::add);
            rendered = rendered + "\n注意事项:\n" + joiner;
        }
        return rendered;
    }
}
