package com.utilitytoolsplugin;

import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;

public class PayCommand implements CommandExecutor, TabCompleter {

    private final EconomyManager economyManager;

    public PayCommand(EconomyManager economyManager) {
        this.economyManager = economyManager;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player)) {
            sender.sendMessage("§cChỉ người chơi mới có thể dùng lệnh này!");
            return true;
        }
        Player player = (Player) sender;

        if (args.length < 2) {
            player.sendMessage("§cCách sử dụng: /pay <tên_người_chơi> <số_tiền>");
            return true;
        }

        Player target = Bukkit.getPlayer(args[0]);
        if (target == null) {
            player.sendMessage("§cKhông tìm thấy người chơi §e" + args[0] + " §choặc họ đang offline!");
            return true;
        }

        if (target.getUniqueId().equals(player.getUniqueId())) {
            player.sendMessage("§cBạn không thể chuyển tiền cho chính mình!");
            return true;
        }

        double amount;
        try {
            amount = Double.parseDouble(args[1]);
        } catch (NumberFormatException e) {
            player.sendMessage("§cSố tiền không hợp lệ: §e" + args[1]);
            return true;
        }

        if (amount <= 0) {
            player.sendMessage("§cSố tiền chuyển phải lớn hơn 0!");
            return true;
        }

        if (!economyManager.hasEnough(player, amount)) {
            player.sendMessage("§cBạn không đủ tiền! Bạn hiện có §f" + String.format("%,.1f", economyManager.getBalance(player)) + " Tiền.");
            return true;
        }

        // Thực hiện chuyển tiền
        if (economyManager.removeBalance(player, amount)) {
            economyManager.addBalance(target, amount);
            player.sendMessage("§aBạn đã chuyển thành công §f" + String.format("%,.1f", amount) + " Tiền §acho §e" + target.getName() + "§a!");
            target.sendMessage("§aBạn đã nhận được §f" + String.format("%,.1f", amount) + " Tiền §atừ §e" + player.getName() + "§a!");
        } else {
            player.sendMessage("§cĐã xảy ra lỗi khi trừ tiền của bạn!");
        }

        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        List<String> completions = new ArrayList<>();
        if (args.length == 1) {
            for (Player p : Bukkit.getOnlinePlayers()) {
                if (p.getName().toLowerCase().startsWith(args[0].toLowerCase())) {
                    completions.add(p.getName());
                }
            }
        } else if (args.length == 2) {
            if (args[1].isEmpty()) {
                completions.add("100");
                completions.add("500");
                completions.add("1000");
                completions.add("5000");
                completions.add("10000");
            }
        }
        return completions;
    }
}
