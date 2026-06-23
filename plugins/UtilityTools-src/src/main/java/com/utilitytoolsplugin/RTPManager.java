package com.utilitytoolsplugin;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
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
import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Random;
import java.util.Set;
import java.util.UUID;

public class RTPManager implements CommandExecutor, Listener {

    private final Plugin plugin;
    private final Random random = new Random();

    // Tên GUI để phân biệt
    private static final String GUI_TITLE = "§5§l✦ §dChọn Thế Giới Teleport §5§l✦";

    // Set các UUID đang trong cooldown
    private final Set<UUID> cooldownSet = new HashSet<>();

    // Cooldown giây
    private static final int COOLDOWN_SECONDS = 30;

    // Phạm vi tọa độ ngẫu nhiên
    private static final int RTP_RANGE = 5000;
    private static final int RTP_MIN   = 500;

    // Số lần thử tìm vị trí an toàn tối đa
    private static final int MAX_ATTEMPTS = 20;

    public RTPManager(Plugin plugin) {
        this.plugin = plugin;
    }

    // ===================== LỆNH /rtp =====================

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player)) {
            sender.sendMessage("§cChỉ người chơi mới dùng được lệnh này!");
            return true;
        }

        Player player = (Player) sender;

        if (cooldownSet.contains(player.getUniqueId())) {
            player.sendMessage("§c⏳ Bạn đang trong thời gian chờ! Hãy đợi §e" + COOLDOWN_SECONDS + "s §ctrước khi dùng lại /rtp.");
            return true;
        }

        openWorldSelectGUI(player);
        return true;
    }

    // ===================== GUI CHỌN THẾ GIỚI =====================

    private void openWorldSelectGUI(Player player) {
        Inventory inv = Bukkit.createInventory(null, 27, GUI_TITLE);

        // --- Điền nền kính ---
        ItemStack filler = makeItem(Material.GRAY_STAINED_GLASS_PANE, " ");
        for (int i = 0; i < 27; i++) inv.setItem(i, filler);

        // --- Nút Overworld (slot 11) ---
        ItemStack overworldBtn = makeItem(
                Material.GRASS_BLOCK,
                "§a§l🌿 Overworld",
                "§7Teleport đến một tọa độ ngẫu nhiên",
                "§7trong §aThế Giới Bình Thường§7.",
                "",
                "§eClick để xác nhận!"
        );
        inv.setItem(11, overworldBtn);

        // --- Nút Nether (slot 15) ---
        ItemStack netherBtn = makeItem(
                Material.NETHERRACK,
                "§c§l🔥 Địa Ngục (Nether)",
                "§7Teleport đến một tọa độ ngẫu nhiên",
                "§7trong §cĐịa Ngục§7.",
                "",
                "§eClick để xác nhận!"
        );
        inv.setItem(15, netherBtn);

        // --- Nút Đóng (slot 13) ---
        ItemStack closeBtn = makeItem(
                Material.BARRIER,
                "§c§lĐóng",
                "§7Click để đóng bảng chọn."
        );
        inv.setItem(13, closeBtn);

        player.openInventory(inv);
    }

    // ===================== XỬ LÝ CLICK GUI =====================

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        if (!event.getView().getTitle().equals(GUI_TITLE)) return;
        event.setCancelled(true);

        if (!(event.getWhoClicked() instanceof Player)) return;
        Player player = (Player) event.getWhoClicked();

        int slot = event.getRawSlot();
        if (slot == 11) {
            // Overworld
            player.closeInventory();
            teleportRandom(player, World.Environment.NORMAL);
        } else if (slot == 15) {
            // Nether
            player.closeInventory();
            teleportRandom(player, World.Environment.NETHER);
        } else if (slot == 13) {
            player.closeInventory();
        }
    }

    // ===================== LOGIC TELEPORT =====================

    private void teleportRandom(Player player, World.Environment env) {
        // Tìm world theo môi trường
        World targetWorld = getWorld(env);
        if (targetWorld == null) {
            player.sendMessage("§cKhông tìm thấy thế giới §f"
                    + (env == World.Environment.NORMAL ? "Overworld" : "Nether")
                    + "§c trên server này!");
            return;
        }

        player.sendMessage("§a⌛ Đang tìm vị trí ngẫu nhiên trong §f"
                + getEnvName(env) + "§a... Vui lòng chờ!");

        // Tìm vị trí bất đồng bộ để không lag main thread
        new BukkitRunnable() {
            @Override
            public void run() {
                Location safeLoc = findSafeLocation(targetWorld, env);

                // Teleport về main thread
                Bukkit.getScheduler().runTask(plugin, () -> {
                    if (safeLoc == null) {
                        player.sendMessage("§c❌ Không thể tìm được vị trí an toàn sau §e"
                                + MAX_ATTEMPTS + " §clần thử. Hãy thử lại!");
                        return;
                    }

                    player.teleport(safeLoc);
                    player.sendMessage("§a§l✔ Teleport thành công! §fBạn đang ở §e"
                            + getEnvName(env)
                            + " §ftại §7(§e" + safeLoc.getBlockX()
                            + "§7, §e" + safeLoc.getBlockY()
                            + "§7, §e" + safeLoc.getBlockZ() + "§7)");

                    // Bắt đầu cooldown
                    startCooldown(player);
                });
            }
        }.runTaskAsynchronously(plugin);
    }

    /**
     * Tìm vị trí an toàn (không phải trong không khí, không phải dung nham, không phải void)
     */
    private Location findSafeLocation(World world, World.Environment env) {
        for (int attempt = 0; attempt < MAX_ATTEMPTS; attempt++) {
            // Random X, Z trong phạm vi [MIN, RANGE] (cả + và -)
            int sign = random.nextBoolean() ? 1 : -1;
            int x = sign * (RTP_MIN + random.nextInt(RTP_RANGE - RTP_MIN));
            sign = random.nextBoolean() ? 1 : -1;
            int z = sign * (RTP_MIN + random.nextInt(RTP_RANGE - RTP_MIN));

            // Giới hạn Y tùy theo môi trường
            int maxY = (env == World.Environment.NETHER) ? 120 : world.getMaxHeight() - 1;
            int minY = (env == World.Environment.NETHER) ? 30  : 60;

            // Tìm block cao nhất tại (x, z)
            int y = world.getHighestBlockYAt(x, z);
            if (y < minY || y > maxY) continue;

            Location loc = new Location(world, x + 0.5, y + 1, z + 0.5);
            loc.setYaw(random.nextFloat() * 360f);
            loc.setPitch(0f);

            // Kiểm tra an toàn: 2 block không khí trên và không phải dung nham / void
            Block feet  = world.getBlockAt(x, y + 1, z);
            Block head  = world.getBlockAt(x, y + 2, z);
            Block floor = world.getBlockAt(x, y,     z);

            if (!feet.getType().isAir()) continue;
            if (!head.getType().isAir()) continue;
            if (isHazard(floor.getType())) continue;

            return loc;
        }
        return null;
    }

    /** Các block nguy hiểm không được đứng trên */
    private boolean isHazard(Material mat) {
        return mat == Material.LAVA
                || mat == Material.AIR
                || mat == Material.VOID_AIR
                || mat == Material.CAVE_AIR
                || mat == Material.FIRE
                || mat == Material.MAGMA_BLOCK;
    }

    /** Lấy world theo môi trường – ưu tiên world tên mặc định */
    private World getWorld(World.Environment env) {
        for (World w : Bukkit.getWorlds()) {
            if (w.getEnvironment() == env) return w;
        }
        return null;
    }

    private String getEnvName(World.Environment env) {
        return env == World.Environment.NORMAL ? "Overworld" : "Địa Ngục (Nether)";
    }

    // ===================== COOLDOWN =====================

    private void startCooldown(Player player) {
        cooldownSet.add(player.getUniqueId());
        player.sendMessage("§7⏳ Cooldown §e" + COOLDOWN_SECONDS + "s §7đã bắt đầu.");

        new BukkitRunnable() {
            @Override
            public void run() {
                cooldownSet.remove(player.getUniqueId());
                if (player.isOnline()) {
                    player.sendMessage("§a✔ Cooldown §f/rtp §ađã hết! Bạn có thể dùng lại.");
                }
            }
        }.runTaskLater(plugin, COOLDOWN_SECONDS * 20L);
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
