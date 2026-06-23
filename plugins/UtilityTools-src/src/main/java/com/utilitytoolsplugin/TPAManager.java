package com.utilitytoolsplugin;

import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

import java.util.ArrayList;
import java.util.List;

public class TPAManager implements CommandExecutor, TabCompleter {

    private final Plugin plugin;

    public TPAManager(Plugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("§cChỉ người chơi mới có thể dùng lệnh này.");
            return true;
        }

        if (args.length == 0) {
            player.sendMessage("§cSử dụng: /tpa <tên_người_chơi>");
            return true;
        }

        String targetName = args[0];
        // getPlayer sẽ tự động tìm người chơi có tên tương tự (partial match)
        Player target = Bukkit.getPlayer(targetName);

        if (target == null || !target.isOnline()) {
            player.sendMessage("§cKhông tìm thấy người chơi nào có tên tương tự hoặc họ không online.");
            return true;
        }

        if (target.equals(player)) {
            player.sendMessage("§cBạn không thể tự dịch chuyển đến chính mình.");
            return true;
        }

        player.teleport(target.getLocation());
        player.sendMessage("§aĐã dịch chuyển đến §e" + target.getName() + "§a!");
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        List<String> completions = new ArrayList<>();
        if (args.length == 1) {
            String partialName = args[0].toLowerCase();
            for (Player p : Bukkit.getOnlinePlayers()) {
                if (p.getName().toLowerCase().startsWith(partialName)) {
                    completions.add(p.getName());
                }
            }
        }
        return completions;
    }
}
