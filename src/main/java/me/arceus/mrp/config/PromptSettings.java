package me.arceus.mrp.config;

import java.util.Collections;
import java.util.List;

public class PromptSettings {

    private final String systemTemplate;
    private final List<String> extraNotes;

    public PromptSettings(String systemTemplate, List<String> extraNotes) {
        this.systemTemplate = systemTemplate;
        this.extraNotes = extraNotes;
    }

    public String getSystemTemplate() {
        return systemTemplate;
    }

    public List<String> getExtraNotes() {
        return Collections.unmodifiableList(extraNotes);
    }
}
