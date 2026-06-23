package com.utilitytoolsplugin;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

import java.io.File;
import java.io.IOException;
import java.util.*;

public class HomeManager implements CommandExecutor, TabCompleter {

    private final Plugin plugin;
    private final File dataFile;
    private FileConfiguration dataConfig;

    // Giới hạn số home tối đa mỗi người
    private static final int MAX_HOMES = 3;

    // Cache: UUID -> (tên home -> Location)
    private final Map<UUID, Map<String, Location>> homes = new HashMap<>();

    public HomeManager(Plugin plugin) {
        this.plugin = plugin;
        this.dataFile = new File(plugin.getDataFolder(), "homes.yml");
        loadData();
    }

    // ===================== LOAD / SAVE =====================

    private void loadData() {
        if (!dataFile.exists()) {
            try {
                dataFile.getParentFile().mkdirs();
                dataFile.createNewFile();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
        dataConfig = YamlConfiguration.loadConfiguration(dataFile);
        homes.clear();

        if (!dataConfig.contains("homes")) return;

        for (String uuidStr : dataConfig.getConfigurationSection("homes").getKeys(false)) {
            UUID uuid;
            try { uuid = UUID.fromString(uuidStr); }
            catch (IllegalArgumentException e) { continue; }

            Map<String, Location> playerHomes = new LinkedHashMap<>();
            String basePath = "homes." + uuidStr;

            if (!dataConfig.contains(basePath)) continue;
            for (String homeName : dataConfig.getConfigurationSection(basePath).getKeys(false)) {
                String path = basePath + "." + homeName;
                String worldName = dataConfig.getString(path + ".world");
                double x = dataConfig.getDouble(path + ".x");
                double y = dataConfig.getDouble(path + ".y");
                double z = dataConfig.getDouble(path + ".z");
                float yaw   = (float) dataConfig.getDouble(path + ".yaw");
                float pitch = (float) dataConfig.getDouble(path + ".pitch");

                World world = Bukkit.getWorld(worldName);
                if (world == null) continue;

                playerHomes.put(homeName.toLowerCase(), new Location(world, x, y, z, yaw, pitch));
            }
            homes.put(uuid, playerHomes);
        }
    }

    private void saveData() {
        dataConfig.set("homes", null);
        for (Map.Entry<UUID, Map<String, Location>> entry : homes.entrySet()) {
            String uuidStr = entry.getKey().toString();
            for (Map.Entry<String, Location> homeEntry : entry.getValue().entrySet()) {
                String path = "homes." + uuidStr + "." + homeEntry.getKey();
                Location loc = homeEntry.getValue();
                dataConfig.set(path + ".world", loc.getWorld().getName());
                dataConfig.set(path + ".x", loc.getX());
                dataConfig.set(path + ".y", loc.getY());
                dataConfig.set(path + ".z", loc.getZ());
                dataConfig.set(path + ".yaw",   (double) loc.getYaw());
                dataConfig.set(path + ".pitch", (double) loc.getPitch());
            }
        }
        try {
            dataConfig.save(dataFile);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    // ===================== LỆNH =====================

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player)) {
            sender.sendMessage("§cChỉ người chơi mới dùng được lệnh này!");
            return true;
        }
        Player player = (Player) sender;
        String cmd = command.getName().toLowerCase();

        switch (cmd) {
            case "sethome": return handleSetHome(player, args);
            case "home":    return handleHome(player, args);
            case "delhome": return handleDelHome(player, args);
            default: return false;
        }
    }

    // ---------- /sethome <tên> ----------
    private boolean handleSetHome(Player player, String[] args) {
        if (args.length == 0) {
            player.sendMessage("§cCách dùng: §f/sethome <tên>");
            return true;
        }

        String name = args[0].toLowerCase();

        // Kiểm tra ký tự hợp lệ
        if (!name.matches("[a-z0-9_-]+")) {
            player.sendMessage("§cTên home chỉ được dùng chữ thường, số, dấu _ và -!");
            return true;
        }

        Map<String, Location> playerHomes = homes.computeIfAbsent(player.getUniqueId(), k -> new LinkedHashMap<>());

        // Nếu home tên này chưa tồn tại → kiểm tra giới hạn
        if (!playerHomes.containsKey(name) && playerHomes.size() >= MAX_HOMES) {
            player.sendMessage("§c❌ Bạn đã đạt giới hạn §e" + MAX_HOMES + " home§c! Hãy xóa bớt bằng §f/delhome <tên>§c.");
            listHomes(player, playerHomes);
            return true;
        }

        playerHomes.put(name, player.getLocation().clone());
        saveData();

        player.sendMessage("§a✔ Đã đặt home §e\"" + name + "\"§a tại §7("
                + "§e" + (int) player.getLocation().getX()
                + "§7, §e" + (int) player.getLocation().getY()
                + "§7, §e" + (int) player.getLocation().getZ()
                + "§7) §atrong §f" + player.getWorld().getName() + "§a.");
        player.sendMessage("§7Số home: §e" + playerHomes.size() + "§7/§e" + MAX_HOMES);
        return true;
    }

    // ---------- /home <tên> ----------
    private boolean handleHome(Player player, String[] args) {
        Map<String, Location> playerHomes = homes.get(player.getUniqueId());

        if (playerHomes == null || playerHomes.isEmpty()) {
            player.sendMessage("§cBạn chưa có home nào! Dùng §f/sethome <tên>§c để tạo.");
            return true;
        }

        if (args.length == 0) {
            // Nếu chỉ có 1 home thì tp thẳng
            if (playerHomes.size() == 1) {
                Map.Entry<String, Location> only = playerHomes.entrySet().iterator().next();
                teleportHome(player, only.getKey(), only.getValue());
            } else {
                player.sendMessage("§cCách dùng: §f/home <tên>§c. Danh sách home của bạn:");
                listHomes(player, playerHomes);
            }
            return true;
        }

        String name = args[0].toLowerCase();
        Location loc = playerHomes.get(name);
        if (loc == null) {
            player.sendMessage("§cKhông tìm thấy home §e\"" + name + "\"§c.");
            listHomes(player, playerHomes);
            return true;
        }

        teleportHome(player, name, loc);
        return true;
    }

    // ---------- /delhome <tên> ----------
    private boolean handleDelHome(Player player, String[] args) {
        if (args.length == 0) {
            player.sendMessage("§cCách dùng: §f/delhome <tên>");
            return true;
        }

        String name = args[0].toLowerCase();
        Map<String, Location> playerHomes = homes.get(player.getUniqueId());

        if (playerHomes == null || !playerHomes.containsKey(name)) {
            player.sendMessage("§cKhông tìm thấy home §e\"" + name + "\"§c.");
            if (playerHomes != null) listHomes(player, playerHomes);
            return true;
        }

        playerHomes.remove(name);
        if (playerHomes.isEmpty()) homes.remove(player.getUniqueId());
        saveData();

        player.sendMessage("§a✔ Đã xóa home §e\"" + name + "\"§a thành công.");
        if (!playerHomes.isEmpty()) {
            player.sendMessage("§7Số home còn lại: §e" + playerHomes.size() + "§7/§e" + MAX_HOMES);
        }
        return true;
    }

    // ===================== HELPER =====================

    private void teleportHome(Player player, String name, Location loc) {
        if (loc.getWorld() == null) {
            player.sendMessage("§cWorld của home §e\"" + name + "\" §ckhông tồn tại!");
            return;
        }
        player.teleport(loc);
        player.sendMessage("§a✔ Đã teleport về home §e\"" + name + "\"§a! §7("
                + "§e" + (int) loc.getX()
                + "§7, §e" + (int) loc.getY()
                + "§7, §e" + (int) loc.getZ()
                + "§7) - §f" + loc.getWorld().getName());
    }

    private void listHomes(Player player, Map<String, Location> playerHomes) {
        if (playerHomes == null || playerHomes.isEmpty()) {
            player.sendMessage("§7Bạn chưa có home nào.");
            return;
        }
        StringBuilder sb = new StringBuilder("§7Danh sách home §7(§e" + playerHomes.size() + "§7/§e" + MAX_HOMES + "§7): ");
        List<String> names = new ArrayList<>(playerHomes.keySet());
        for (int i = 0; i < names.size(); i++) {
            sb.append("§e").append(names.get(i));
            if (i < names.size() - 1) sb.append("§7, ");
        }
        player.sendMessage(sb.toString());
    }

    // ===================== TAB COMPLETE =====================

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (!(sender instanceof Player) || args.length != 1) return Collections.emptyList();
        Player player = (Player) sender;
        String cmd = command.getName().toLowerCase();

        // Tab complete cho /home và /delhome → gợi ý tên home của player
        if (cmd.equals("home") || cmd.equals("delhome")) {
            Map<String, Location> playerHomes = homes.get(player.getUniqueId());
            if (playerHomes == null) return Collections.emptyList();
            List<String> result = new ArrayList<>();
            String input = args[0].toLowerCase();
            for (String name : playerHomes.keySet()) {
                if (name.startsWith(input)) result.add(name);
            }
            return result;
        }
        return Collections.emptyList();
    }
}
