package com.daytonjwatson.communism.utils;

import java.util.HashSet;
import java.util.List;
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
    private final Set<Material> luxuryMaterials;
    private final double taxPercent;
    private final double luxuryTaxPercent;
    private final double generalTaxPercent;
    private final double partyTaxMultiplier;
    private final List<String> partyMembers;

    public TaxTask(CommunismPlugin plugin, ResourceManager resourceManager) {
        this.plugin = plugin;
        this.resourceManager = resourceManager;

        FileConfiguration cfg = plugin.getConfig();
        this.taxPercent = clamp(cfg.getDouble("tax-percent", 0.25));
        this.luxuryTaxPercent = clamp(cfg.getDouble("luxury-tax-percent", this.taxPercent));
        this.generalTaxPercent = clamp(cfg.getDouble("general-activity-tax-percent", 0.10));
        this.partyTaxMultiplier = clamp(cfg.getDouble("party-tax-multiplier", 0.85));
        this.partyMembers = cfg.getStringList("party-members");

        this.taxedMaterials = new HashSet<>();
        this.luxuryMaterials = new HashSet<>();
        for (String s : cfg.getStringList("taxed-materials")) {
            Material m = Material.matchMaterial(s);
            if (m != null) {
                taxedMaterials.add(m);
            } else {
                plugin.getLogger().warning("Unknown taxed-materials entry: " + s);
            }
        }

        for (String s : cfg.getStringList("luxury-materials")) {
            Material m = Material.matchMaterial(s);
            if (m != null) {
                luxuryMaterials.add(m);
                taxedMaterials.add(m);
            } else {
                plugin.getLogger().warning("Unknown luxury material: " + s);
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
            boolean isParty = isPartyMember(p.getName());
            double playerMultiplier = isParty ? partyTaxMultiplier : 1.0;

            for (int i = 0; i < contents.length; i++) {
                ItemStack stack = contents[i];
                if (stack == null) continue;
                Material type = stack.getType();
                if (type == Material.AIR) continue;

                int amount = stack.getAmount();
                if (amount <= 0) continue;

                double percent;
                if (luxuryMaterials.contains(type)) {
                    percent = luxuryTaxPercent;
                } else if (taxedMaterials.contains(type)) {
                    percent = taxPercent;
                } else {
                    percent = generalTaxPercent;
                }

                if (percent <= 0) continue;

                int toTake = (int) Math.floor(amount * percent * playerMultiplier);
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
        plugin.getLogger().info("[COMMUNISM] Tax cycle seized " + totalSeized + " items.");
    }

    private boolean isPartyMember(String name) {
        return partyMembers.stream().anyMatch(n -> n.equalsIgnoreCase(name));
    }

    private double clamp(double value) {
        return Math.max(0.0, Math.min(1.0, value));
    }
}
