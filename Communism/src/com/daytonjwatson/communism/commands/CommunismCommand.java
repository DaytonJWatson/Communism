package com.daytonjwatson.communism.commands;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import com.daytonjwatson.communism.CommunismPlugin;
import com.daytonjwatson.communism.managers.ResourceManager;

public class CommunismCommand implements CommandExecutor, TabCompleter {

    private final CommunismPlugin plugin;
    private final ResourceManager resourceManager;

    public CommunismCommand(CommunismPlugin plugin, ResourceManager resourceManager) {
        this.plugin = plugin;
        this.resourceManager = resourceManager;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {

        if (!sender.hasPermission("communism.admin")) {
            sender.sendMessage(ChatColor.RED + "You lack the Party credentials to touch this.");
            return true;
        }

        if (args.length == 0) {
            sendHelp(sender, label);
            return true;
        }

        String sub = args[0].toLowerCase(Locale.ROOT);

        switch (sub) {
            case "status":
                handleStatus(sender);
                break;
            case "payout":
                handlePayout(sender);
                break;
            case "toggle":
                plugin.toggleCommunism();
                sender.sendMessage(ChatColor.YELLOW + "Communism is now " +
                        (plugin.isCommunismEnabled() ? ChatColor.GREEN + "ENABLED" : ChatColor.RED + "DISABLED"));
                break;
            case "reload":
                plugin.reloadState();
                sender.sendMessage(ChatColor.GREEN + "Communism config reloaded.");
                break;
            default:
                sendHelp(sender, label);
        }

        return true;
    }

    private void sendHelp(CommandSender sender, String label) {
        sender.sendMessage(ChatColor.DARK_RED + "" + ChatColor.BOLD + "=== COMMUNISM CONTROL ===");
        sender.sendMessage(ChatColor.YELLOW + "/" + label + " status " + ChatColor.GRAY + "- View the State's hoarded resources.");
        sender.sendMessage(ChatColor.YELLOW + "/" + label + " payout " + ChatColor.GRAY + "- Redistribute resources 'equally'.");
        sender.sendMessage(ChatColor.YELLOW + "/" + label + " toggle " + ChatColor.GRAY + "- Enable/disable communism.");
        sender.sendMessage(ChatColor.YELLOW + "/" + label + " reload " + ChatColor.GRAY + "- Reload config.");
    }

    private void handleStatus(CommandSender sender) {
        Map<Material, Integer> snapshot = resourceManager.getSnapshot();
        if (snapshot.isEmpty()) {
            sender.sendMessage(ChatColor.GRAY + "The State currently holds nothing. Even the bureaucracy is poor.");
            return;
        }

        sender.sendMessage(ChatColor.DARK_RED + "" + ChatColor.BOLD + "=== STATE RESOURCE POOL ===");
        snapshot.entrySet().stream()
                .sorted(Comparator.comparingInt(Map.Entry<Material, Integer>::getValue).reversed())
                .limit(20)
                .forEach(e -> sender.sendMessage(ChatColor.RED + e.getKey().name() + ": " +
                        ChatColor.WHITE + e.getValue()));
    }

    private void handlePayout(CommandSender sender) {
        Collection<? extends Player> online = Bukkit.getOnlinePlayers();
        if (online.isEmpty()) {
            sender.sendMessage(ChatColor.GRAY + "No workers online to 'reward'.");
            return;
        }

        Map<Material, Integer> snapshot = resourceManager.getSnapshot();
        if (snapshot.isEmpty()) {
            sender.sendMessage(ChatColor.GRAY + "The State has nothing to redistribute.");
            return;
        }

        FileConfiguration cfg = plugin.getConfig();
        List<String> partyNames = cfg.getStringList("party-members");
        double bonusMultiplier = Math.max(1.0, cfg.getDouble("party-bonus-multiplier", 2.0));

        // Build lists of party vs non-party players
        List<Player> partyPlayers = new ArrayList<>();
        List<Player> workers = new ArrayList<>();
        for (Player p : online) {
            if (partyNames.stream().anyMatch(name -> name.equalsIgnoreCase(p.getName()))) {
                partyPlayers.add(p);
            } else {
                workers.add(p);
            }
        }

        int totalPlayers = online.size();
        if (totalPlayers == 0) {
            sender.sendMessage(ChatColor.GRAY + "No workers online.");
            return;
        }

        // For each material: split equally, but party members get a bigger slice,
        // illustrating corruption in the system.
        int materialsPaidOut = 0;

        for (Map.Entry<Material, Integer> entry : snapshot.entrySet()) {
            Material mat = entry.getKey();
            int total = entry.getValue();
            if (total <= 0) continue;

            if (partyPlayers.isEmpty()) {
                // Everyone equal (but rounding still loses items to "bureaucracy")
                int perPlayer = total / totalPlayers;
                if (perPlayer <= 0) continue;

                for (Player p : online) {
                    giveSafe(p, new ItemStack(mat, perPlayer));
                }
                resourceManager.remove(mat, perPlayer * totalPlayers);
                materialsPaidOut++;
            } else {
                // Weighted payout
                double partyWeight = bonusMultiplier;
                double workerWeight = 1.0;

                double totalWeight = partyPlayers.size() * partyWeight + workers.size() * workerWeight;
                if (totalWeight <= 0) continue;

                int distributed = 0;

                for (Player p : partyPlayers) {
                    int amount = (int) Math.floor((partyWeight / totalWeight) * total);
                    if (amount <= 0) continue;
                    giveSafe(p, new ItemStack(mat, amount));
                    distributed += amount;
                }

                for (Player p : workers) {
                    int amount = (int) Math.floor((workerWeight / totalWeight) * total);
                    if (amount <= 0) continue;
                    giveSafe(p, new ItemStack(mat, amount));
                    distributed += amount;
                }

                resourceManager.remove(mat, distributed);
                materialsPaidOut++;
            }
        }

        if (materialsPaidOut == 0) {
            sender.sendMessage(ChatColor.GRAY + "The State had nothing meaningful to redistribute.");
            return;
        }

        // Flavour broadcasts
        Bukkit.broadcastMessage(ChatColor.DARK_RED + "" + ChatColor.BOLD +
                "[COMMUNISM] " + ChatColor.RED + "Resources have been redistributed.");

        for (Player p : Bukkit.getOnlinePlayers()) {
            boolean isParty = isPartyMember(p, plugin.getConfig().getStringList("party-members"));
            if (isParty) {
                p.sendMessage(ChatColor.GOLD + "As a loyal Party member, you somehow got a bit more than everyone else.");
            } else {
                p.sendMessage(ChatColor.GRAY + "You received an equal share... except somehow it feels less than you earned.");
            }
        }
    }

    private boolean isPartyMember(OfflinePlayer player, List<String> partyNames) {
        return partyNames.stream().anyMatch(n -> n.equalsIgnoreCase(player.getName()));
    }

    private void giveSafe(Player player, ItemStack stack) {
        HashMap<Integer, ItemStack> overflow = player.getInventory().addItem(stack);
        if (!overflow.isEmpty()) {
            for (ItemStack extra : overflow.values()) {
                player.getWorld().dropItemNaturally(player.getLocation(), extra);
            }
            player.sendMessage(ChatColor.GRAY + "Your pockets overflow with centrally planned generosity.");
        }
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (!sender.hasPermission("communism.admin")) return Collections.emptyList();

        if (args.length == 1) {
            List<String> base = Arrays.asList("status", "payout", "toggle", "reload");
            String prefix = args[0].toLowerCase(Locale.ROOT);
            List<String> out = new ArrayList<>();
            for (String s : base) {
                if (s.startsWith(prefix)) {
                    out.add(s);
                }
            }
            return out;
        }

        return Collections.emptyList();
    }
}
