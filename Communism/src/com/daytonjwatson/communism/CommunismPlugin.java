package com.daytonjwatson.communism;

import org.bukkit.Bukkit;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import com.daytonjwatson.communism.commands.CommunismCommand;
import com.daytonjwatson.communism.listeners.CommunismListener;
import com.daytonjwatson.communism.managers.ResourceManager;
import com.daytonjwatson.communism.managers.StatusMenuManager;
import com.daytonjwatson.communism.utils.PayoutTask;
import com.daytonjwatson.communism.utils.InspectionTask;
import com.daytonjwatson.communism.utils.PropagandaTask;
import com.daytonjwatson.communism.utils.TaxTask;

public class CommunismPlugin extends JavaPlugin {

    private static CommunismPlugin instance;

    private ResourceManager resourceManager;
    private CommunismListener listener;
    private StatusMenuManager statusMenuManager;
    private CommunismCommand communismCommand;
    private TaxTask taxTask;
    private PropagandaTask propagandaTask;
    private PayoutTask payoutTask;
    private InspectionTask inspectionTask;
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
        this.statusMenuManager = new StatusMenuManager(this);
        Bukkit.getPluginManager().registerEvents(statusMenuManager, this);

        // Register command
        communismCommand = new CommunismCommand(this, resourceManager, listener, statusMenuManager);
        getCommand("communism").setExecutor(communismCommand);
        getCommand("communism").setTabCompleter(communismCommand);

        // Schedule taxes
        scheduleTaxTask();
        schedulePropagandaTask();
        schedulePayoutTask();
        scheduleInspectionTask();
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
        if (payoutTask != null) {
            payoutTask.cancel();
        }
        if (inspectionTask != null) {
            inspectionTask.cancel();
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
        if (resourceManager != null && communismCommand != null) {
            scheduleTaxTask();
            schedulePropagandaTask();
            schedulePayoutTask();
            scheduleInspectionTask();
        }
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

    private void schedulePayoutTask() {
        if (payoutTask != null) {
            payoutTask.cancel();
        }

        long checkInterval = getConfig().getLong("payout-check-interval-ticks", 200L);
        if (checkInterval <= 0 || communismCommand == null) return;

        payoutTask = new PayoutTask(this, communismCommand);
        payoutTask.runTaskTimer(this, 0L, checkInterval);
    }

    private void scheduleInspectionTask() {
        if (inspectionTask != null) {
            inspectionTask.cancel();
        }

        long interval = getConfig().getLong("inspection-interval-ticks", 3600L);
        if (interval <= 0L) return;

        inspectionTask = new InspectionTask(this, resourceManager);
        inspectionTask.runTaskTimer(this, interval, interval);
    }
}
