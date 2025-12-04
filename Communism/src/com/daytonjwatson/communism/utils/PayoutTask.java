package com.daytonjwatson.communism.utils;

import java.util.List;

import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.command.CommandSender;
import org.bukkit.scheduler.BukkitRunnable;

import com.daytonjwatson.communism.CommunismPlugin;
import com.daytonjwatson.communism.commands.CommunismCommand;

public class PayoutTask extends BukkitRunnable {

    private final CommunismPlugin plugin;
    private final CommunismCommand command;
    private final long morningThreshold;
    private long lastPayoutDay = -1L;

    public PayoutTask(CommunismPlugin plugin, CommunismCommand command) {
        this.plugin = plugin;
        this.command = command;
        this.morningThreshold = 1000L;
    }

    @Override
    public void run() {
        if (!plugin.isCommunismEnabled()) return;

        List<World> worlds = Bukkit.getWorlds();
        if (worlds.isEmpty()) return;

        World world = worlds.get(0);
        long fullTime = world.getFullTime();
        long currentDay = fullTime / 24000L;
        long timeOfDay = world.getTime();

        if (timeOfDay <= morningThreshold && currentDay != lastPayoutDay) {
            CommandSender console = Bukkit.getConsoleSender();
            command.forcePayout(console);
            lastPayoutDay = currentDay;
        }
    }
}
