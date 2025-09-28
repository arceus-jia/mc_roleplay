package me.arceus.mrp.villager;

import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * Holds optional per-villager prompt customisations loaded from JSON.
 */
public class VillagerPromptOverride {

    private String systemTemplate;
    private List<String> extraNotes;
    private Map<String, String> variables;
    private Boolean inheritDefaultNotes;
    private Map<String, List<String>> variableCandidates;
    private VillagerSuccessBehavior success;

    // Gson requires a no-args constructor
    public VillagerPromptOverride() {
    }

    public String getSystemTemplate() {
        return systemTemplate;
    }

    public boolean hasTemplate() {
        return systemTemplate != null && !systemTemplate.isBlank();
    }

    public List<String> getExtraNotes() {
        return extraNotes != null ? extraNotes : Collections.emptyList();
    }

    public boolean hasCustomNotes() {
        return extraNotes != null && !extraNotes.isEmpty();
    }

    public Map<String, String> getVariables() {
        return variables != null ? variables : Collections.emptyMap();
    }

    public boolean hasVariables() {
        return variables != null && !variables.isEmpty();
    }

    public boolean shouldInheritDefaultNotes() {
        return inheritDefaultNotes == null || inheritDefaultNotes;
    }

    public Map<String, List<String>> getVariableCandidates() {
        return variableCandidates != null ? variableCandidates : Collections.emptyMap();
    }

    public boolean hasVariableCandidates() {
        return variableCandidates != null && !variableCandidates.isEmpty();
    }

    public VillagerSuccessBehavior getSuccess() {
        return success;
    }
}
