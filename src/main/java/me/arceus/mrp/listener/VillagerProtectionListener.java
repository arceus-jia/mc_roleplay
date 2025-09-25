package me.arceus.mrp.listener;

import me.arceus.mrp.MrpPlugin;
import me.arceus.mrp.villager.VillagerProfile;
import org.bukkit.attribute.Attribute;
import org.bukkit.entity.Villager;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.CreatureSpawnEvent;
import org.bukkit.event.entity.EntityDamageEvent;

/**
 * Ensures registered NPC villagers are protected from damage or accidental removal.
 */
public class VillagerProtectionListener implements Listener {

    private final MrpPlugin plugin;

    public VillagerProtectionListener(MrpPlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onVillagerDamage(EntityDamageEvent event) {
        if (!(event.getEntity() instanceof Villager villager)) {
            return;
        }
        VillagerProfile profile = plugin.getVillagerRegistry().getProfile(villager.getUniqueId());
        if (profile == null) {
            return;
        }
        event.setCancelled(true);
        plugin.applyNpcProtection(villager);
        var attribute = villager.getAttribute(Attribute.GENERIC_MAX_HEALTH);
        if (attribute != null) {
            villager.setHealth(attribute.getValue());
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onVillagerSpawn(CreatureSpawnEvent event) {
        if (!(event.getEntity() instanceof Villager villager)) {
            return;
        }
        if (plugin.getVillagerRegistry().getProfile(villager.getUniqueId()) != null) {
            plugin.applyNpcProtection(villager);
        }
    }

}
