package com.utilitytoolsplugin;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.Plugin;
import java.util.HashMap;

public class SellManager implements CommandExecutor, Listener {

    private final EconomyManager economyManager;
    private final String guiTitle = ChatColor.GREEN + "Bỏ đồ vào đây để bán";

    public SellManager(EconomyManager economyManager) {
        this.economyManager = economyManager;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player)) return true;
        Player player = (Player) sender;
        Inventory inv = Bukkit.createInventory(null, 54, guiTitle);
        player.openInventory(inv);
        return true;
    }

    @EventHandler
    public void onInventoryClose(InventoryCloseEvent event) {
        if (!event.getView().getTitle().equals(guiTitle)) return;
        
        Player player = (Player) event.getPlayer();
        Inventory inv = event.getInventory();
        
        double totalEarned = 0;
        boolean hasSpawnerItem = false;
        
        for (int i = 0; i < inv.getSize(); i++) {
            ItemStack item = inv.getItem(i);
            if (item != null && item.getType() != Material.AIR) {
                if (isSpawnerItem(item)) {
                    hasSpawnerItem = true;
                    HashMap<Integer, ItemStack> leftOver = player.getInventory().addItem(item);
                    for (ItemStack left : leftOver.values()) {
                        player.getWorld().dropItemNaturally(player.getLocation(), left);
                    }
                    inv.setItem(i, null);
                    continue;
                }
                double unitPrice = getUnitPrice(item);
                totalEarned += unitPrice * item.getAmount();
                inv.setItem(i, null); // Xóa đồ vì đã bán
            }
        }
        
        if (hasSpawnerItem) {
            player.sendMessage("§cVật phẩm từ lồng không thể bán lại cho server! Đã hoàn trả vào túi của bạn.");
        }
        
        if (totalEarned > 0) {
            economyManager.addBalance(player, totalEarned);
            player.sendMessage("§aBạn đã bán các vật phẩm và nhận được §f" + String.format("%,.1f", totalEarned) + " Tiền!");
        } else if (!hasSpawnerItem) {
            player.sendMessage("§eBạn không bán gì cả.");
        }
    }

    private boolean isSpawnerItem(ItemStack item) {
        if (item == null || !item.hasItemMeta()) return false;
        ItemMeta meta = item.getItemMeta();
        if (meta == null) return false;
        return meta.getPersistentDataContainer().has(ItemSpawnerManager.SPAWNER_ITEM_KEY, PersistentDataType.BYTE);
    }


    private double getUnitPrice(ItemStack item) {
        Material type = item.getType();
        
        if (type == Material.DRIED_KELP_BLOCK) {
            return 500.0 / 64.0; // 500 cho 1 lốc (64)
        }
        if (type == Material.DIAMOND || type == Material.DIAMOND_BLOCK) return 500.0;
        if (type == Material.NETHERITE_INGOT || type == Material.NETHERITE_BLOCK || type == Material.NETHERITE_SCRAP) return 1000.0;
        if (type == Material.GOLD_INGOT || type == Material.GOLD_BLOCK) return 150.0;
        if (type == Material.IRON_INGOT || type == Material.IRON_BLOCK) return 100.0;
        if (type == Material.EMERALD || type == Material.EMERALD_BLOCK) return 300.0;
        if (type == Material.LAPIS_LAZULI || type == Material.REDSTONE) return 50.0;
        
        // Random cố định cho các item khác từ 20 đến 1000
        int hash = Math.abs(type.name().hashCode());
        // Cho phần lớn đồ rác giá thấp, đồ hiếm giá cao
        double price = 20.0 + (hash % 100); 
        
        if (price > 1000) price = 1000;
        return price;
    }
}
