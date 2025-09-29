package me.arceus.mrp.villager;

import java.util.Collections;
import java.util.List;

/**
 * Describes how a villager responds when a special task is completed
 * (for example, the射覆角色判定玩家猜中目标)。
 */
public class VillagerSuccessBehavior {

    private static final List<String> DEFAULT_TRIGGERS = List.of("SUCCESS");

    private String message;
    private Boolean resetConversation;
    private List<VillagerRewardOption> rewardPool;
    private List<String> triggers;
    private Boolean continueConversation;

    // Gson requires a no-args constructor
    public VillagerSuccessBehavior() {
    }

    public String getMessage() {
        return message;
    }

    public boolean shouldResetConversation() {
        if (continueConversation != null) {
            return !continueConversation;
        }
        return resetConversation == null || resetConversation;
    }

    public List<VillagerRewardOption> getRewardPool() {
        return rewardPool != null ? rewardPool : Collections.emptyList();
    }

    public boolean hasRewards() {
        return rewardPool != null && !rewardPool.isEmpty();
    }

    public List<String> getTriggers() {
        if (triggers == null || triggers.isEmpty()) {
            return DEFAULT_TRIGGERS;
        }
        return triggers;
    }

    public Boolean getContinueConversation() {
        return continueConversation;
    }
}
