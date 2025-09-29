package me.arceus.mrp.prompt;

import me.arceus.mrp.MrpPlugin;
import me.arceus.mrp.config.PromptSettings;
import me.arceus.mrp.conversation.ConversationSession;
import me.arceus.mrp.villager.VillagerProfile;
import me.arceus.mrp.villager.VillagerPromptOverride;

import java.util.List;
import java.util.Map;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.StringJoiner;

public class PromptService {

    private static final String FALLBACK_TEMPLATE = "你正在扮演{name}，正与玩家{user}对话。村民简介：{description}";

    private final MrpPlugin plugin;

    public PromptService(MrpPlugin plugin) {
        this.plugin = plugin;
    }

    public String buildSystemPrompt(VillagerProfile profile, ConversationSession session, String playerName) {
        PromptSettings settings = plugin.getConfigService().getPromptSettings();
        VillagerPromptOverride override = profile != null ? profile.getPromptOverride() : null;

        String template = null;
        if (override != null && override.hasTemplate()) {
            template = override.getSystemTemplate();
        }
        if (template == null || template.isBlank()) {
            template = settings != null ? settings.getSystemTemplate() : null;
        }
        if (template == null || template.isBlank()) {
            template = FALLBACK_TEMPLATE;
        }

        Map<String, String> replacements = buildReplacements(profile, session, playerName, override);

        String rendered = applyReplacements(template, replacements);

        List<String> resolvedNotes = new ArrayList<>();
        if (settings != null && (!settings.getExtraNotes().isEmpty())) {
            if (override == null || override.shouldInheritDefaultNotes()) {
                for (String note : settings.getExtraNotes()) {
                    resolvedNotes.add(applyReplacements(note, replacements));
                }
            }
        }
        if (override != null && override.hasCustomNotes()) {
            for (String note : override.getExtraNotes()) {
                resolvedNotes.add(applyReplacements(note, replacements));
            }
        }

        if (!resolvedNotes.isEmpty()) {
            StringJoiner joiner = new StringJoiner("\n");
            resolvedNotes.forEach(joiner::add);
            rendered = rendered + "\n注意事项:\n" + joiner;
        }
        return rendered;
    }

    public String renderTemplate(String template, VillagerProfile profile, ConversationSession session, String playerName) {
        if (template == null || template.isBlank()) {
            return "";
        }
        VillagerPromptOverride override = profile != null ? profile.getPromptOverride() : null;
        Map<String, String> replacements = buildReplacements(profile, session, playerName, override);
        return applyReplacements(template, replacements);
    }

    private Map<String, String> buildReplacements(VillagerProfile profile,
                                                  ConversationSession session,
                                                  String playerName,
                                                  VillagerPromptOverride override) {
        Map<String, String> replacements = new LinkedHashMap<>();

        String villagerName = profile != null && profile.getName() != null ? profile.getName() : "村民";
        String description = profile != null && profile.getDescription() != null ? profile.getDescription() : "这位村民还没有简介";
        String persona = profile != null && profile.getPersona() != null ? profile.getPersona() : "";
        String userName = playerName != null ? playerName : "玩家";

        replacements.put("name", villagerName);
        replacements.put("user", userName);
        replacements.put("description", description);
        replacements.put("persona", persona);

        if (override != null && override.hasVariables()) {
            replacements.putAll(override.getVariables());
        }
        if (session != null) {
            Map<String, String> promptVariables = session.getPromptVariables();
            if (!promptVariables.isEmpty()) {
                replacements.putAll(promptVariables);
            }
        }
        return replacements;
    }

    private String applyReplacements(String template, Map<String, String> replacements) {
        String result = template;
        for (Map.Entry<String, String> entry : replacements.entrySet()) {
            String key = entry.getKey();
            String value = entry.getValue() != null ? entry.getValue() : "";
            result = result.replace("{" + key + "}", value);
        }
        return result;
    }
}
