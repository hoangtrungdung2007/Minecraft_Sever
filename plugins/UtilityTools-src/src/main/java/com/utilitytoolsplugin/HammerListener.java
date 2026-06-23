package com.utilitytoolsplugin;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.Damageable;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

import java.util.*;

/**
 * Hammer 3x3 - Cuốc Phá 3x3 Block
 * 
 * Logic:
 * - Người chơi cầm "Hammer" (cuốc có NBT đặc biệt hoặc đặt tên "Hammer")
 * - Khi đào 1 block đá/đất/khoáng sản → tự động phá 3x3x3 block
 * - 3x3x3 = khối lập phương 26 block xung quanh block đang phá
 * - Tiêu hao độ bền = số block thực sự bị phá (tối đa 26)
 * 
 * Cách xác định mặt phẳng 3x3:
 * - Nhìn lên/xuống: mặt phẳng XZ (ngang)
 * - Nhìn ngang: mặt phẳng XY hoặc ZY tùy hướng nhìn
 * 
 * Cách nhận Hammer:
 * - Lệnh: /hammer give [player]
 * - Hoặc: đặt tên cuốc diamond là "§6Hammer" trong anvil
 */
public class HammerListener implements Listener {

    private final UtilityToolsPlugin plugin;
    private final NamespacedKey HAMMER_KEY;
    
    // Block có thể bị phá bởi Hammer
    private static final Set<Material> HAMMER_BREAKABLE = new HashSet<>(Arrays.asList(
        // Đá các loại
        Material.STONE, Material.COBBLESTONE, Material.GRANITE, Material.DIORITE,
        Material.ANDESITE, Material.DEEPSLATE, Material.COBBLED_DEEPSLATE,
        Material.BLACKSTONE, Material.BASALT, Material.SMOOTH_BASALT,
        Material.TUFF, Material.CALCITE, Material.DRIPSTONE_BLOCK,
        Material.NETHERRACK, Material.END_STONE, Material.SANDSTONE,
        Material.RED_SANDSTONE, Material.OBSIDIAN,
        // Đất
        Material.DIRT, Material.GRASS_BLOCK, Material.GRAVEL, Material.SAND,
        Material.RED_SAND, Material.CLAY, Material.COARSE_DIRT,
        Material.ROOTED_DIRT, Material.MUD, Material.PACKED_MUD,
        Material.MUDDY_MANGROVE_ROOTS,
        // Khoáng sản và deepslate ores (tất cả)
        Material.COAL_ORE, Material.DEEPSLATE_COAL_ORE,
        Material.IRON_ORE, Material.DEEPSLATE_IRON_ORE,
        Material.COPPER_ORE, Material.DEEPSLATE_COPPER_ORE,
        Material.GOLD_ORE, Material.DEEPSLATE_GOLD_ORE,
        Material.REDSTONE_ORE, Material.DEEPSLATE_REDSTONE_ORE,
        Material.LAPIS_ORE, Material.DEEPSLATE_LAPIS_ORE,
        Material.DIAMOND_ORE, Material.DEEPSLATE_DIAMOND_ORE,
        Material.EMERALD_ORE, Material.DEEPSLATE_EMERALD_ORE,
        Material.NETHER_QUARTZ_ORE, Material.NETHER_GOLD_ORE
    ));
    
    // Các loại cuốc hỗ trợ làm Hammer
    private static final Set<Material> PICKAXES = new HashSet<>(Arrays.asList(
        Material.WOODEN_PICKAXE, Material.STONE_PICKAXE, Material.IRON_PICKAXE,
        Material.GOLDEN_PICKAXE, Material.DIAMOND_PICKAXE, Material.NETHERITE_PICKAXE
    ));

    public HammerListener(UtilityToolsPlugin plugin) {
        this.plugin = plugin;
        this.HAMMER_KEY = new NamespacedKey(plugin, "is_hammer");
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onBlockBreak(BlockBreakEvent event) {
        Player player = event.getPlayer();
        Block brokenBlock = event.getBlock();
        ItemStack tool = player.getInventory().getItemInMainHand();
        
        // Kiểm tra: dùng cuốc
        if (!PICKAXES.contains(tool.getType())) return;
        
        // Kiểm tra: block có thể bị phá bởi Hammer
        if (!HAMMER_BREAKABLE.contains(brokenBlock.getType())) return;
        
        // Kiểm tra: item có phải Hammer không (kiểm tra NBT hoặc tên)
        if (!isHammer(tool)) return;
        
        // Tính toán 26 block theo hướng đào (3x3 x 3 lớp sâu)
        List<Block> surroundingBlocks = getTunnelBlocks(player, brokenBlock);
        
        if (surroundingBlocks.isEmpty()) return;
        
        // Đếm block thực sự bị phá (phải đúng loại có thể phá)
        List<Block> breakableBlocks = new ArrayList<>();
        for (Block b : surroundingBlocks) {
            if (HAMMER_BREAKABLE.contains(b.getType())) {
                breakableBlocks.add(b);
            }
        }
        
        // Tiêu hao độ bền
        ItemMeta meta = tool.getItemMeta();
        if (meta instanceof Damageable damageable) {
            int maxDurability = tool.getType().getMaxDurability();
            int currentDamage = damageable.getDamage();
            int durabilityUsed = breakableBlocks.size();
            
            // Phá tất cả block 3x3
            for (Block block : breakableBlocks) {
                block.breakNaturally(tool);
            }
            
            // Cập nhật độ bền
            int newDamage = currentDamage + durabilityUsed;
            if (newDamage >= maxDurability) {
                player.getInventory().setItemInMainHand(null);
                player.getWorld().playSound(player.getLocation(),
                    org.bukkit.Sound.ENTITY_ITEM_BREAK, 1.0f, 1.0f);
                player.sendMessage("§c✖ Hammer của bạn đã bị vỡ!");
            } else {
                damageable.setDamage(newDamage);
                tool.setItemMeta(meta);
            }
        }
    }
    
    /**
     * Trả về 26 block theo hướng đào:
     *  - Lớp 0 (mặt trước, cùng mặt phẳng với block vừa đào): 8 block xung quanh
     *  - Lớp 1 (sâu hơn 1 block): 9 block
     *  - Lớp 2 (sâu hơn 2 block): 9 block
     *  Tổng = 8 + 9 + 9 = 26 block
     */
    private List<Block> getTunnelBlocks(Player player, Block targetBlock) {
        List<Block> result = new ArrayList<>();

        // Xác định mặt bị tấn công (face bị đào vào)
        BlockFace hitFace = getHitFace(player);

        // Hướng đào sâu vào tường (đối diện với mặt bị đánh)
        int ddx = -hitFace.getModX();
        int ddy = -hitFace.getModY();
        int ddz = -hitFace.getModZ();

        // Hai trục vuông góc tạo nên mặt phẳng 3x3
        int[] ax1, ax2;
        switch (hitFace) {
            case NORTH, SOUTH -> { ax1 = new int[]{1, 0, 0}; ax2 = new int[]{0, 1, 0}; } // mặt XY
            case EAST, WEST   -> { ax1 = new int[]{0, 0, 1}; ax2 = new int[]{0, 1, 0}; } // mặt ZY
            case UP, DOWN     -> { ax1 = new int[]{1, 0, 0}; ax2 = new int[]{0, 0, 1}; } // mặt XZ
            default           -> { ax1 = new int[]{1, 0, 0}; ax2 = new int[]{0, 1, 0}; }
        }

        // 3 lớp: depth=0 (mặt bị đào), depth=1, depth=2
        for (int depth = 0; depth < 3; depth++) {
            for (int i = -1; i <= 1; i++) {
                for (int j = -1; j <= 1; j++) {
                    if (depth == 0 && i == 0 && j == 0) continue; // block chính đã xử lý
                    int dx = ax1[0] * i + ax2[0] * j + ddx * depth;
                    int dy = ax1[1] * i + ax2[1] * j + ddy * depth;
                    int dz = ax1[2] * i + ax2[2] * j + ddz * depth;
                    result.add(targetBlock.getRelative(dx, dy, dz));
                }
            }
        }
        return result;
    }

    /** Xác định mặt block bị tấn công bằng ray trace */
    private BlockFace getHitFace(Player player) {
        org.bukkit.util.RayTraceResult ray = player.getWorld().rayTraceBlocks(
                player.getEyeLocation(),
                player.getEyeLocation().getDirection(),
                6.0,
                org.bukkit.FluidCollisionMode.NEVER,
                true);
        if (ray != null && ray.getHitBlockFace() != null) return ray.getHitBlockFace();

        // Fallback từ pitch/yaw
        float pitch = player.getLocation().getPitch();
        if (pitch < -45) return BlockFace.DOWN;
        if (pitch >  45) return BlockFace.UP;
        float yaw = player.getLocation().getYaw();
        if (yaw >= -45  && yaw <  45)  return BlockFace.SOUTH;
        if (yaw >=  45  && yaw < 135)  return BlockFace.WEST;
        if (yaw >= 135  || yaw < -135) return BlockFace.NORTH;
        return BlockFace.EAST;
    }
    
    /**
     * Kiểm tra item có phải là Hammer hay không
     * Kiểm tra NBT tag hoặc tên item
     */
    public boolean isHammer(ItemStack item) {
        if (item == null || item.getType() == Material.AIR) return false;
        
        // Kiểm tra NBT persistent data
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            if (meta.getPersistentDataContainer().has(HAMMER_KEY, PersistentDataType.BYTE)) {
                return true;
            }
            // Fallback: kiểm tra tên item
            if (meta.hasDisplayName()) {
                String name = org.bukkit.ChatColor.stripColor(meta.getDisplayName());
                if (name.equalsIgnoreCase("Hammer") || name.contains("Búa")) {
                    return true;
                }
            }
        }
        return false;
    }
    
    /**
     * Tạo item Hammer từ cuốc có sẵn (dùng trong command)
     */
    public ItemStack createHammer(Material pickaxeType) {
        ItemStack hammer = new ItemStack(pickaxeType);
        ItemMeta meta = hammer.getItemMeta();
        
        if (meta != null) {
            meta.setDisplayName("§6§lHammer §r§6(3x3x3)");
            
            List<String> lore = new ArrayList<>();
            lore.add("§7Phá §fkhối 3x3x3 §7(26 block) cùng lúc");
            lore.add("§7Tiêu hao §fđộ bền §7cho mỗi block");
            lore.add("§8[UtilityTools Plugin]");
            meta.setLore(lore);
            
            // Đánh dấu NBT
            meta.getPersistentDataContainer().set(HAMMER_KEY, PersistentDataType.BYTE, (byte) 1);
            
            hammer.setItemMeta(meta);
        }
        
        return hammer;
    }
    
    public NamespacedKey getHammerKey() {
        return HAMMER_KEY;
    }
}
