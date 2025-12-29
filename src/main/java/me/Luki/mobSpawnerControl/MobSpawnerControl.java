package me.Luki.mobSpawnerControl;

import org.bukkit.plugin.PluginManager;
import org.bukkit.command.CommandMap;
import org.bukkit.command.Command;
import java.lang.reflect.Field;
import java.util.Arrays;
import org.bukkit.event.HandlerList;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.command.CommandSender;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;

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

        // Register command programmatically (Paper requires programmatic registration)
        try {
            CommandMap commandMap = null;
            Field f = null;
            Object holder = null;
            try {
                f = pm.getClass().getDeclaredField("commandMap");
                holder = pm;
            } catch (NoSuchFieldException ignored) {
                try {
                    f = getServer().getClass().getDeclaredField("commandMap");
                    holder = getServer();
                } catch (NoSuchFieldException ignored2) {
                    f = null;
                }
            }
            if (f != null) {
                f.setAccessible(true);
                commandMap = (CommandMap) f.get(holder);
            }
            if (commandMap != null) {
                Command mobCmd = new Command("mobspawnercontrol") {
                    @Override
                    public boolean execute(CommandSender sender, String commandLabel, String[] args) {
                        return MobSpawnerControl.this.onCommand(sender, this, commandLabel, args);
                    }
                };
                mobCmd.setAliases(Arrays.asList("msc"));
                mobCmd.setPermission("mobspawnercontrol.reload");
                commandMap.register("MobSpawnerControl", mobCmd);
            } else {
                getLogger().warning("CommandMap not available; /mobspawnercontrol not registered.");
            }
        } catch (Throwable t) {
            getLogger().warning("Could not register command programmatically: " + t.getMessage());
        }

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

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (command.getName().equalsIgnoreCase("mobspawnercontrol") || command.getName().equalsIgnoreCase("msc")) {
            if (args.length > 0 && args[0].equalsIgnoreCase("reload")) {
                if (!sender.hasPermission("mobspawnercontrol.reload")) {
                    sender.sendMessage(Component.text("You don't have permission to reload MobSpawnerControl.").color(NamedTextColor.RED));
                    return true;
                }
                try {
                    reloadConfig();
                    sender.sendMessage(Component.text("MobSpawnerControl config reloaded.").color(NamedTextColor.GREEN));
                } catch (Throwable t) {
                    sender.sendMessage(Component.text("Failed to reload config: " + t.getMessage()).color(NamedTextColor.RED));
                }
                return true;
            }
            sender.sendMessage(Component.text("Usage: /mobspawnercontrol reload").color(NamedTextColor.YELLOW));
            return true;
        }
        return false;
    }
}
