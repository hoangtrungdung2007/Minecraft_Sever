package com.utilitytoolsplugin;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.OfflinePlayer;
import org.bukkit.Statistic;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.SkullMeta;
import org.bukkit.plugin.Plugin;

import java.io.File;
import java.io.IOException;
import java.text.DecimalFormat;
import java.util.*;

public class StatsManager implements CommandExecutor, TabCompleter, Listener {

    private final Plugin plugin;
    private final EconomyManager economyManager;
    private final File dataFile;
    private FileConfiguration dataConfig;

    // Cache: UUID -> StatsData
    private final Map<UUID, StatsData> statsMap = new HashMap<>();

    // GUI title
    private static final String GUI_TITLE_PREFIX = "§5§l✦ §dStats: §f";

    // Formatter tiền
    private static final DecimalFormat MONEY_FMT = new DecimalFormat("#,###.##");

    // ---- Inner class ----
    private static class StatsData {
        int kills   = 0;
        int deaths  = 0;
        long playTimeMillis = 0;      // Tổng thời gian chơi (ms)
        long sessionStart   = 0;      // Thời điểm bắt đầu session hiện tại (ms), 0 = offline

        StatsData() {}
    }

    // ===================== CONSTRUCTOR =====================

    public StatsManager(Plugin plugin, EconomyManager economyManager) {
        this.plugin = plugin;
        this.economyManager = economyManager;
        this.dataFile = new File(plugin.getDataFolder(), "stats.yml");
        loadData();

        // Ghi nhận session start cho những player đang online khi plugin reload
        for (Player p : Bukkit.getOnlinePlayers()) {
            getOrCreate(p.getUniqueId()).sessionStart = System.currentTimeMillis();
        }
    }

    // ===================== LOAD / SAVE =====================

    private void loadData() {
        if (!dataFile.exists()) {
            try {
                dataFile.getParentFile().mkdirs();
                dataFile.createNewFile();
            } catch (IOException e) { e.printStackTrace(); }
        }
        dataConfig = YamlConfiguration.loadConfiguration(dataFile);
        statsMap.clear();

        if (!dataConfig.contains("stats")) return;
        for (String uuidStr : dataConfig.getConfigurationSection("stats").getKeys(false)) {
            UUID uuid;
            try { uuid = UUID.fromString(uuidStr); } catch (Exception e) { continue; }
            String path = "stats." + uuidStr;
            StatsData d = new StatsData();
            d.kills         = dataConfig.getInt(path + ".kills", 0);
            d.deaths        = dataConfig.getInt(path + ".deaths", 0);
            d.playTimeMillis = dataConfig.getLong(path + ".playtime", 0);
            statsMap.put(uuid, d);
        }
    }

    public void saveData() {
        // Flush session time cho người đang online
        for (Player p : Bukkit.getOnlinePlayers()) {
            StatsData d = getOrCreate(p.getUniqueId());
            if (d.sessionStart > 0) {
                d.playTimeMillis += System.currentTimeMillis() - d.sessionStart;
                d.sessionStart = System.currentTimeMillis(); // reset để không tính kép
            }
        }
        dataConfig.set("stats", null);
        for (Map.Entry<UUID, StatsData> entry : statsMap.entrySet()) {
            String path = "stats." + entry.getKey();
            StatsData d = entry.getValue();
            dataConfig.set(path + ".kills",    d.kills);
            dataConfig.set(path + ".deaths",   d.deaths);
            dataConfig.set(path + ".playtime", d.playTimeMillis);
        }
        try { dataConfig.save(dataFile); } catch (IOException e) { e.printStackTrace(); }
    }

    private StatsData getOrCreate(UUID uuid) {
        return statsMap.computeIfAbsent(uuid, k -> new StatsData());
    }

    // ===================== EVENTS =====================

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        StatsData d = getOrCreate(event.getPlayer().getUniqueId());
        d.sessionStart = System.currentTimeMillis();
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        StatsData d = getOrCreate(event.getPlayer().getUniqueId());
        if (d.sessionStart > 0) {
            d.playTimeMillis += System.currentTimeMillis() - d.sessionStart;
            d.sessionStart = 0;
        }
        saveData();
    }

    @EventHandler
    public void onDeath(PlayerDeathEvent event) {
        StatsData victim = getOrCreate(event.getEntity().getUniqueId());
        victim.deaths++;

        // Nếu người giết là player → cộng kill
        if (event.getEntity().getKiller() instanceof Player) {
            StatsData killer = getOrCreate(event.getEntity().getKiller().getUniqueId());
            killer.kills++;
        }
        saveData();
    }

    // ===================== LỆNH /stats =====================

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player)) {
            sender.sendMessage("§cChỉ người chơi mới dùng được lệnh này!");
            return true;
        }
        Player viewer = (Player) sender;

        // Nếu không có tên → xem stats của chính mình
        String targetName = (args.length > 0) ? args[0] : viewer.getName();

        // Tìm player (online hoặc offline)
        @SuppressWarnings("deprecation")
        OfflinePlayer target = Bukkit.getOfflinePlayer(targetName);

        if (!target.hasPlayedBefore() && !target.isOnline()) {
            viewer.sendMessage("§cKhông tìm thấy người chơi §e\"" + targetName + "\"§c!");
            return true;
        }

        openStatsGUI(viewer, target);
        return true;
    }

    // ===================== GUI =====================

    private void openStatsGUI(Player viewer, OfflinePlayer target) {
        UUID uuid = target.getUniqueId();
        StatsData d = getOrCreate(uuid);

        // Tính thời gian chơi hiện tại (cộng thêm session đang chạy nếu đang online)
        long totalMs = d.playTimeMillis;
        if (d.sessionStart > 0) totalMs += System.currentTimeMillis() - d.sessionStart;

        double balance = economyManager.getBalanceByUUID(uuid);
        String name = target.getName() != null ? target.getName() : uuid.toString();
        boolean isOnline = target.isOnline();

        // Thống kê thời gian
        long totalSeconds = totalMs / 1000;
        long days    = totalSeconds / 86400;
        long hours   = (totalSeconds % 86400) / 3600;
        long minutes = (totalSeconds % 3600) / 60;

        // Thống kê từ Minecraft Statistic (bonus - nếu player đã từng online)
        int statKills  = d.kills;
        int statDeaths = d.deaths;

        // Nếu đang online có thể lấy Bukkit Statistic làm cross-check
        if (isOnline) {
            Player online = target.getPlayer();
            if (online != null) {
                // Dùng stat riêng của ta, không override
            }
        }

        Inventory inv = Bukkit.createInventory(null, 54, GUI_TITLE_PREFIX + name);

        // --- Nền kính ---
        ItemStack border = makeItem(Material.GRAY_STAINED_GLASS_PANE, " ");
        for (int i = 0; i < 54; i++) inv.setItem(i, border);

        // --- Đầu người chơi (slot 4) ---
        ItemStack skull = new ItemStack(Material.PLAYER_HEAD);
        SkullMeta skullMeta = (SkullMeta) skull.getItemMeta();
        if (skullMeta != null) {
            skullMeta.setOwningPlayer(target);
            String status = isOnline ? "§aĐang Online" : "§7Offline";
            skullMeta.setDisplayName("§e§l" + name);
            skullMeta.setLore(Arrays.asList(
                    "§7Trạng thái: " + status
            ));
            skull.setItemMeta(skullMeta);
        }
        inv.setItem(4, skull);

        // --- Tiền (slot 20) ---
        inv.setItem(20, makeItem(
                Material.GOLD_INGOT,
                "§6§l💰 Số Tiền",
                "§f" + MONEY_FMT.format(balance) + " §6Tiền",
                "",
                "§7Tài khoản hiện tại của người chơi."
        ));

        // --- Số lần chết (slot 22) ---
        inv.setItem(22, makeItem(
                Material.SKELETON_SKULL,
                "§c§l💀 Số Lần Chết",
                "§f" + statDeaths + " §clần",
                "",
                "§7Tổng số lần bị tiêu diệt."
        ));

        // --- Số kill (slot 24) ---
        inv.setItem(24, makeItem(
                Material.IRON_SWORD,
                "§a§l⚔ Số Kill",
                "§f" + statKills + " §akill",
                "",
                "§7Tổng số người chơi đã hạ gục."
        ));

        // --- Thời gian chơi (slot 31) ---
        String playtimeStr;
        if (days > 0) {
            playtimeStr = "§f" + days + " §7ngày §f" + hours + " §7giờ §f" + minutes + " §7phút";
        } else if (hours > 0) {
            playtimeStr = "§f" + hours + " §7giờ §f" + minutes + " §7phút";
        } else {
            playtimeStr = "§f" + minutes + " §7phút";
        }

        inv.setItem(31, makeItem(
                Material.CLOCK,
                "§b§l⏱ Thời Gian Chơi",
                playtimeStr,
                "",
                "§7Tổng thời gian đã chơi trên server."
        ));

        // --- K/D Ratio (slot 40) ---
        double kd = (statDeaths == 0) ? statKills : (double) statKills / statDeaths;
        String kdStr = String.format("%.2f", kd);
        Material kdMat = kd >= 1.0 ? Material.EMERALD : Material.REDSTONE;
        inv.setItem(40, makeItem(
                kdMat,
                "§e§l📊 K/D Ratio",
                "§f" + kdStr,
                "§7(" + statKills + " kill / " + statDeaths + " death)",
                "",
                kd >= 2.0 ? "§6★ Xuất sắc!" : kd >= 1.0 ? "§aÔn!" : "§cCần cải thiện."
        ));

        // --- Nút đóng (slot 49) ---
        inv.setItem(49, makeItem(Material.BARRIER, "§c§lĐóng", "§7Click để đóng."));

        viewer.openInventory(inv);
    }

    @EventHandler
    public void onInventoryClick(org.bukkit.event.inventory.InventoryClickEvent event) {
        if (!event.getView().getTitle().startsWith(GUI_TITLE_PREFIX)) return;
        event.setCancelled(true);
        if (event.getRawSlot() == 49) event.getWhoClicked().closeInventory();
    }

    // ===================== TAB COMPLETE =====================

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length != 1) return Collections.emptyList();
        String input = args[0].toLowerCase();
        List<String> result = new ArrayList<>();
        for (Player p : Bukkit.getOnlinePlayers()) {
            if (p.getName().toLowerCase().startsWith(input)) result.add(p.getName());
        }
        return result;
    }

    // ===================== HELPER =====================

    private ItemStack makeItem(Material mat, String name, String... lore) {
        ItemStack item = new ItemStack(mat);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(name);
            if (lore.length > 0) meta.setLore(Arrays.asList(lore));
            item.setItemMeta(meta);
        }
        return item;
    }
}
