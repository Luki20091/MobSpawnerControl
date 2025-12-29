package me.Luki.mobSpawnerControl;

import org.bukkit.plugin.PluginManager;
import org.bukkit.event.HandlerList;
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
        // defaults are provided in the bundled config.yml; do not generate per-EntityType sections here to avoid loops
        saveConfig();
    }

    @Override
    public void onDisable() {
        getLogger().info("MobSpawnerControl disabling");
        if (spawnerListener != null) {
            try {
                // cancel any scheduled tasks for this plugin and unregister listener
                getServer().getScheduler().cancelTasks(this);
                HandlerList.unregisterAll(spawnerListener);
            } catch (Throwable ignored) {}
        }
    }
}
