package com.daytonjwatson.communism.gui;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

public class PayoutViewer implements Listener {

    private static final int INVENTORY_SIZE = 54;
    private static final int ITEMS_PER_PAGE = 45;

    private final Map<UUID, ViewerSession> sessions;

    public PayoutViewer() {
        this.sessions = new HashMap<>();
    }

    public void openViewer(Player player, List<ItemStack> items) {
        if (items == null || items.isEmpty()) return;

        ViewerSession session = new ViewerSession(new ArrayList<>(items));
        sessions.put(player.getUniqueId(), session);
        player.openInventory(buildInventory(session, 0));
    }

    private Inventory buildInventory(ViewerSession session, int page) {
        int totalPages = session.getTotalPages();
        page = Math.max(0, Math.min(page, totalPages - 1));
        session.setCurrentPage(page);

        String title = ChatColor.DARK_RED + "Payout " + ChatColor.GRAY + "(Page " + (page + 1) + "/" + totalPages + ")";
        Inventory inv = Bukkit.createInventory(new PayoutHolder(), INVENTORY_SIZE, title);

        int start = page * ITEMS_PER_PAGE;
        int end = Math.min(start + ITEMS_PER_PAGE, session.getItems().size());

        for (int i = start; i < end; i++) {
            inv.setItem(i - start, session.getItems().get(i));
        }

        if (page > 0) {
            inv.setItem(45, navigationItem(Material.ARROW, ChatColor.YELLOW + "Previous Page"));
        }

        inv.setItem(49, navigationItem(Material.BARRIER, ChatColor.RED + "Close"));

        if (page < totalPages - 1) {
            inv.setItem(53, navigationItem(Material.ARROW, ChatColor.YELLOW + "Next Page"));
        }

        return inv;
    }

    private ItemStack navigationItem(Material material, String name) {
        ItemStack stack = new ItemStack(material);
        ItemMeta meta = stack.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(name);
            stack.setItemMeta(meta);
        }
        return stack;
    }

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getInventory().getHolder() instanceof PayoutHolder)) return;

        event.setCancelled(true);
        if (!(event.getWhoClicked() instanceof Player)) return;

        Player player = (Player) event.getWhoClicked();
        ViewerSession session = sessions.get(player.getUniqueId());
        if (session == null) return;

        int rawSlot = event.getRawSlot();
        if (rawSlot == 45 && session.getCurrentPage() > 0) {
            player.openInventory(buildInventory(session, session.getCurrentPage() - 1));
        } else if (rawSlot == 53 && session.getCurrentPage() < session.getTotalPages() - 1) {
            player.openInventory(buildInventory(session, session.getCurrentPage() + 1));
        } else if (rawSlot == 49) {
            player.closeInventory();
        }
    }

    @EventHandler
    public void onInventoryDrag(InventoryDragEvent event) {
        if (event.getInventory().getHolder() instanceof PayoutHolder) {
            event.setCancelled(true);
        }
    }

    public void clearSession(Player player) {
        sessions.remove(player.getUniqueId());
    }

    private static class ViewerSession {
        private final List<ItemStack> items;
        private int currentPage;

        ViewerSession(List<ItemStack> items) {
            this.items = items;
            this.currentPage = 0;
        }

        List<ItemStack> getItems() {
            return items;
        }

        int getTotalPages() {
            return Math.max(1, (int) Math.ceil(items.size() / (double) ITEMS_PER_PAGE));
        }

        int getCurrentPage() {
            return currentPage;
        }

        void setCurrentPage(int currentPage) {
            this.currentPage = currentPage;
        }
    }

    private static class PayoutHolder implements InventoryHolder {
        @Override
        public Inventory getInventory() {
            return null;
        }
    }
}
