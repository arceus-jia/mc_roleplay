package me.arceus.mrp.villager;

import java.util.Collections;
import java.util.List;

/**
 * Represents one reward candidate that can be granted when a villager task succeeds.
 */
public class VillagerRewardOption {

    private String name;
    private List<String> commands;
    private List<String> messages;
    private double weight;

    // Gson requires a no-args constructor
    public VillagerRewardOption() {
    }

    public String getName() {
        return name;
    }

    public List<String> getCommands() {
        return commands != null ? commands : Collections.emptyList();
    }

    public List<String> getMessages() {
        return messages != null ? messages : Collections.emptyList();
    }

    public double getWeight() {
        return weight > 0 ? weight : 1.0D;
    }
}
