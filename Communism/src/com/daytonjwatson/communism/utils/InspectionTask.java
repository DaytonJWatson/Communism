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
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitRunnable;

import com.daytonjwatson.communism.CommunismPlugin;
import com.daytonjwatson.communism.managers.ResourceManager;

public class InspectionTask extends BukkitRunnable {

    private final CommunismPlugin plugin;
    private final ResourceManager resourceManager;
    private final Set<Material> contraband;
    private final double seizurePercent;
    private final int contrabandThreshold;
    private final int penaltyTicks;
    private final double partyLeniency;
    private final List<String> partyMembers;

    public InspectionTask(CommunismPlugin plugin, ResourceManager resourceManager) {
        this.plugin = plugin;
        this.resourceManager = resourceManager;

        FileConfiguration cfg = plugin.getConfig();
        this.seizurePercent = clamp(cfg.getDouble("inspection-seizure-percent", 0.35));
        this.contrabandThreshold = Math.max(1, cfg.getInt("inspection-contraband-threshold", 48));
        this.penaltyTicks = Math.max(20, cfg.getInt("inspection-penalty-ticks", 400));
        this.partyLeniency = clamp(cfg.getDouble("inspection-party-leniency", 0.6));
        this.partyMembers = cfg.getStringList("party-members");

        contraband = new HashSet<>();
        for (String s : cfg.getStringList("taxed-materials")) {
            Material mat = Material.matchMaterial(s);
            if (mat != null) {
                contraband.add(mat);
            }
        }
        for (String s : cfg.getStringList("luxury-materials")) {
            Material mat = Material.matchMaterial(s);
            if (mat != null) {
                contraband.add(mat);
            }
        }
    }

    @Override
    public void run() {
        if (!plugin.isCommunismEnabled()) return;
        if (Bukkit.getOnlinePlayers().isEmpty()) return;

        int totalSeized = 0;
        for (Player player : Bukkit.getOnlinePlayers()) {
            if (player.getGameMode() != GameMode.SURVIVAL) continue;

            boolean isParty = isPartyMember(player.getName());
            int heldContraband = countContraband(player);
            if (heldContraband < contrabandThreshold) continue;

            double leniency = isParty ? partyLeniency : 1.0;
            int toSeize = (int) Math.floor(heldContraband * seizurePercent * leniency);
            if (toSeize <= 0) continue;

            int seized = confiscate(player, toSeize);
            if (seized <= 0) continue;

            totalSeized += seized;
            applyPenalties(player);
            sendInspectionMessage(player, seized, isParty);
        }

        if (totalSeized > 0) {
            Bukkit.broadcastMessage(Utils.PREFIX + ChatColor.DARK_RED + "State inspectors seized " + ChatColor.WHITE
                    + totalSeized + ChatColor.DARK_RED + " contraband items during random inspections.");
        }

        if (totalSeized > 0) {
            resourceManager.save();
        }
    }

    private int countContraband(Player player) {
        int amount = 0;
        for (ItemStack stack : player.getInventory().getContents()) {
            if (stack == null || stack.getType() == Material.AIR) continue;
            if (!contraband.contains(stack.getType())) continue;
            amount += stack.getAmount();
        }
        return amount;
    }

    private int confiscate(Player player, int amountNeeded) {
        ItemStack[] contents = player.getInventory().getContents();
        int seized = 0;

        for (int i = 0; i < contents.length && amountNeeded > 0; i++) {
            ItemStack stack = contents[i];
            if (stack == null || stack.getType() == Material.AIR) continue;
            if (!contraband.contains(stack.getType())) continue;

            int take = Math.min(stack.getAmount(), amountNeeded);
            stack.setAmount(stack.getAmount() - take);
            contents[i] = stack.getAmount() > 0 ? stack : null;

            resourceManager.add(stack.getType(), take);
            seized += take;
            amountNeeded -= take;
        }

        player.getInventory().setContents(contents);
        return seized;
    }

    private void applyPenalties(Player player) {
        player.addPotionEffect(new PotionEffect(PotionEffectType.SLOW, penaltyTicks, 1, true, true, true));
        player.addPotionEffect(new PotionEffect(PotionEffectType.MINING_FATIGUE, penaltyTicks, 1, true, true, true));
    }

    private void sendInspectionMessage(Player player, int seized, boolean isParty) {
        if (isParty) {
            player.sendMessage(Utils.PREFIX + ChatColor.GOLD + "Your Party papers softened the blow, but inspectors still"
                    + " seized " + seized + " items and put you on watch.");
        } else {
            player.sendMessage(Utils.PREFIX + ChatColor.RED + "Surprise inspection! " + seized
                    + " items were confiscated and your movement was restricted.");
        }
    }

    private boolean isPartyMember(String name) {
        return partyMembers.stream().anyMatch(n -> n.equalsIgnoreCase(name));
    }

    private double clamp(double value) {
        return Math.max(0.0, Math.min(1.0, value));
    }
}
