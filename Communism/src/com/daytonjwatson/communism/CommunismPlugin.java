package com.daytonjwatson.communism;

import org.bukkit.Bukkit;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import com.daytonjwatson.communism.commands.CommunismCommand;
import com.daytonjwatson.communism.listeners.CommunismListener;
import com.daytonjwatson.communism.managers.ResourceManager;
import com.daytonjwatson.communism.utils.PropagandaTask;
import com.daytonjwatson.communism.utils.TaxTask;

public class CommunismPlugin extends JavaPlugin {

    private static CommunismPlugin instance;

    private ResourceManager resourceManager;
    private CommunismListener listener;
    private TaxTask taxTask;
    private PropagandaTask propagandaTask;
    private boolean enabled;

    @Override
    public void onEnable() {
        instance = this;
        saveDefaultConfig();
        reloadState();

        this.resourceManager = new ResourceManager(this);
        this.resourceManager.load();

        // Register listeners
        this.listener = new CommunismListener(this, resourceManager);
        Bukkit.getPluginManager().registerEvents(listener, this);

        // Register command
        CommunismCommand cmd = new CommunismCommand(this, resourceManager, listener);
        getCommand("communism").setExecutor(cmd);
        getCommand("communism").setTabCompleter(cmd);

        // Schedule taxes
        scheduleTaxTask();
        schedulePropagandaTask();
        getLogger().info("Communism enabled. All your loot belongs to the State.");
    }

    @Override
    public void onDisable() {
        if (taxTask != null) {
            taxTask.cancel();
        }
        if (propagandaTask != null) {
            propagandaTask.cancel();
        }
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
        if (listener != null) {
            listener.reloadMaterials();
        }
        scheduleTaxTask();
        schedulePropagandaTask();
    }

    private void scheduleTaxTask() {
        if (taxTask != null) {
            taxTask.cancel();
        }

        long interval = getConfig().getLong("tax-interval-ticks", 12000L);
        if (interval <= 0) return;

        taxTask = new TaxTask(this, resourceManager);
        taxTask.runTaskTimer(this, interval, interval);
    }

    private void schedulePropagandaTask() {
        if (propagandaTask != null) {
            propagandaTask.cancel();
        }

        long interval = getConfig().getLong("propaganda-interval-ticks", 6000L);
        if (interval <= 0) return;

        propagandaTask = new PropagandaTask(this);
        propagandaTask.runTaskTimer(this, interval, interval);
    }
}
