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
import org.bukkit.Bukkit;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.entity.EntityPickupItemEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.entity.FoodLevelChangeEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.inventory.FurnaceExtractEvent;
import org.bukkit.event.inventory.CraftItemEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerItemConsumeEvent;
import org.bukkit.event.player.PlayerFishEvent;
import org.bukkit.inventory.ItemStack;

import com.daytonjwatson.communism.CommunismPlugin;
import com.daytonjwatson.communism.managers.ResourceManager;

public class CommunismListener implements Listener {

    private final CommunismPlugin plugin;
    private final ResourceManager resourceManager;
    private final Set<Material> seizedBlocks;
    private final Set<Material> taxedMaterials;
    private final Set<Material> luxuryMaterials;
    private double baseTaxPercent;
    private double luxuryTaxPercent;
    private double generalActivityTaxPercent;
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

        baseTaxPercent = clampPercent(cfg.getDouble("tax-percent", 0.25));
        luxuryTaxPercent = clampPercent(cfg.getDouble("luxury-tax-percent", baseTaxPercent));
        generalActivityTaxPercent = clampPercent(cfg.getDouble("general-activity-tax-percent", 0.10));

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
        double percent = getTaxPercent(type, true);
        if (percent <= 0) return;

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
    public void onItemPickup(EntityPickupItemEvent event) {
        if (!plugin.isCommunismEnabled()) return;
        if (!(event.getEntity() instanceof Player)) return;

        Player player = (Player) event.getEntity();
        if (player.getGameMode() != GameMode.SURVIVAL) return;

        ItemStack stack = event.getItem().getItemStack();
        Material type = stack.getType();
        if (type == Material.AIR) return;

        double percent = getTaxPercent(type, true);
        if (percent <= 0) return;

        int toTake = (int) Math.floor(stack.getAmount() * percent);
        if (toTake <= 0) return;

        int remainder = stack.getAmount() - toTake;

        if (remainder <= 0) {
            event.setCancelled(true);
            player.sendMessage(ChatColor.RED + "The State snatched the item before you could pick it up.");
        } else {
            stack.setAmount(remainder);
            event.getItem().setItemStack(stack);
            player.sendMessage(ChatColor.RED + "The State skims " + toTake + " from everything you touch.");
        }

        resourceManager.add(type, toTake);
        resourceManager.save();
    }

    @EventHandler
    public void onCraftItem(CraftItemEvent event) {
        if (!plugin.isCommunismEnabled()) return;
        if (!(event.getWhoClicked() instanceof Player)) return;

        Player player = (Player) event.getWhoClicked();
        if (player.getGameMode() != GameMode.SURVIVAL) return;

        ItemStack result = event.getCurrentItem();
        if (result == null) return;

        Material type = result.getType();
        double percent = getTaxPercent(type, true);
        if (percent <= 0) return;

        int toTake = (int) Math.floor(result.getAmount() * percent);
        if (toTake <= 0) return;

        int remainder = result.getAmount() - toTake;
        if (remainder <= 0) {
            event.setCancelled(true);
            player.sendMessage(ChatColor.RED + "The workshop foreman rejected your craft as untaxed.");
        } else {
            result.setAmount(remainder);
            event.setCurrentItem(result);
            player.sendMessage(ChatColor.RED + "The State kept " + toTake + " of your freshly crafted goods.");
        }

        resourceManager.add(type, toTake);
        resourceManager.save();
    }

    @EventHandler
    public void onFish(PlayerFishEvent event) {
        if (!plugin.isCommunismEnabled()) return;
        if (!(event.getCaught() instanceof org.bukkit.entity.Item)) return;

        Player player = event.getPlayer();
        if (player.getGameMode() != GameMode.SURVIVAL) return;

        org.bukkit.entity.Item caught = (org.bukkit.entity.Item) event.getCaught();
        ItemStack stack = caught.getItemStack();
        Material type = stack.getType();
        double percent = getTaxPercent(type, true);
        if (percent <= 0) return;

        int toTake = (int) Math.floor(stack.getAmount() * percent);
        if (toTake <= 0) return;

        int remainder = stack.getAmount() - toTake;
        if (remainder <= 0) {
            caught.remove();
            player.sendMessage(ChatColor.GRAY + "The fishing inspector confiscated your entire catch.");
        } else {
            stack.setAmount(remainder);
            caught.setItemStack(stack);
            player.sendMessage(ChatColor.GRAY + "You lost " + toTake + " fish to surprise inspection.");
        }

        resourceManager.add(type, toTake);
        resourceManager.save();
    }

    @EventHandler
    public void onItemConsume(PlayerItemConsumeEvent event) {
        if (!plugin.isCommunismEnabled()) return;

        Player player = event.getPlayer();
        if (player.getGameMode() != GameMode.SURVIVAL) return;

        ItemStack item = event.getItem();
        Material type = item.getType();
        double percent = getTaxPercent(type, true);
        if (percent <= 0) return;

        int confiscate = Math.max(1, (int) Math.floor(percent * Math.max(1, item.getAmount())));

        Bukkit.getScheduler().runTask(plugin, () -> {
            Map<Integer, ItemStack> leftover = player.getInventory().removeItem(new ItemStack(type, confiscate));
            int seized = confiscate;
            for (ItemStack stack : leftover.values()) {
                seized -= stack.getAmount();
            }

            if (seized > 0) {
                resourceManager.add(type, seized);
                resourceManager.save();
                player.sendMessage(ChatColor.RED + "Consumption tax deducted " + seized + " extra item(s). Enjoy your crumbs.");
            }
        });
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

    private double getTaxPercent(Material material, boolean allowGeneralTax) {
        if (material == null) return 0.0;
        if (luxuryMaterials.contains(material)) return luxuryTaxPercent;
        if (taxedMaterials.contains(material)) return baseTaxPercent;
        return allowGeneralTax ? generalActivityTaxPercent : 0.0;
    }
}
