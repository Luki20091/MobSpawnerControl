package me.Luki.mobSpawnerControl;

import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.LivingEntity;
import org.bukkit.inventory.ItemStack;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.CreatureSpawnEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.metadata.FixedMetadataValue;

import java.util.Map;
import java.util.UUID;
import java.util.logging.Level;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;

public class SpawnerListener implements Listener {
    private final MobSpawnerControl plugin;
    private final Queue<UUID> pendingClean = new ConcurrentLinkedQueue<>();
    private volatile boolean running = true;

    public SpawnerListener(MobSpawnerControl plugin) {
        this.plugin = plugin;

        // single scheduled processor to clean equipment from recently-spawned entities
        plugin.getServer().getScheduler().runTaskTimer(plugin, () -> {
            int limit = plugin.getConfig().getInt("cleanup-per-tick", 100);
            int processed = 0;
            boolean debug = plugin.getConfig().getBoolean("debug", true);
            while (processed < limit) {
            if (!running) break;
            UUID id = pendingClean.poll();
            if (id == null) break;
            processed++;
            Entity ent = plugin.getServer().getEntity(id);
            if (!(ent instanceof LivingEntity living)) continue;
            if (!living.isValid() || living.isDead()) continue;
            // only process entities that were spawned from a spawner
            if (!living.hasMetadata("fromSpawner")) continue;
                try {
                    if (living.getEquipment() != null) {
                        ItemStack helmet = living.getEquipment().getHelmet();
                        ItemStack chest = living.getEquipment().getChestplate();
                        ItemStack legs = living.getEquipment().getLeggings();
                        ItemStack boots = living.getEquipment().getBoots();
                        ItemStack main = living.getEquipment().getItemInMainHand();
                        ItemStack off = living.getEquipment().getItemInOffHand();
                        // Treat skeleton bows/crossbows as non-equipment for clearing
                        if (living.getType() == EntityType.SKELETON) {
                            try {
                                if (main != null && (main.getType() == org.bukkit.Material.BOW || main.getType() == org.bukkit.Material.CROSSBOW)) main = null;
                                if (off != null && (off.getType() == org.bukkit.Material.BOW || off.getType() == org.bukkit.Material.CROSSBOW)) off = null;
                            } catch (Throwable ignored) {}
                        }
                        boolean nowHasEquipment = (helmet != null && helmet.getType() != org.bukkit.Material.AIR)
                                || (chest != null && chest.getType() != org.bukkit.Material.AIR)
                                || (legs != null && legs.getType() != org.bukkit.Material.AIR)
                                || (boots != null && boots.getType() != org.bukkit.Material.AIR)
                                || (main != null && main.getType() != org.bukkit.Material.AIR)
                                || (off != null && off.getType() != org.bukkit.Material.AIR);

                        if (nowHasEquipment) {
                            // clear equipment (but don't remove skeleton bows/crossbows)
                            living.getEquipment().setHelmet(null);
                            living.getEquipment().setChestplate(null);
                            living.getEquipment().setLeggings(null);
                            living.getEquipment().setBoots(null);
                            try {
                                ItemStack curMain = living.getEquipment().getItemInMainHand();
                                ItemStack curOff = living.getEquipment().getItemInOffHand();
                                if (!(living.getType() == EntityType.SKELETON && curMain != null && (curMain.getType() == org.bukkit.Material.BOW || curMain.getType() == org.bukkit.Material.CROSSBOW))) {
                                    living.getEquipment().setItemInMainHand(new ItemStack(org.bukkit.Material.AIR));
                                }
                                if (!(living.getType() == EntityType.SKELETON && curOff != null && (curOff.getType() == org.bukkit.Material.BOW || curOff.getType() == org.bukkit.Material.CROSSBOW))) {
                                    living.getEquipment().setItemInOffHand(new ItemStack(org.bukkit.Material.AIR));
                                }
                            } catch (Throwable ignored) {}
                            if (debug) plugin.getLogger().log(Level.FINE, "Cleared equipment from entity in queued processor.");
                        }
                    }
                    // handle requeue attempts (to catch other plugins equipping shortly after spawn)
                    int attempts = 0;
                    if (living.hasMetadata("cleanupAttempts")) {
                        try {
                            Object o = living.getMetadata("cleanupAttempts").get(0).value();
                            if (o instanceof Number) attempts = ((Number) o).intValue();
                        } catch (Throwable ignored) {}
                    }
                    if (attempts > 0) {
                        attempts--;
                        living.setMetadata("cleanupAttempts", new FixedMetadataValue(plugin, attempts));
                        if (attempts > 0) pendingClean.add(living.getUniqueId());
                    } else {
                        living.removeMetadata("cleanupAttempts", plugin);
                    }
                } catch (Throwable t) {
                    plugin.getLogger().log(Level.WARNING, "Error while processing pendingClean queue: ", t);
                }
            }
        }, 1L, 1L);

        // note: scheduled task will be cancelled automatically when plugin is disabled
    }

    public void shutdown() {
        running = false;
        pendingClean.clear();
    }
        // periodic world scan removed: cleanup is triggered only from spawn event (entities marked with fromSpawner)

    @EventHandler
    public void onCreatureSpawn(CreatureSpawnEvent event) {
        if (event.getSpawnReason() != CreatureSpawnEvent.SpawnReason.SPAWNER) return;

        Entity entity = event.getEntity();
        EntityType type = entity.getType();

        boolean debug = plugin.getConfig().getBoolean("debug", true);
        if (debug) plugin.getLogger().log(Level.INFO, "Spawner attempting to spawn: " + type);

        // detect if the spawned entity (if living) has any equipment (armor, held items)
        boolean hasEquipment = false;
        if (entity instanceof LivingEntity living) {
            ItemStack helmet = living.getEquipment() == null ? null : living.getEquipment().getHelmet();
            ItemStack chest = living.getEquipment() == null ? null : living.getEquipment().getChestplate();
            ItemStack legs = living.getEquipment() == null ? null : living.getEquipment().getLeggings();
            ItemStack boots = living.getEquipment() == null ? null : living.getEquipment().getBoots();
            ItemStack main = living.getEquipment() == null ? null : living.getEquipment().getItemInMainHand();
            ItemStack off = living.getEquipment() == null ? null : living.getEquipment().getItemInOffHand();
            // Allow skeletons to spawn with a bow/crossbow in hand (don't treat as equipment)
            if (type == EntityType.SKELETON) {
                try {
                    if (main != null && (main.getType() == org.bukkit.Material.BOW || main.getType() == org.bukkit.Material.CROSSBOW)) main = null;
                    if (off != null && (off.getType() == org.bukkit.Material.BOW || off.getType() == org.bukkit.Material.CROSSBOW)) off = null;
                } catch (Throwable ignored) {}
            }

            hasEquipment = (helmet != null && helmet.getType() != org.bukkit.Material.AIR)
                    || (chest != null && chest.getType() != org.bukkit.Material.AIR)
                    || (legs != null && legs.getType() != org.bukkit.Material.AIR)
                    || (boots != null && boots.getType() != org.bukkit.Material.AIR)
                    || (main != null && main.getType() != org.bukkit.Material.AIR)
                    || (off != null && off.getType() != org.bukkit.Material.AIR);
            if (debug) plugin.getLogger().log(Level.INFO, "Entity has equipment: " + hasEquipment);
        }

        // check per-mob config whether spawner should allow respawn when entity has equipment
        String path = "mobs." + type.name() + ".respawn-with-equipment";
        boolean allowRespawnWithEquip = plugin.getConfig().getBoolean(path, false);
        if (debug) plugin.getLogger().log(Level.INFO, "Config path " + path + " = " + allowRespawnWithEquip);

        if (hasEquipment && !allowRespawnWithEquip) {
            if (debug) plugin.getLogger().log(Level.INFO, "Spawner spawned " + type + " with equipment; clearing equipment as configured.");
            if (entity instanceof LivingEntity living1) {
                if (living1.getEquipment() != null) {
                    living1.getEquipment().setHelmet(null);
                    living1.getEquipment().setChestplate(null);
                    living1.getEquipment().setLeggings(null);
                    living1.getEquipment().setBoots(null);
                    living1.getEquipment().setItemInMainHand(new ItemStack(org.bukkit.Material.AIR));
                    living1.getEquipment().setItemInOffHand(new ItemStack(org.bukkit.Material.AIR));
                }
            }
        }

        // mark entity as spawned from spawner so we can control its drops later
        entity.setMetadata("fromSpawner", new FixedMetadataValue(plugin, true));
        // equipment may be applied a tick after spawn; enqueue for queued processor instead of scheduling
        if (!allowRespawnWithEquip && entity instanceof LivingEntity livingToQueue) {
            int attempts = plugin.getConfig().getInt("cleanup-attempts", 5);
            livingToQueue.setMetadata("cleanupAttempts", new FixedMetadataValue(plugin, attempts));
            pendingClean.add(livingToQueue.getUniqueId());
        }
        if (debug) plugin.getLogger().log(Level.INFO, "Marked entity " + type + " as fromSpawner.");
    }

    @EventHandler
    public void onEntityDeath(EntityDeathEvent event) {
        Entity entity = event.getEntity();
        EntityType type = entity.getType();

        boolean debug = plugin.getConfig().getBoolean("debug", true);
        if (debug) plugin.getLogger().log(Level.INFO, "Entity died: " + type);

        if (!entity.hasMetadata("fromSpawner")) {
            if (debug) plugin.getLogger().log(Level.INFO, "Entity " + type + " was not from a spawner; leaving drops untouched.");
            return;
        }

        // control drops per mob
        String mobBase = "mobs." + type.name();
        if (!plugin.getConfig().isConfigurationSection(mobBase)) {
            if (debug) plugin.getLogger().log(Level.INFO, "No config section for mob " + type + "; removing all drops by default.");
            event.getDrops().clear();
            return;
        }

        Map<String, Object> dropsSection = plugin.getConfig().getConfigurationSection(mobBase + ".drops").getValues(false);
        boolean allowAny = plugin.getConfig().getBoolean(mobBase + ".allow-any-drops", false);

        var drops = event.getDrops();
        if (dropsSection == null || dropsSection.isEmpty()) {
            if (debug) plugin.getLogger().log(Level.INFO, "No drop entries for " + type + "; allow-any-drops=" + allowAny);
            if (!allowAny) {
                drops.clear();
            }
            return;
        }

        // Iterate existing drops and remove those disabled in config
        drops.removeIf(itemStack -> {
            String matName = itemStack.getType().name();
            Object o = dropsSection.get(matName);
            boolean allow = false; // default to false unless explicitly enabled
            if (o instanceof Boolean) allow = (Boolean) o;
            if (debug) plugin.getLogger().log(Level.INFO, "Drop " + matName + " allowed? " + allow + " for mob " + type);
            return !allow;
        });
    }
}
