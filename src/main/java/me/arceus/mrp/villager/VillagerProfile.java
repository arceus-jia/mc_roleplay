package me.arceus.mrp.villager;

import java.util.UUID;

public class VillagerProfile {

    private UUID villagerId;
    private int characterId;
    private String name;
    private String description;
    private String persona;
    private String modelOverride;
    private boolean freezeAi;
    private VillagerPromptOverride promptOverride;

    // Gson 需要无参构造函数
    public VillagerProfile() {
    }

    public VillagerProfile(UUID villagerId,
                           int characterId,
                           String name,
                           String description,
                           String persona,
                           String modelOverride,
                           VillagerPromptOverride promptOverride) {
        this.villagerId = villagerId;
        this.characterId = characterId;
        this.name = name;
        this.description = description;
        this.persona = persona;
        this.modelOverride = modelOverride;
        this.promptOverride = promptOverride;
    }

    public UUID getVillagerId() {
        return villagerId;
    }

    public int getCharacterId() {
        return characterId;
    }

    public String getName() {
        return name;
    }

    public String getDescription() {
        return description;
    }

    public String getPersona() {
        return persona;
    }

    public String getModelOverride() {
        return modelOverride;
    }

    public boolean isFreezeAi() {
        return freezeAi;
    }

    public VillagerPromptOverride getPromptOverride() {
        return promptOverride;
    }

    public void setVillagerId(UUID villagerId) {
        this.villagerId = villagerId;
    }

    public void setCharacterId(int characterId) {
        this.characterId = characterId;
    }

    public void setName(String name) {
        this.name = name;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public void setPersona(String persona) {
        this.persona = persona;
    }

    public void setModelOverride(String modelOverride) {
        this.modelOverride = modelOverride;
    }

    public void setFreezeAi(boolean freezeAi) {
        this.freezeAi = freezeAi;
    }

    public void setPromptOverride(VillagerPromptOverride promptOverride) {
        this.promptOverride = promptOverride;
    }
}
