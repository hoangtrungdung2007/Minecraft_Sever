package com.utilitytoolsplugin;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.data.BlockData;
import org.bukkit.block.data.Levelled;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerItemHeldEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * TorchLightListener - Đuốc Sáng Động Khi Cầm Trên Tay
 *
 * Logic:
 * - Khi người chơi cầm vật phẩm phát sáng (đuốc, đèn lồng, glowstone, v.v.)
 *   → Đặt một block ánh sáng ẩn (Material.LIGHT) tại vị trí của player
 *   → Block LIGHT có level tương ứng với vật phẩm đang cầm
 * - Khi player di chuyển → dịch chuyển block ánh sáng theo
 * - Khi player bỏ đuốc / rời server → xóa block ánh sáng
 *
 * Kỹ thuật:
 * - Dùng Material.LIGHT (invisible light source, 1.17+)
 * - Block LIGHT không hiển thị trong game, chỉ phát sáng
 * - Track last light position per player để xóa khi di chuyển
 * - Scheduler 4 tick/lần để tránh quá tải server
 *
 * Vật phẩm phát sáng được hỗ trợ:
 * Đuốc (15), Đuốc linh hồn (10), Đèn lồng (15), Đèn lồng hồn (10),
 * Glowstone (15), Đèn biển (15), Nấm phát sáng (7),
 * Đuốc redstone (7 khi bình thường), Lửa (15), Blaze Rod (12),
 * Magma Block (3), Lava Bucket (15), Glow Berry (7),
 * Block End Rod (14), Shroomlight (15), Frosted Ice (varying)
 */
public class TorchLightListener implements Listener {

    private final UtilityToolsPlugin plugin;

    // Map: playerUUID -> vị trí block LIGHT hiện tại
    private final Map<UUID, Location> lightLocations = new HashMap<>();

    // Map: playerUUID -> task scheduler
    private final Map<UUID, BukkitTask> lightTasks = new HashMap<>();

    // Map: vật phẩm -> mức sáng (0-15)
    private static final Map<Material, Integer> LIGHT_ITEMS = new HashMap<>();

    static {
        // Mức sáng tối đa (15)
        LIGHT_ITEMS.put(Material.TORCH,                15);
        LIGHT_ITEMS.put(Material.WALL_TORCH,           15);
        LIGHT_ITEMS.put(Material.GLOWSTONE,            15);
        LIGHT_ITEMS.put(Material.SEA_LANTERN,          15);
        LIGHT_ITEMS.put(Material.BEACON,               15);
        LIGHT_ITEMS.put(Material.SHROOMLIGHT,          15);
        LIGHT_ITEMS.put(Material.LANTERN,              15);
        LIGHT_ITEMS.put(Material.LAVA_BUCKET,          15);
        LIGHT_ITEMS.put(Material.FIRE,                 15);
        LIGHT_ITEMS.put(Material.CONDUIT,              15);
        LIGHT_ITEMS.put(Material.JACK_O_LANTERN,       15);
        LIGHT_ITEMS.put(Material.CAMPFIRE,             15);

        // Mức sáng cao (14)
        LIGHT_ITEMS.put(Material.END_ROD,              14);

        // Mức sáng 13
        LIGHT_ITEMS.put(Material.FIRE_CHARGE,          13);

        // Mức sáng 12
        LIGHT_ITEMS.put(Material.BLAZE_ROD,            12);
        LIGHT_ITEMS.put(Material.BLAZE_POWDER,         12);

        // Mức sáng 11
        LIGHT_ITEMS.put(Material.SOUL_CAMPFIRE,        10);
        LIGHT_ITEMS.put(Material.SOUL_LANTERN,         10);
        LIGHT_ITEMS.put(Material.SOUL_TORCH,           10);
        LIGHT_ITEMS.put(Material.SOUL_WALL_TORCH,      10);

        // Mức sáng 9
        LIGHT_ITEMS.put(Material.ENDER_CHEST,           7);
        LIGHT_ITEMS.put(Material.ENCHANTING_TABLE,      7);

        // Mức sáng 7
        LIGHT_ITEMS.put(Material.GLOW_BERRIES,          7);
        LIGHT_ITEMS.put(Material.REDSTONE_TORCH,        7);
        LIGHT_ITEMS.put(Material.REDSTONE_WALL_TORCH,   7);
        LIGHT_ITEMS.put(Material.GLOWSTONE_DUST,        7);
        LIGHT_ITEMS.put(Material.SEA_PICKLE,            6);
        LIGHT_ITEMS.put(Material.BROWN_MUSHROOM,        1);

        // Mức sáng thấp
        LIGHT_ITEMS.put(Material.MAGMA_BLOCK,           3);
        LIGHT_ITEMS.put(Material.NETHER_PORTAL,        11);
        LIGHT_ITEMS.put(Material.KELP_PLANT,            0);
    }

    public TorchLightListener(UtilityToolsPlugin plugin) {
        this.plugin = plugin;
        startGlobalLightTask();
    }

    /**
     * Task chạy mỗi 3 tick (15 lần/giây) để cập nhật ánh sáng theo vị trí player
     */
    private void startGlobalLightTask() {
        new BukkitRunnable() {
            @Override
            public void run() {
                for (Player player : plugin.getServer().getOnlinePlayers()) {
                    if (!player.isOnline()) continue;

                    ItemStack held = player.getInventory().getItemInMainHand();
                    Integer lightLevel = getLightLevel(held);

                    // Kiểm tra tay phụ nếu tay chính không có
                    if (lightLevel == null || lightLevel == 0) {
                        ItemStack offhand = player.getInventory().getItemInOffHand();
                        lightLevel = getLightLevel(offhand);
                    }

                    if (lightLevel != null && lightLevel > 0) {
                        updatePlayerLight(player, lightLevel);
                    } else {
                        removePlayerLight(player);
                    }
                }
            }
        }.runTaskTimer(plugin, 0L, 3L); // Mỗi 3 tick
    }

    /**
     * Cập nhật block ánh sáng tại vị trí player
     */
    private void updatePlayerLight(Player player, int lightLevel) {
        Location playerLoc = player.getLocation().getBlock().getLocation();
        UUID uuid = player.getUniqueId();
        Location oldLoc = lightLocations.get(uuid);

        // Nếu player không di chuyển → không cần cập nhật
        if (oldLoc != null && isSameBlock(oldLoc, playerLoc)) {
            return;
        }

        // Xóa ánh sáng cũ
        if (oldLoc != null) {
            clearLightBlock(oldLoc);
        }

        // Đặt ánh sáng mới tại vị trí player (hoặc +1 block lên để không bị che)
        Location lightLoc = findBestLightLocation(playerLoc, player);

        if (setLightBlock(lightLoc, lightLevel)) {
            lightLocations.put(uuid, lightLoc);
        }
    }

    /**
     * Tìm vị trí tốt nhất để đặt block ánh sáng
     * Ưu tiên: vị trí đứng của player → block trên đầu → block xung quanh
     */
    private Location findBestLightLocation(Location baseLoc, Player player) {
        World world = baseLoc.getWorld();
        if (world == null) return baseLoc;

        // Thử vị trí đứng của player trước
        Location[] candidates = {
            baseLoc.clone(),
            baseLoc.clone().add(0, 1, 0),
            baseLoc.clone().add(0, -1, 0),
            baseLoc.clone().add(1, 0, 0),
            baseLoc.clone().add(-1, 0, 0),
            baseLoc.clone().add(0, 0, 1),
            baseLoc.clone().add(0, 0, -1),
        };

        for (Location loc : candidates) {
            Block block = loc.getBlock();
            // LIGHT block có thể đặt ở air, water, hoặc block không solid
            if (block.getType() == Material.AIR
                || block.getType() == Material.WATER
                || block.getType() == Material.LIGHT
                || !block.getType().isSolid()) {
                return loc;
            }
        }

        return baseLoc; // Fallback
    }

    /**
     * Đặt Material.LIGHT tại vị trí chỉ định với mức sáng tương ứng
     */
    private boolean setLightBlock(Location loc, int lightLevel) {
        if (loc == null || loc.getWorld() == null) return false;
        Block block = loc.getBlock();

        // Không ghi đè block solid (đá, đất, v.v.)
        if (block.getType().isSolid()
            && block.getType() != Material.LIGHT) {
            return false;
        }

        try {
            BlockData lightData = Material.LIGHT.createBlockData();
            if (lightData instanceof Levelled levelled) {
                // Clamp light level 0-15
                levelled.setLevel(Math.max(0, Math.min(15, lightLevel)));
            }
            block.setBlockData(lightData, false); // false = không gửi physics update
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * Xóa block ánh sáng (đặt lại thành AIR)
     */
    private void clearLightBlock(Location loc) {
        if (loc == null || loc.getWorld() == null) return;
        Block block = loc.getBlock();
        if (block.getType() == Material.LIGHT) {
            block.setType(Material.AIR, false);
        }
    }

    /**
     * Lấy mức sáng của vật phẩm
     * Trả về null nếu không phải vật phẩm phát sáng
     */
    private Integer getLightLevel(ItemStack item) {
        if (item == null || item.getType() == Material.AIR) return null;
        return LIGHT_ITEMS.get(item.getType());
    }

    /**
     * Xóa ánh sáng khi player không còn cầm đuốc
     */
    private void removePlayerLight(Player player) {
        UUID uuid = player.getUniqueId();
        Location oldLoc = lightLocations.remove(uuid);
        if (oldLoc != null) {
            clearLightBlock(oldLoc);
        }
    }

    /**
     * Kiểm tra 2 Location có cùng block không
     */
    private boolean isSameBlock(Location a, Location b) {
        if (a.getWorld() != b.getWorld()) return false;
        return a.getBlockX() == b.getBlockX()
            && a.getBlockY() == b.getBlockY()
            && a.getBlockZ() == b.getBlockZ();
    }

    // --- Event Handlers ---

    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerQuit(PlayerQuitEvent event) {
        removePlayerLight(event.getPlayer());
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onItemHeld(PlayerItemHeldEvent event) {
        // Cập nhật ngay lập tức khi đổi item
        Player player = event.getPlayer();
        plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
            ItemStack held = player.getInventory().getItemInMainHand();
            Integer lightLevel = getLightLevel(held);
            
            // Kiểm tra tay phụ nếu tay chính không có
            if (lightLevel == null || lightLevel == 0) {
                ItemStack offhand = player.getInventory().getItemInOffHand();
                lightLevel = getLightLevel(offhand);
            }

            if (lightLevel != null && lightLevel > 0) {
                updatePlayerLight(player, lightLevel);
            } else {
                removePlayerLight(player);
            }
        }, 1L);
    }

    /**
     * Dọn dẹp tất cả ánh sáng khi plugin tắt
     */
    public void cleanup() {
        for (Location loc : lightLocations.values()) {
            clearLightBlock(loc);
        }
        lightLocations.clear();
    }
}
