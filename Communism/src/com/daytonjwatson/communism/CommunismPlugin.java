package com.daytonjwatson.communism;

import org.bukkit.Bukkit;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import com.daytonjwatson.communism.commands.CommunismCommand;
import com.daytonjwatson.communism.listeners.CommunismListener;
import com.daytonjwatson.communism.managers.ResourceManager;
import com.daytonjwatson.communism.utils.TaxTask;

public class CommunismPlugin extends JavaPlugin {

    private static CommunismPlugin instance;

    private ResourceManager resourceManager;
    private boolean enabled;

    @Override
    public void onEnable() {
        instance = this;
        saveDefaultConfig();
        reloadState();

        this.resourceManager = new ResourceManager(this);
        this.resourceManager.load();

        // Register listeners
        Bukkit.getPluginManager().registerEvents(new CommunismListener(this, resourceManager), this);

        // Register command
        CommunismCommand cmd = new CommunismCommand(this, resourceManager);
        getCommand("communism").setExecutor(cmd);
        getCommand("communism").setTabCompleter(cmd);

        // Schedule taxes
        scheduleTaxTask();
        getLogger().info("Communism enabled. All your loot belongs to the State.");
    }

    @Override
    public void onDisable() {
        if (resourceManager != null) {
            resourceManager.save();
        }
    }

    public static CommunismPlugin getInstance() {
        return instance;
    }

    public boolean isCommunismEnabled() {
        return enabled;
    }

    public void toggleCommunism() {
        this.enabled = !this.enabled;
        FileConfiguration cfg = getConfig();
        cfg.set("enabled", this.enabled);
        saveConfig();
    }

    public void reloadState() {
        reloadConfig();
        this.enabled = getConfig().getBoolean("enabled", true);
    }

    private void scheduleTaxTask() {
        long interval = getConfig().getLong("tax-interval-ticks", 12000L);
        if (interval <= 0) return;

        new TaxTask(this, resourceManager).runTaskTimer(this, interval, interval);
    }
}
