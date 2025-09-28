package me.arceus.mrp.villager;

import java.util.Collections;
import java.util.List;

/**
 * Describes how a villager responds when a special task is completed
 * (for example, the射覆角色判定玩家猜中目标)。
 */
public class VillagerSuccessBehavior {

    private String message;
    private Boolean resetConversation;
    private List<VillagerRewardOption> rewardPool;

    // Gson requires a no-args constructor
    public VillagerSuccessBehavior() {
    }

    public String getMessage() {
        return message;
    }

    public boolean shouldResetConversation() {
        return resetConversation == null || resetConversation;
    }

    public List<VillagerRewardOption> getRewardPool() {
        return rewardPool != null ? rewardPool : Collections.emptyList();
    }

    public boolean hasRewards() {
        return rewardPool != null && !rewardPool.isEmpty();
    }
}
