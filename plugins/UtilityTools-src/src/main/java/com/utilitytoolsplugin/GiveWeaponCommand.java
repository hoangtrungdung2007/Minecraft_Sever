package com.utilitytoolsplugin;

import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

public class GiveWeaponCommand implements CommandExecutor {

    private final UtilityToolsPlugin plugin;
    private final NamespacedKey MACE_KEY;
    private final NamespacedKey EXPLOSIVE_BOW_KEY;

    public GiveWeaponCommand(UtilityToolsPlugin plugin) {
        this.plugin = plugin;
        this.MACE_KEY = new NamespacedKey(plugin, "mace_weapon");
        this.EXPLOSIVE_BOW_KEY = new NamespacedKey(plugin, "explosive_bow");
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("§cChỉ người chơi mới dùng được lệnh này!");
            return true;
        }

        if (!player.hasPermission("utilitytoolsplugin.giveweapon")) {
            player.sendMessage("§cBạn không có quyền sử dụng lệnh này!");
            return true;
        }

        if (command.getName().equalsIgnoreCase("givemace")) {
            ItemStack mace = new ItemStack(Material.MACE);
            ItemMeta maceMeta = mace.getItemMeta();
            if (maceMeta != null) {
                maceMeta.setDisplayName("§4§lChùy Quỷ Dữ");
                maceMeta.addEnchant(Enchantment.KNOCKBACK, 2, true);
                maceMeta.getPersistentDataContainer().set(MACE_KEY, PersistentDataType.BYTE, (byte) 1);
                mace.setItemMeta(maceMeta);
            }
            player.getInventory().addItem(mace);
            player.sendMessage("§aBạn đã nhận được §4§lChùy Quỷ Dữ§a!");
            return true;
        }

        if (command.getName().equalsIgnoreCase("givebow")) {
            ItemStack bow = new ItemStack(Material.BOW);
            ItemMeta bowMeta = bow.getItemMeta();
            if (bowMeta != null) {
                bowMeta.setDisplayName("§c§lCung Hủy Diệt");
                bowMeta.addEnchant(Enchantment.POWER, 5, true);
                bowMeta.addEnchant(Enchantment.FLAME, 1, true);
                bowMeta.getPersistentDataContainer().set(EXPLOSIVE_BOW_KEY, PersistentDataType.BYTE, (byte) 1);
                bow.setItemMeta(bowMeta);
            }
            player.getInventory().addItem(bow);
            player.sendMessage("§aBạn đã nhận được §c§lCung Hủy Diệt§a!");
            
            // Give 1 stack of arrows
            player.getInventory().addItem(new ItemStack(Material.ARROW, 64));
            return true;
        }

        return false;
    }
}
