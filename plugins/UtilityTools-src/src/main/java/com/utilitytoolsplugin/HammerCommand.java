package com.utilitytoolsplugin;

import org.bukkit.Material;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

/**
 * Command /hammer - Cho người chơi nhận Hammer
 * 
 * Usage:
 * /hammer give [player]       - Cho Hammer kim cương
 * /hammer give [player] iron  - Cho Hammer sắt
 * /hammer give [player] netherite - Cho Hammer netherite
 * /hammer info                - Xem hướng dẫn sử dụng
 */
public class HammerCommand implements CommandExecutor {

    private final UtilityToolsPlugin plugin;

    public HammerCommand(UtilityToolsPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length == 0) {
            sendHelp(sender);
            return true;
        }
        
        switch (args[0].toLowerCase()) {
            case "give" -> {
                return handleGive(sender, args);
            }
            case "info" -> {
                sendInfo(sender);
                return true;
            }
            default -> {
                sendHelp(sender);
                return true;
            }
        }
    }
    
    private boolean handleGive(CommandSender sender, String[] args) {
        Player target = null;
        Material hammerMaterial = Material.DIAMOND_PICKAXE; // default
        
        if (args.length >= 2) {
            target = plugin.getServer().getPlayer(args[1]);
            if (target == null) {
                sender.sendMessage("§cKhông tìm thấy người chơi: " + args[1]);
                return true;
            }
        } else if (sender instanceof Player p) {
            target = p;
        } else {
            sender.sendMessage("§cBạn phải chỉ định tên người chơi khi dùng từ console!");
            return true;
        }
        
        if (args.length >= 3) {
            hammerMaterial = switch (args[2].toLowerCase()) {
                case "wood", "wooden" -> Material.WOODEN_PICKAXE;
                case "stone" -> Material.STONE_PICKAXE;
                case "iron" -> Material.IRON_PICKAXE;
                case "gold", "golden" -> Material.GOLDEN_PICKAXE;
                case "diamond" -> Material.DIAMOND_PICKAXE;
                case "netherite" -> Material.NETHERITE_PICKAXE;
                default -> Material.DIAMOND_PICKAXE;
            };
        }
        
        // Tạo hammer
        ItemStack hammer = createHammer(hammerMaterial);

        // Cho vào inventory
        target.getInventory().addItem(hammer);
        
        target.sendMessage("§a✔ Bạn đã nhận được §6§lHammer§a! Dùng cuốc đặc biệt này để phá 3x3 block.");
        if (!sender.equals(target)) {
            sender.sendMessage("§a✔ Đã cho §f" + target.getName() + " §amột Hammer!");
        }
        
        return true;
    }
    
    private ItemStack createHammer(Material pickaxeType) {
        ItemStack hammer = new ItemStack(pickaxeType);
        org.bukkit.inventory.meta.ItemMeta meta = hammer.getItemMeta();
        
        if (meta != null) {
            meta.setDisplayName("§6§lHammer §r§6(3x3)");
            java.util.List<String> lore = new java.util.ArrayList<>();
            lore.add("§7Phá §f3x3 §7block cùng lúc");
            lore.add("§7Tiêu hao §fđộ bền §7cho mỗi block");
            lore.add("§7Hoạt động với §fđá, đất và quặng");
            lore.add("§8[UtilityTools Plugin]");
            meta.setLore(lore);
            
            org.bukkit.NamespacedKey key = new org.bukkit.NamespacedKey(plugin, "is_hammer");
            meta.getPersistentDataContainer().set(key, 
                org.bukkit.persistence.PersistentDataType.BYTE, (byte) 1);
            
            hammer.setItemMeta(meta);
        }
        return hammer;
    }
    
    private void sendHelp(CommandSender sender) {
        sender.sendMessage("§6§l=== UtilityTools - Hammer ===");
        sender.sendMessage("§e/hammer give §7[player] [material] §f- Cho Hammer");
        sender.sendMessage("§e/hammer info §f- Xem hướng dẫn");
        sender.sendMessage("§7Material: wood, stone, iron, gold, diamond, netherite");
    }
    
    private void sendInfo(CommandSender sender) {
        sender.sendMessage("§6§l=== Hammer 3x3 - Hướng Dẫn ===");
        sender.sendMessage("§7• Cầm Hammer và đào bất kỳ block đá/đất/quặng");
        sender.sendMessage("§7• Tự động phá §f3x3 block §7vuông góc với hướng nhìn");
        sender.sendMessage("§7• Tiêu hao §f1 độ bền §7cho mỗi block bị phá");
        sender.sendMessage("§7• Nhận Hammer bằng lệnh §f/hammer give");
        sender.sendMessage("§7• Hoặc đặt tên cuốc là §f\"Hammer\" §7trong Anvil");
    }
}
