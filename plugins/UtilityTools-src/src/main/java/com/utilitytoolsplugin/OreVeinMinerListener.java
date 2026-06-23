package com.utilitytoolsplugin;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.Damageable;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.*;

/**
 * OreVeinMiner - Đào Mạch Quặng Nhanh
 * 
 * Logic:
 * - Khi người chơi SNEAK (Shift) + đào block khoáng sản bằng cuốc (Pickaxe)
 * - BFS tìm tất cả block khoáng sản cùng loại kề nhau (6 hướng trực tiếp)
 * - Phá tất cả cùng lúc → drop vật phẩm
 * - Tiêu hao độ bền cuốc = số block bị phá
 * - Giới hạn tối đa 64 block (1 stack)
 * 
 * Khoáng sản hỗ trợ:
 * - Coal (Than), Iron (Sắt), Gold (Vàng), Diamond (Kim cương)
 * - Redstone, Lapis, Emerald, Copper, Ancient Debris
 * - Nether Quartz, Nether Gold, Deepslate variants
 */
public class OreVeinMinerListener implements Listener {

    private final UtilityToolsPlugin plugin;
    
    // Giới hạn số block khoáng sản tối đa (1 stack)
    private static final int MAX_ORE_BLOCKS = 64;
    
    // Set các loại khoáng sản được hỗ trợ
    private static final Set<Material> ORES = new HashSet<>(Arrays.asList(
        // Overworld ores
        Material.COAL_ORE,       Material.DEEPSLATE_COAL_ORE,
        Material.IRON_ORE,       Material.DEEPSLATE_IRON_ORE,
        Material.COPPER_ORE,     Material.DEEPSLATE_COPPER_ORE,
        Material.GOLD_ORE,       Material.DEEPSLATE_GOLD_ORE,
        Material.REDSTONE_ORE,   Material.DEEPSLATE_REDSTONE_ORE,
        Material.LAPIS_ORE,      Material.DEEPSLATE_LAPIS_ORE,
        Material.DIAMOND_ORE,    Material.DEEPSLATE_DIAMOND_ORE,
        Material.EMERALD_ORE,    Material.DEEPSLATE_EMERALD_ORE,
        // Nether ores
        Material.NETHER_QUARTZ_ORE,
        Material.NETHER_GOLD_ORE,
        Material.ANCIENT_DEBRIS
    ));
    
    // Set các loại cuốc (Pickaxe)
    private static final Set<Material> PICKAXES = new HashSet<>(Arrays.asList(
        Material.WOODEN_PICKAXE, Material.STONE_PICKAXE, Material.IRON_PICKAXE,
        Material.GOLDEN_PICKAXE, Material.DIAMOND_PICKAXE, Material.NETHERITE_PICKAXE
    ));

    public OreVeinMinerListener(UtilityToolsPlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onBlockBreak(BlockBreakEvent event) {
        Player player = event.getPlayer();
        Block brokenBlock = event.getBlock();
        ItemStack tool = player.getInventory().getItemInMainHand();
        
        // Kiểm tra: block phải là quặng
        if (!ORES.contains(brokenBlock.getType())) return;
        
        // Kiểm tra: dùng cuốc
        if (!PICKAXES.contains(tool.getType())) return;
        
        // Kiểm tra: người chơi phải đang Sneak (Shift)
        if (!player.isSneaking()) return;
        
        // BFS tìm tất cả quặng cùng loại kề nhau
        Material oreType = brokenBlock.getType();
        List<Block> connectedOres = findConnectedOres(brokenBlock, oreType);
        
        if (connectedOres.isEmpty()) return;
        
        // Tính độ bền tiêu hao
        int durabilityUsed = connectedOres.size();
        
        // Xử lý độ bền
        ItemMeta meta = tool.getItemMeta();
        if (meta instanceof Damageable damageable) {
            int maxDurability = tool.getType().getMaxDurability();
            int currentDamage = damageable.getDamage();
            
            // Phá tất cả quặng kết nối
            for (Block block : connectedOres) {
                block.breakNaturally(tool);
            }
            
            // Cập nhật độ bền
            int newDamage = currentDamage + durabilityUsed;
            if (newDamage >= maxDurability) {
                player.getInventory().setItemInMainHand(null);
                player.getWorld().playSound(player.getLocation(), 
                    org.bukkit.Sound.ENTITY_ITEM_BREAK, 1.0f, 1.0f);
                player.sendMessage("§c✖ Cuốc của bạn đã bị vỡ do đào quá nhiều quặng!");
            } else {
                damageable.setDamage(newDamage);
                tool.setItemMeta(meta);
            }
        }
        
        // Thông báo
        String oreName = getOreDisplayName(oreType);
        player.sendMessage(String.format("§b✔ VeinMiner: Đã đào %d block %s!", 
            connectedOres.size() + 1, oreName));
    }
    
    /**
     * BFS 6 hướng - chỉ tìm block trực tiếp kề nhau (không chéo)
     * để phản ánh đúng "mạch quặng" thực sự
     */
    private List<Block> findConnectedOres(Block startBlock, Material oreType) {
        List<Block> result = new ArrayList<>();
        Set<Location> visited = new HashSet<>();
        Queue<Block> queue = new LinkedList<>();
        
        queue.add(startBlock);
        visited.add(startBlock.getLocation());
        
        // 6 hướng trực tiếp (không chéo)
        int[][] directions = {
            {1,0,0}, {-1,0,0},  // X
            {0,1,0}, {0,-1,0},  // Y
            {0,0,1}, {0,0,-1}   // Z
        };
        
        while (!queue.isEmpty() && result.size() < MAX_ORE_BLOCKS) {
            Block current = queue.poll();
            
            for (int[] dir : directions) {
                Block neighbor = current.getRelative(dir[0], dir[1], dir[2]);
                Location loc = neighbor.getLocation();
                
                if (visited.contains(loc)) continue;
                visited.add(loc);
                
                // Chỉ tìm quặng cùng loại
                if (neighbor.getType() == oreType) {
                    result.add(neighbor);
                    queue.add(neighbor);
                }
            }
        }
        
        return result;
    }
    
    /**
     * Tên hiển thị của quặng bằng tiếng Việt
     */
    private String getOreDisplayName(Material ore) {
        return switch (ore) {
            case COAL_ORE, DEEPSLATE_COAL_ORE -> "§8Than";
            case IRON_ORE, DEEPSLATE_IRON_ORE -> "§7Sắt";
            case COPPER_ORE, DEEPSLATE_COPPER_ORE -> "§6Đồng";
            case GOLD_ORE, DEEPSLATE_GOLD_ORE -> "§eVàng";
            case REDSTONE_ORE, DEEPSLATE_REDSTONE_ORE -> "§cRedstone";
            case LAPIS_ORE, DEEPSLATE_LAPIS_ORE -> "§9Lapis";
            case DIAMOND_ORE, DEEPSLATE_DIAMOND_ORE -> "§bKim cương";
            case EMERALD_ORE, DEEPSLATE_EMERALD_ORE -> "§aEmerald";
            case NETHER_QUARTZ_ORE -> "§fThạch anh";
            case NETHER_GOLD_ORE -> "§eVàng Nether";
            case ANCIENT_DEBRIS -> "§5Ancient Debris";
            default -> ore.name();
        };
    }
}
