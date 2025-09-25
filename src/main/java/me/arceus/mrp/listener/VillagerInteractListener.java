package me.arceus.mrp.listener;

import me.arceus.mrp.MrpPlugin;
import me.arceus.mrp.villager.VillagerProfile;
import me.arceus.mrp.villager.VillagerRegistry;
import org.bukkit.entity.Player;
import org.bukkit.entity.Villager;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerInteractEntityEvent;

public class VillagerInteractListener implements Listener {

    private final MrpPlugin plugin;

    public VillagerInteractListener(MrpPlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onPlayerInteract(PlayerInteractEntityEvent event) {
        if (!(event.getRightClicked() instanceof Villager villager)) {
            return;
        }
        Player player = event.getPlayer();

        VillagerRegistry registry = plugin.getVillagerRegistry();
        VillagerProfile profile = registry.getProfile(villager.getUniqueId());
        if (profile == null) {
            return;
        }

        event.setCancelled(true);

        plugin.getConversationUiService().openConversation(player, profile);
    }
}
