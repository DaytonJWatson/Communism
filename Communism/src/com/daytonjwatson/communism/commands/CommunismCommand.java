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
import java.util.stream.Collectors;

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
import org.bukkit.inventory.meta.BookMeta;

import com.daytonjwatson.communism.CommunismPlugin;
import com.daytonjwatson.communism.managers.ResourceManager;
import com.daytonjwatson.communism.managers.StatusMenuManager;
import com.daytonjwatson.communism.utils.TaxTask;

public class CommunismCommand implements CommandExecutor, TabCompleter {

    private final CommunismPlugin plugin;
    private final ResourceManager resourceManager;
    private final com.daytonjwatson.communism.listeners.CommunismListener listener;
    private final StatusMenuManager statusMenuManager;

    public CommunismCommand(CommunismPlugin plugin, ResourceManager resourceManager,
            com.daytonjwatson.communism.listeners.CommunismListener listener, StatusMenuManager statusMenuManager) {
        this.plugin = plugin;
        this.resourceManager = resourceManager;
        this.listener = listener;
        this.statusMenuManager = statusMenuManager;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {

        if (args.length == 0) {
            sendHelp(sender, label);
            return true;
        }

        String sub = args[0].toLowerCase(Locale.ROOT);

        if (sub.equals("guide")) {
            giveGuide(sender);
            return true;
        }

        if (!sender.hasPermission("communism.admin")) {
            sender.sendMessage(ChatColor.RED + "You lack the Party credentials to touch this.");
            return true;
        }

        switch (sub) {
            case "status":
                handleStatus(sender);
                break;
            case "payout":
                handlePayout(sender);
                break;
            case "forcetax":
                new TaxTask(plugin, resourceManager).run();
                sender.sendMessage(ChatColor.RED + "Forced a tax sweep. Workers tremble.");
                break;
            case "toggle":
                plugin.toggleCommunism();
                sender.sendMessage(ChatColor.YELLOW + "Communism is now " +
                        (plugin.isCommunismEnabled() ? ChatColor.GREEN + "ENABLED" : ChatColor.RED + "DISABLED"));
                break;
            case "reload":
                plugin.reloadState();
                listener.reloadMaterials();
                sender.sendMessage(ChatColor.GREEN + "Communism config reloaded.");
                break;
            case "clearpool":
                resourceManager.clear();
                resourceManager.save();
                sender.sendMessage(ChatColor.YELLOW + "State pool emptied. Chaos for testing achieved.");
                break;
            case "givepool":
                handleGivePool(sender, args);
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
        sender.sendMessage(ChatColor.YELLOW + "/" + label + " forcetax " + ChatColor.GRAY + "- Run an instant tax cycle.");
        sender.sendMessage(ChatColor.YELLOW + "/" + label + " clearpool " + ChatColor.GRAY + "- Empty the State's hoard.");
        sender.sendMessage(ChatColor.YELLOW + "/" + label + " givepool <player> " + ChatColor.GRAY + "- Dump everything on one player.");
        sender.sendMessage(ChatColor.YELLOW + "/" + label + " reload " + ChatColor.GRAY + "- Reload config.");
        sender.sendMessage(ChatColor.YELLOW + "/" + label + " guide " + ChatColor.GRAY + "- Receive the propaganda handbook.");
    }

    private void handleStatus(CommandSender sender) {
        Map<Material, Integer> snapshot = resourceManager.getSnapshot();
        if (sender instanceof Player) {
            statusMenuManager.openStatusMenu((Player) sender, snapshot);
            return;
        }

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

        resourceManager.save();
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

    private void handleGivePool(CommandSender sender, String[] args) {
        if (args.length < 2) {
            sender.sendMessage(ChatColor.RED + "Usage: /communism givepool <player>");
            return;
        }

        Player target = Bukkit.getPlayerExact(args[1]);
        if (target == null) {
            sender.sendMessage(ChatColor.RED + "Player not found or not online.");
            return;
        }

        Map<Material, Integer> snapshot = resourceManager.getSnapshot();
        if (snapshot.isEmpty()) {
            sender.sendMessage(ChatColor.GRAY + "State pool is empty. No chaos today.");
            return;
        }

        int itemsGiven = 0;
        for (Map.Entry<Material, Integer> entry : snapshot.entrySet()) {
            int amount = entry.getValue();
            if (amount <= 0) continue;

            giveSafe(target, new ItemStack(entry.getKey(), amount));
            resourceManager.remove(entry.getKey(), amount);
            itemsGiven += amount;
        }

        resourceManager.save();
        sender.sendMessage(ChatColor.YELLOW + "Handed " + itemsGiven + " items to " + target.getName() + " from the State pool.");
        target.sendMessage(ChatColor.DARK_RED + "The Party blessed you with everything. Expect suspicion.");
    }

    private void giveGuide(CommandSender sender) {
        if (!(sender instanceof Player)) {
            sender.sendMessage(ChatColor.RED + "Only players can read the propaganda book.");
            return;
        }

        Player player = (Player) sender;
        FileConfiguration cfg = plugin.getConfig();
        List<String> pages = cfg.getStringList("messages.guide");
        if (pages.isEmpty()) {
            pages = List.of("Follow the rules: Taxes, confiscation, party privilege, and death means the State keeps all.");
        }

        ItemStack book = new ItemStack(Material.WRITTEN_BOOK);
        BookMeta meta = (BookMeta) book.getItemMeta();
        if (meta != null) {
            meta.setAuthor(ChatColor.RED + "The State");
            meta.setTitle(ChatColor.DARK_RED + "How To Obey");
            meta.setPages(pages.stream().map(page -> ChatColor.translateAlternateColorCodes('&', page)).collect(Collectors.toList()));
            book.setItemMeta(meta);
        }

        giveSafe(player, book);
        player.sendMessage(ChatColor.GRAY + "You received the official guide to your own exploitation.");
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (!sender.hasPermission("communism.admin")) return Collections.emptyList();

        if (args.length == 1) {
            List<String> base = Arrays.asList("status", "payout", "toggle", "reload", "forcetax", "clearpool", "givepool", "guide");
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
