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
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.NamespacedKey;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.Plugin;
import org.bukkit.enchantments.Enchantment;

import java.util.Arrays;
import java.util.ArrayList;
import java.util.List;

public class ShopManager implements CommandExecutor, Listener {

    private final Plugin plugin;
    private final EconomyManager economyManager;
    private final String guiTitle = ChatColor.GOLD + "Cửa Hàng (Shop)";

    // Pre-create NameSpacedKey for custom spawners
    public static NamespacedKey SPAWNER_TYPE_KEY;

    public ShopManager(Plugin plugin, EconomyManager economyManager) {
        this.plugin = plugin;
        this.economyManager = economyManager;
        SPAWNER_TYPE_KEY = new NamespacedKey(plugin, "spawner_type");
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player)) return true;
        Player player = (Player) sender;
        openShop(player);
        return true;
    }

    private void openShop(Player player) {
        Inventory inv = Bukkit.createInventory(null, 27, guiTitle);

        inv.setItem(11, createShopItem(Material.TOTEM_OF_UNDYING, "§eTotem Hồi Sinh", 500, null, 1));
        inv.setItem(12, createShopItem(Material.ENDER_PEARL, "§5Ngọc Ender", 120, null, 1));
        inv.setItem(13, createShopItem(Material.BLAZE_ROD, "§6Que Quỷ Lửa", 30, null, 64));
        inv.setItem(14, createShopItem(Material.SPAWNER, "§fLồng Skeleton (Rương)", 100, "skeleton", 1));
        inv.setItem(15, createShopItem(Material.SPAWNER, "§cLồng Quỷ Lửa (Rương)", 100, "blaze", 1));
        inv.setItem(16, createShopItem(Material.SPAWNER, "§7Lồng Người Sắt (Rương)", 150, "iron_golem", 1));
        inv.setItem(17, createShopItem(Material.ENCHANTED_GOLDEN_APPLE, "§d🍎 Táo Vàng Phù Phép", 300, null, 64));
        inv.setItem(18, createShopItem(Material.EXPERIENCE_BOTTLE, "§aChai Kinh Nghiệm", 200, null, 64));
        inv.setItem(19, createShopItem(Material.OBSIDIAN, "§5Hắc Diện Thạch", 100, null, 64));
        inv.setItem(20, createShopItem(Material.ENDER_CHEST, "§5Rương Ender", 1000, null, 1));
        inv.setItem(21, createShopItem(Material.FIREWORK_ROCKET, "§bPháo Hoa", 500, null, 64));
        
        ItemStack elytra = createShopItem(Material.ELYTRA, "§dCánh Phù Phép", 100000, null, 1);
        elytra.addUnsafeEnchantment(Enchantment.MENDING, 1);
        elytra.addUnsafeEnchantment(Enchantment.UNBREAKING, 3);
        inv.setItem(22, elytra);
        
        inv.setItem(23, createShopItem(Material.ARROW, "§7Mũi Tên", 500, null, 64));
        inv.setItem(24, createShopItem(Material.SHULKER_BOX, "§dHộp Shulker", 1500, null, 1));

        player.openInventory(inv);
    }

    private ItemStack createShopItem(Material mat, String name, double price, String spawnerType, int amount) {
        ItemStack item = new ItemStack(mat, amount);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(name);
            List<String> lore = new java.util.ArrayList<>();
            lore.add("§aGiá: §f" + price + " Tiền");
            lore.add("§7Click để mua " + amount + " cái");
            if (spawnerType != null) {
                lore.add("§7Click phải khi đặt để mở rương.");
                lore.add("§7Đặt nhiều lồng cùng loại để gộp!");
            }
            meta.setLore(lore);
            if (spawnerType != null) {
                meta.getPersistentDataContainer().set(SPAWNER_TYPE_KEY, PersistentDataType.STRING, spawnerType);
            }
            item.setItemMeta(meta);
        }
        return item;
    }

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        if (!event.getView().getTitle().equals(guiTitle)) return;
        event.setCancelled(true); // Ngăn lấy item ra khỏi GUI

        if (!(event.getWhoClicked() instanceof Player)) return;
        Player player = (Player) event.getWhoClicked();

        ItemStack clicked = event.getCurrentItem();
        if (clicked == null || clicked.getType() == Material.AIR) return;

        double price = 0;
        switch (event.getRawSlot()) {
            case 11: price = 500; break;
            case 12: price = 120; break;
            case 13: price = 30; break;
            case 14: price = 100; break;
            case 15: price = 100; break;
            case 16: price = 150; break;
            case 17: price = 300; break;
            case 18: price = 200; break;
            case 19: price = 100; break;
            case 20: price = 1000; break;
            case 21: price = 500; break;
            case 22: price = 100000; break;
            case 23: price = 500; break;
            case 24: price = 1500; break;
            default: return; // Click ra ngoài vùng
        }

        if (economyManager.hasEnough(player, price)) {
            economyManager.removeBalance(player, price);
            
            ItemStack buyItem = clicked.clone();
            ItemMeta meta = buyItem.getItemMeta();
            if (meta != null) {
                meta.setLore(null); // Xóa dòng chữ giá tiền
                buyItem.setItemMeta(meta);
            }
            
            player.getInventory().addItem(buyItem);
            player.sendMessage("§aBạn đã mua thành công " + buyItem.getItemMeta().getDisplayName() + " §avới giá §f" + price + " Tiền!");
        } else {
            player.sendMessage("§cBạn không đủ tiền! Cần §f" + price + " Tiền.");
        }
    }
}
