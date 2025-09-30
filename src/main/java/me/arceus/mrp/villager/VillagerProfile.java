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
    private String greeting;
    private String introduction;
    private String providerOverride;

    // Gson 需要无参构造函数
    public VillagerProfile() {
    }

    public VillagerProfile(UUID villagerId,
                           int characterId,
                           String name,
                           String description,
                           String persona,
                           String modelOverride,
                           VillagerPromptOverride promptOverride,
                           String greeting,
                           String introduction,
                           String providerOverride) {
        this.villagerId = villagerId;
        this.characterId = characterId;
        this.name = name;
        this.description = description;
        this.persona = persona;
        this.modelOverride = modelOverride;
        this.promptOverride = promptOverride;
        this.greeting = greeting;
        this.introduction = introduction;
        this.providerOverride = providerOverride;
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

    public String getGreeting() {
        return greeting;
    }

    public String getIntroduction() {
        return introduction;
    }

    public boolean hasGreeting() {
        return greeting != null && !greeting.isBlank();
    }

    public boolean hasIntroduction() {
        return introduction != null && !introduction.isBlank();
    }

    public String getProviderOverride() {
        return providerOverride;
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

    public void setGreeting(String greeting) {
        this.greeting = greeting;
    }

    public void setIntroduction(String introduction) {
        this.introduction = introduction;
    }

    public void setProviderOverride(String providerOverride) {
        this.providerOverride = providerOverride;
    }
}
