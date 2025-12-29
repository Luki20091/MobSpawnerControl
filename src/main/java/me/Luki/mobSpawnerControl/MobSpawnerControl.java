package me.Luki.mobSpawnerControl;

import org.bukkit.plugin.PluginManager;
import org.bukkit.event.HandlerList;
import org.bukkit.entity.EntityType;
import org.bukkit.plugin.java.JavaPlugin;

public final class MobSpawnerControl extends JavaPlugin {

    private SpawnerListener spawnerListener;

    @Override
    public void onEnable() {
        saveDefaultConfig();

        boolean debug = getConfig().getBoolean("debug", true);
        getLogger().info("MobSpawnerControl enabling");

        PluginManager pm = getServer().getPluginManager();
        spawnerListener = new SpawnerListener(this);
        pm.registerEvents(spawnerListener, this);

        if (debug) getLogger().info("Registered SpawnerListener");

        // ensure every mob has a config section so server admin can toggle drops and respawn behaviour
        for (EntityType t : EntityType.values()) {
            String base = "mobs." + t.name();
            if (!getConfig().isConfigurationSection(base)) {
                // default: do NOT allow spawner respawn when entity has equipment
                getConfig().set(base + ".respawn-with-equipment", false);
                // default: do not allow any drops from spawner-spawned mobs unless explicitly enabled
                getConfig().set(base + ".allow-any-drops", false);
                getConfig().set(base + ".drops", null);
                if (debug) getLogger().info("Created default config section for mob: " + t.name());
            }
        }
        saveConfig();
    }

    @Override
    public void onDisable() {
        getLogger().info("MobSpawnerControl disabling");
        if (spawnerListener != null) {
            try {
                spawnerListener.shutdown();
                HandlerList.unregisterAll(spawnerListener);
            } catch (Throwable ignored) {}
        }
    }
}
