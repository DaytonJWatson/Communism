package com.daytonjwatson.communism.managers;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import com.daytonjwatson.communism.CommunismPlugin;

public class StatusMenuManager implements Listener {

    private static final int INVENTORY_SIZE = 54;
    private static final int ITEMS_PER_PAGE = 45;

    private final CommunismPlugin plugin;
    private final Map<UUID, StatusSession> sessions = new HashMap<>();

    public StatusMenuManager(CommunismPlugin plugin) {
        this.plugin = plugin;
    }

    public void openStatusMenu(Player player, Map<Material, Integer> snapshot) {
        if (snapshot.isEmpty()) {
            player.sendMessage(ChatColor.GRAY + "The State currently holds nothing. Even the bureaucracy is poor.");
            return;
        }

        List<Map.Entry<Material, Integer>> entries = snapshot.entrySet().stream()
                .sorted(Comparator.comparingInt(Map.Entry<Material, Integer>::getValue).reversed())
                .collect(Collectors.toList());

        StatusSession session = new StatusSession(entries);
        sessions.put(player.getUniqueId(), session);
        player.openInventory(buildPage(session, 0));
    }

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player)) return;
        Player player = (Player) event.getWhoClicked();
        StatusSession session = sessions.get(player.getUniqueId());
        if (session == null) return;
        if (!isStatusInventory(event.getView().getTitle())) return;

        event.setCancelled(true);

        int rawSlot = event.getRawSlot();

        if (rawSlot == 45 && session.page > 0) {
            player.openInventory(buildPage(session, session.page - 1));
        }

        if (rawSlot == 49) {
            player.closeInventory();
        }

        if (rawSlot == 53 && (session.page + 1) < session.totalPages()) {
            player.openInventory(buildPage(session, session.page + 1));
        }
    }

    @EventHandler
    public void onInventoryClose(InventoryCloseEvent event) {
        sessions.remove(event.getPlayer().getUniqueId());
    }

    private Inventory buildPage(StatusSession session, int page) {
        session.page = page;
        Inventory inv = plugin.getServer().createInventory(null, INVENTORY_SIZE,
                ChatColor.DARK_RED + "State Payout (" + (page + 1) + "/" + session.totalPages() + ")");

        int startIndex = page * ITEMS_PER_PAGE;
        int endIndex = Math.min(startIndex + ITEMS_PER_PAGE, session.entries.size());

        for (int i = startIndex; i < endIndex; i++) {
            Map.Entry<Material, Integer> entry = session.entries.get(i);
            ItemStack display = new ItemStack(entry.getKey());
            display.setAmount(Math.max(1, Math.min(64, entry.getValue())));
            ItemMeta meta = display.getItemMeta();
            if (meta != null) {
                meta.setDisplayName(ChatColor.RED + entry.getKey().name());
                List<String> lore = new ArrayList<>();
                lore.add(ChatColor.GRAY + "Total stored: " + ChatColor.WHITE + entry.getValue());
                lore.add(ChatColor.DARK_GRAY + "This will be split during payout.");
                meta.setLore(lore);
                display.setItemMeta(meta);
            }
            inv.addItem(display);
        }

        inv.setItem(45, navItem(Material.ARROW, ChatColor.YELLOW + "Previous Page"));
        inv.setItem(49, navItem(Material.BARRIER, ChatColor.RED + "Close"));
        inv.setItem(53, navItem(Material.ARROW, ChatColor.YELLOW + "Next Page"));
        return inv;
    }

    private ItemStack navItem(Material material, String name) {
        ItemStack stack = new ItemStack(material);
        ItemMeta meta = stack.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(name);
            stack.setItemMeta(meta);
        }
        return stack;
    }

    private boolean isStatusInventory(String title) {
        return title != null && title.startsWith(ChatColor.DARK_RED + "State Payout");
    }

    private static class StatusSession {
        private final List<Map.Entry<Material, Integer>> entries;
        private int page;

        private StatusSession(List<Map.Entry<Material, Integer>> entries) {
            this.entries = entries;
        }

        private int totalPages() {
            return (int) Math.max(1, Math.ceil(entries.size() / (double) ITEMS_PER_PAGE));
        }
    }
}
