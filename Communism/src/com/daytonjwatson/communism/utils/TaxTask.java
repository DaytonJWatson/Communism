package com.daytonjwatson.communism.utils;

import java.util.HashSet;
import java.util.Set;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.GameMode;
import org.bukkit.Material;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.scheduler.BukkitRunnable;

import com.daytonjwatson.communism.CommunismPlugin;
import com.daytonjwatson.communism.managers.ResourceManager;

public class TaxTask extends BukkitRunnable {

    private final CommunismPlugin plugin;
    private final ResourceManager resourceManager;
    private final Set<Material> taxedMaterials;
    private final double taxPercent;

    public TaxTask(CommunismPlugin plugin, ResourceManager resourceManager) {
        this.plugin = plugin;
        this.resourceManager = resourceManager;

        FileConfiguration cfg = plugin.getConfig();
        this.taxPercent = Math.max(0.0, Math.min(1.0, cfg.getDouble("tax-percent", 0.25)));

        this.taxedMaterials = new HashSet<>();
        for (String s : cfg.getStringList("taxed-materials")) {
            Material m = Material.matchMaterial(s);
            if (m != null) {
                taxedMaterials.add(m);
            } else {
                plugin.getLogger().warning("Unknown taxed-materials entry: " + s);
            }
        }
    }

    @Override
    public void run() {
        if (!plugin.isCommunismEnabled()) return;

        if (Bukkit.getOnlinePlayers().isEmpty()) return;

        Bukkit.broadcastMessage(ChatColor.DARK_RED + "" + ChatColor.BOLD +
                "[COMMUNISM] " + ChatColor.RED + "The State is collecting its share. Hide it if you can...");

        int totalSeized = 0;

        for (Player p : Bukkit.getOnlinePlayers()) {
        	if (p.getGameMode() != GameMode.SURVIVAL) continue;

            ItemStack[] contents = p.getInventory().getContents();
            for (int i = 0; i < contents.length; i++) {
                ItemStack stack = contents[i];
                if (stack == null) continue;
                if (!taxedMaterials.contains(stack.getType())) continue;

                int amount = stack.getAmount();
                if (amount <= 0) continue;

                int toTake = (int) Math.floor(amount * taxPercent);
                if (toTake <= 0) continue;

                stack.setAmount(amount - toTake);
                contents[i] = stack.getAmount() > 0 ? stack : null;

                resourceManager.add(stack.getType(), toTake);
                totalSeized += toTake;
            }

            p.getInventory().setContents(contents);

            p.sendMessage(ChatColor.RED + "The State seized a portion of your goods for the 'greater good'.");
            p.sendMessage(ChatColor.GRAY + "You feel less motivated to work somehow...");
        }

        if (totalSeized > 0) {
            Bukkit.broadcastMessage(ChatColor.DARK_RED + "[COMMUNISM] " + ChatColor.GRAY +
                    "The State seized " + totalSeized + " items to feed the bureaucracy.");
        } else {
            Bukkit.broadcastMessage(ChatColor.DARK_RED + "[COMMUNISM] " + ChatColor.GRAY +
                    "There was nothing left worth taking this cycle.");
        }

        resourceManager.save();
    }
}
