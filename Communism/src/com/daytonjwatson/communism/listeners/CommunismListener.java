package com.daytonjwatson.communism.listeners;

import java.util.HashSet;
import java.util.Set;

import org.bukkit.ChatColor;
import org.bukkit.GameMode;
import org.bukkit.Material;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;

import com.daytonjwatson.communism.CommunismPlugin;
import com.daytonjwatson.communism.managers.ResourceManager;

public class CommunismListener implements Listener {

    private final CommunismPlugin plugin;
    private final ResourceManager resourceManager;
    private final Set<Material> seizedBlocks;

    public CommunismListener(CommunismPlugin plugin, ResourceManager resourceManager) {
        this.plugin = plugin;
        this.resourceManager = resourceManager;
        this.seizedBlocks = new HashSet<>();
        reloadMaterials();
    }

    private void reloadMaterials() {
        seizedBlocks.clear();
        FileConfiguration cfg = plugin.getConfig();
        for (String s : cfg.getStringList("seized-blocks")) {
            Material m = Material.matchMaterial(s);
            if (m != null) {
                seizedBlocks.add(m);
            } else {
                plugin.getLogger().warning("Unknown seized-block material in config: " + s);
            }
        }
    }

    @EventHandler
    public void onBlockBreak(BlockBreakEvent event) {
        if (!plugin.isCommunismEnabled()) return;

        Player player = event.getPlayer();
        if (player.getGameMode() != GameMode.SURVIVAL) return;

        Material type = event.getBlock().getType();
        if (!seizedBlocks.contains(type)) return;

        // Stop normal drops; send them to the State instead.
        event.setDropItems(false);

        // Treat each block as 1 "unit" for simplicity.
        resourceManager.add(type, 1);

        player.sendMessage(ChatColor.RED + "" + ChatColor.BOLD +
                "All " + type.name() + " now belong to the State.");
        player.sendMessage(ChatColor.GRAY +
                "You worked, but the Party keeps the profit. Enjoy equality in poverty.");
    }
}
