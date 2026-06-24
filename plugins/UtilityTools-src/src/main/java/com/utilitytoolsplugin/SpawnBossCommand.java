package com.utilitytoolsplugin;

import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import org.bukkit.entity.Skeleton;
import org.bukkit.entity.Zombie;

import java.util.ArrayList;
import java.util.List;

public class SpawnBossCommand implements CommandExecutor, TabCompleter {

    private final UtilityToolsPlugin plugin;

    public SpawnBossCommand(UtilityToolsPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(ChatColor.RED + "Lệnh này chỉ dành cho người chơi!");
            return true;
        }

        if (!player.hasPermission("utilitytoolsplugin.spawnboss") && !player.isOp()) {
            player.sendMessage(ChatColor.RED + "Bạn không có quyền dùng lệnh này!");
            return true;
        }

        Location loc = player.getLocation();
        if (args.length == 0) {
            // Nếu chỉ gõ /spawn mà không ghi rõ ske hay zombie -> Triệu hồi cả 2 Boss cùng lúc!
            Skeleton skeleton = (Skeleton) loc.getWorld().spawnEntity(loc, EntityType.SKELETON);
            plugin.getBossSkeletonManager().makeBossSkeleton(skeleton);

            Zombie zombie = (Zombie) loc.getWorld().spawnEntity(loc, EntityType.ZOMBIE);
            plugin.getBossZombieManager().makeBossZombie(zombie);

            player.sendMessage(ChatColor.GREEN + "Đã triệu hồi đồng thời §4§l☠ Quỷ Vương Xương ☠§a và §4§l☠ Quỷ Vương Zombie ☠§a thành công!");
            player.sendMessage(ChatColor.YELLOW + "Mẹo: Bạn có thể gõ cụ thể /spawn ske hoặc /spawn zombie để gọi từng con.");
            return true;
        }

        String type = args[0].toLowerCase();

        if (type.equalsIgnoreCase("ske") || type.equalsIgnoreCase("skeleton")) {
            Skeleton skeleton = (Skeleton) loc.getWorld().spawnEntity(loc, EntityType.SKELETON);
            plugin.getBossSkeletonManager().makeBossSkeleton(skeleton);
            player.sendMessage(ChatColor.GREEN + "Đã triệu hồi §4§l☠ Quỷ Vương Xương ☠§a thành công!");
            return true;
        } else if (type.equalsIgnoreCase("zombie")) {
            Zombie zombie = (Zombie) loc.getWorld().spawnEntity(loc, EntityType.ZOMBIE);
            plugin.getBossZombieManager().makeBossZombie(zombie);
            player.sendMessage(ChatColor.GREEN + "Đã triệu hồi §4§l☠ Quỷ Vương Zombie ☠§a thành công!");
            return true;
        } else {
            player.sendMessage(ChatColor.RED + "Loại boss không hợp lệ! Hãy chọn 'ske' hoặc 'zombie'.");
            return true;
        }
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        List<String> suggestions = new ArrayList<>();
        if (!sender.hasPermission("utilitytoolsplugin.spawnboss") && !sender.isOp()) {
            return suggestions;
        }

        if (args.length == 1) {
            if ("ske".startsWith(args[0].toLowerCase())) {
                suggestions.add("ske");
            }
            if ("zombie".startsWith(args[0].toLowerCase())) {
                suggestions.add("zombie");
            }
        }
        return suggestions;
    }
}
