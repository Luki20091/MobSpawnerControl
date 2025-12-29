package me.Luki.mobSpawnerControl;

import org.bukkit.NamespacedKey;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.LivingEntity;
import org.bukkit.inventory.ItemStack;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.CreatureSpawnEvent;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;

import java.util.UUID;
import java.util.logging.Level;

public class SpawnerListener implements Listener {
    private final MobSpawnerControl plugin;
    private final NamespacedKey spawnerKey;
    private final NamespacedKey attemptsKey;

    public SpawnerListener(MobSpawnerControl plugin) {
        this.plugin = plugin;
        this.spawnerKey = new NamespacedKey(plugin, "fromSpawner");
        this.attemptsKey = new NamespacedKey(plugin, "cleanupAttempts");
    }

    private void clearAllEquipment(LivingEntity ent) {
        if (ent.getEquipment() == null) return;
        try { ent.getEquipment().setHelmet(null); } catch (Throwable ignored) {}
        try { ent.getEquipment().setChestplate(null); } catch (Throwable ignored) {}
        try { ent.getEquipment().setLeggings(null); } catch (Throwable ignored) {}
        try { ent.getEquipment().setBoots(null); } catch (Throwable ignored) {}
        try { ent.getEquipment().setItemInMainHand(new ItemStack(org.bukkit.Material.AIR)); } catch (Throwable ignored) {}
        try { ent.getEquipment().setItemInOffHand(new ItemStack(org.bukkit.Material.AIR)); } catch (Throwable ignored) {}

        try { ent.getEquipment().setHelmetDropChance(0f); } catch (Throwable ignored) {}
        try { ent.getEquipment().setChestplateDropChance(0f); } catch (Throwable ignored) {}
        try { ent.getEquipment().setLeggingsDropChance(0f); } catch (Throwable ignored) {}
        try { ent.getEquipment().setBootsDropChance(0f); } catch (Throwable ignored) {}
        try { ent.getEquipment().setItemInMainHandDropChance(0f); } catch (Throwable ignored) {}
        try { ent.getEquipment().setItemInOffHandDropChance(0f); } catch (Throwable ignored) {}
    }

    private void scheduleCleanupFor(LivingEntity living, long delayTicks) {
        UUID id = living.getUniqueId();
        plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
            try {
                Entity e = plugin.getServer().getEntity(id);
                if (!(e instanceof LivingEntity ent)) return;
                PersistentDataContainer pdc = ent.getPersistentDataContainer();
                if (!pdc.has(spawnerKey, PersistentDataType.BYTE)) return;

                boolean hadEquipment = false;
                try {
                    if (ent.getEquipment() != null) {
                        ItemStack h = ent.getEquipment().getHelmet();
                        ItemStack c = ent.getEquipment().getChestplate();
                        ItemStack l = ent.getEquipment().getLeggings();
                        ItemStack b = ent.getEquipment().getBoots();
                        ItemStack m = ent.getEquipment().getItemInMainHand();
                        ItemStack o = ent.getEquipment().getItemInOffHand();
                        hadEquipment = (h != null && h.getType() != org.bukkit.Material.AIR)
                                || (c != null && c.getType() != org.bukkit.Material.AIR)
                                || (l != null && l.getType() != org.bukkit.Material.AIR)
                                || (b != null && b.getType() != org.bukkit.Material.AIR)
                                || (m != null && m.getType() != org.bukkit.Material.AIR)
                                || (o != null && o.getType() != org.bukkit.Material.AIR);
                    }
                } catch (Throwable ignored) {}

                if (hadEquipment) {
                    try { clearAllEquipment(ent); } catch (Throwable ignored) {}
                    boolean debug = plugin.getConfig().getBoolean("debug", false);
                    if (debug) plugin.getLogger().log(Level.FINE, "Cleared equipment from entity in scheduled processor.");
                }

                int attempts = 0;
                try {
                    Integer v = pdc.get(attemptsKey, PersistentDataType.INTEGER);
                    if (v != null) attempts = v.intValue();
                } catch (Throwable ignored) {}

                if (attempts > 0) {
                    attempts--;
                    pdc.set(attemptsKey, PersistentDataType.INTEGER, attempts);
                    if (attempts > 0) scheduleCleanupFor(ent, 1L);
                } else {
                    pdc.remove(attemptsKey);
                }
            } catch (Throwable t) {
                plugin.getLogger().log(Level.WARNING, "Error in scheduled cleanup task: ", t);
            }
        }, delayTicks);
    }

    @EventHandler
    public void onCreatureSpawn(CreatureSpawnEvent event) {
        if (event.getSpawnReason() != CreatureSpawnEvent.SpawnReason.SPAWNER) return;

        Entity entity = event.getEntity();
        EntityType type = entity.getType();

        boolean debug = plugin.getConfig().getBoolean("debug", false);
        if (debug) plugin.getLogger().log(Level.INFO, "Spawner attempting to spawn: " + type);

        boolean hasEquipment = false;
        if (entity instanceof LivingEntity living) {
            try {
                ItemStack helmet = living.getEquipment() == null ? null : living.getEquipment().getHelmet();
                ItemStack chest = living.getEquipment() == null ? null : living.getEquipment().getChestplate();
                ItemStack legs = living.getEquipment() == null ? null : living.getEquipment().getLeggings();
                ItemStack boots = living.getEquipment() == null ? null : living.getEquipment().getBoots();
                ItemStack main = living.getEquipment() == null ? null : living.getEquipment().getItemInMainHand();
                ItemStack off = living.getEquipment() == null ? null : living.getEquipment().getItemInOffHand();
                hasEquipment = (helmet != null && helmet.getType() != org.bukkit.Material.AIR)
                        || (chest != null && chest.getType() != org.bukkit.Material.AIR)
                        || (legs != null && legs.getType() != org.bukkit.Material.AIR)
                        || (boots != null && boots.getType() != org.bukkit.Material.AIR)
                        || (main != null && main.getType() != org.bukkit.Material.AIR)
                        || (off != null && off.getType() != org.bukkit.Material.AIR);
            } catch (Throwable ignored) {}
            if (debug) plugin.getLogger().log(Level.INFO, "Entity has equipment: " + hasEquipment);
        }

        String path = "mobs." + type.name() + ".respawn-with-equipment";
        boolean allowRespawnWithEquip = plugin.getConfig().getBoolean(path, false);
        if (debug) plugin.getLogger().log(Level.INFO, "Config path " + path + " = " + allowRespawnWithEquip);

        if (hasEquipment && !allowRespawnWithEquip) {
            if (debug) plugin.getLogger().log(Level.INFO, "Spawner spawned " + type + " with equipment; clearing equipment as configured.");
            if (entity instanceof LivingEntity living1) {
                try { clearAllEquipment(living1); } catch (Throwable ignored) {}
            }
        }

        if (entity instanceof LivingEntity livingMark) {
            PersistentDataContainer pdc = livingMark.getPersistentDataContainer();
            pdc.set(spawnerKey, PersistentDataType.BYTE, (byte) 1);
            if (!allowRespawnWithEquip) {
                int attempts = plugin.getConfig().getInt("cleanup-attempts", 5);
                pdc.set(attemptsKey, PersistentDataType.INTEGER, attempts);
                scheduleCleanupFor(livingMark, plugin.getConfig().getInt("cleanup-delay-ticks", 1));
            }
        }
        if (debug) plugin.getLogger().log(Level.INFO, "Marked entity " + type + " as fromSpawner.");
    }
}

