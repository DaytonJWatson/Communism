package com.daytonjwatson.communism.listeners;

import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;

import org.bukkit.ChatColor;
import org.bukkit.GameMode;
import org.bukkit.Material;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.entity.FoodLevelChangeEvent;
import org.bukkit.event.inventory.FurnaceExtractEvent;
import org.bukkit.event.player.PlayerDeathEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.inventory.ItemStack;

import com.daytonjwatson.communism.CommunismPlugin;
import com.daytonjwatson.communism.managers.ResourceManager;

public class CommunismListener implements Listener {

    private final CommunismPlugin plugin;
    private final ResourceManager resourceManager;
    private final Set<Material> seizedBlocks;
    private final Set<Material> taxedMaterials;
    private final Set<Material> luxuryMaterials;
    private final Random random;

    public CommunismListener(CommunismPlugin plugin, ResourceManager resourceManager) {
        this.plugin = plugin;
        this.resourceManager = resourceManager;
        this.seizedBlocks = new HashSet<>();
        this.taxedMaterials = new HashSet<>();
        this.luxuryMaterials = new HashSet<>();
        this.random = new Random();
        reloadMaterials();
    }

    public void reloadMaterials() {
        seizedBlocks.clear();
        taxedMaterials.clear();
        luxuryMaterials.clear();
        FileConfiguration cfg = plugin.getConfig();
        for (String s : cfg.getStringList("seized-blocks")) {
            Material m = Material.matchMaterial(s);
            if (m != null) {
                seizedBlocks.add(m);
            } else {
                plugin.getLogger().warning("Unknown seized-block material in config: " + s);
            }
        }

        for (String s : cfg.getStringList("taxed-materials")) {
            Material m = Material.matchMaterial(s);
            if (m != null) {
                taxedMaterials.add(m);
            } else {
                plugin.getLogger().warning("Unknown taxed material in config: " + s);
            }
        }

        for (String s : cfg.getStringList("luxury-materials")) {
            Material m = Material.matchMaterial(s);
            if (m != null) {
                luxuryMaterials.add(m);
                taxedMaterials.add(m);
            } else {
                plugin.getLogger().warning("Unknown luxury material in config: " + s);
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

    @EventHandler
    public void onMobDeath(EntityDeathEvent event) {
        if (!plugin.isCommunismEnabled()) return;
        if (!(event.getEntity().getKiller() instanceof Player)) return;

        Player killer = event.getEntity().getKiller();
        if (killer == null || killer.getGameMode() != GameMode.SURVIVAL) return;

        Iterator<ItemStack> iterator = event.getDrops().iterator();
        int seized = 0;
        while (iterator.hasNext()) {
            ItemStack drop = iterator.next();
            if (drop == null || drop.getType() == Material.AIR) continue;

            Material type = drop.getType();
            if (!taxedMaterials.contains(type) && !seizedBlocks.contains(type)) continue;

            resourceManager.add(type, drop.getAmount());
            iterator.remove();
            seized += drop.getAmount();
        }

        if (seized > 0) {
            killer.sendMessage(ChatColor.RED + "The State confiscated your spoils before you could blink.");
            resourceManager.save();
        }
    }

    @EventHandler
    public void onFurnaceExtract(FurnaceExtractEvent event) {
        if (!plugin.isCommunismEnabled()) return;

        Player player = event.getPlayer();
        if (player.getGameMode() != GameMode.SURVIVAL) return;

        Material type = event.getItemType();
        if (!taxedMaterials.contains(type)) return;

        FileConfiguration cfg = plugin.getConfig();
        double basePercent = clampPercent(cfg.getDouble("tax-percent", 0.25));
        double luxuryPercent = clampPercent(cfg.getDouble("luxury-tax-percent", basePercent));
        double percent = luxuryMaterials.contains(type) ? luxuryPercent : basePercent;

        int amount = event.getItemAmount();
        int toTake = (int) Math.floor(amount * percent);
        if (toTake <= 0) return;

        Map<Integer, ItemStack> leftover = player.getInventory().removeItem(new ItemStack(type, toTake));
        int seized = toTake;
        for (ItemStack stack : leftover.values()) {
            seized -= stack.getAmount();
        }

        if (seized <= 0) return;

        resourceManager.add(type, seized);
        resourceManager.save();
        player.sendMessage(ChatColor.RED + "The furnace attendant quietly rerouted " + seized + " items to the State.");
    }

    @EventHandler
    public void onPlayerDeath(PlayerDeathEvent event) {
        if (!plugin.isCommunismEnabled()) return;

        Player player = event.getEntity();
        if (player.getGameMode() != GameMode.SURVIVAL) return;

        int seized = 0;
        for (ItemStack stack : event.getDrops()) {
            if (stack == null || stack.getType() == Material.AIR) continue;
            resourceManager.add(stack.getType(), stack.getAmount());
            seized += stack.getAmount();
        }

        event.getDrops().clear();

        if (seized > 0) {
            resourceManager.save();
            player.sendMessage(ChatColor.DARK_RED + "Your death was patriotic. The State kept everything.");
        }
    }

    @EventHandler
    public void onFoodLevelChange(FoodLevelChangeEvent event) {
        if (!plugin.isCommunismEnabled()) return;
        if (!(event.getEntity() instanceof Player)) return;

        Player player = (Player) event.getEntity();
        FileConfiguration cfg = plugin.getConfig();
        int maxFood = Math.max(0, cfg.getInt("max-food-level", 14));
        double rationChance = clampPercent(cfg.getDouble("ration-denial-chance", 0.1));
        int newLevel = event.getFoodLevel();

        if (random.nextDouble() < rationChance) {
            event.setCancelled(true);
            player.sendMessage(ChatColor.GRAY + cfg.getString("messages.rations", "Rations were cut without explanation."));
            return;
        }

        if (newLevel > maxFood) {
            event.setFoodLevel(maxFood);
            player.sendMessage(ChatColor.RED + "Your ration booklet only allows you to be this full: " + maxFood + ".");
        }
    }

    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        if (!plugin.isCommunismEnabled()) return;

        Player player = event.getPlayer();
        List<String> partyMembers = plugin.getConfig().getStringList("party-members");
        boolean isParty = partyMembers.stream().anyMatch(n -> n.equalsIgnoreCase(player.getName()));

        if (isParty) {
            String prefix = plugin.getConfig().getString("party-name-prefix", "[PARTY] ");
            player.setDisplayName(prefix + player.getName());
        } else {
            player.setDisplayName(player.getName());
        }
    }

    private double clampPercent(double value) {
        return Math.max(0.0, Math.min(1.0, value));
    }
}
