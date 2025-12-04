package com.daytonjwatson.communism.utils;

import java.util.List;
import java.util.Random;

import org.bukkit.Bukkit;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.scheduler.BukkitRunnable;

import com.daytonjwatson.communism.CommunismPlugin;

public class PropagandaTask extends BukkitRunnable {

    private final CommunismPlugin plugin;
    private final Random random;

    public PropagandaTask(CommunismPlugin plugin) {
        this.plugin = plugin;
        this.random = new Random();
    }

    @Override
    public void run() {
        if (!plugin.isCommunismEnabled()) return;

        FileConfiguration cfg = plugin.getConfig();
        List<String> propaganda = cfg.getStringList("messages.propaganda");
        if (propaganda.isEmpty()) return;

        String message = propaganda.get(random.nextInt(propaganda.size()));
        Bukkit.broadcastMessage(Utils.prefix("&7" + message));
    }
}
