package com.utilitytoolsplugin;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.enchantments.Enchantment;
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
 * TreeCapitator - Chặt Cây Nhanh
 * 
 * Logic:
 * - Khi người chơi phá 1 block gỗ bằng rìu (Axe)
 * - BFS (Breadth-First Search) từ block bị phá
 * - Tìm tất cả block gỗ kết nối (6 hướng + 18 hướng chéo = 26 block xung quanh)
 * - Phá tất cả cùng lúc → drop vật phẩm
 * - Tiêu hao độ bền rìu tương ứng (1 durability/block)
 * - Giới hạn tối đa 500 block để tránh lag
 */
public class TreeCapitatorListener implements Listener {

    private final UtilityToolsPlugin plugin;
    
    // Giới hạn số block tối đa để tránh lag server
    private static final int MAX_BLOCKS = 500;
    
    // Set các loại gỗ (log)
    private static final Set<Material> WOOD_LOGS = new HashSet<>(Arrays.asList(
        Material.OAK_LOG, Material.SPRUCE_LOG, Material.BIRCH_LOG,
        Material.JUNGLE_LOG, Material.ACACIA_LOG, Material.DARK_OAK_LOG,
        Material.MANGROVE_LOG, Material.CHERRY_LOG, Material.BAMBOO_BLOCK,
        Material.OAK_WOOD, Material.SPRUCE_WOOD, Material.BIRCH_WOOD,
        Material.JUNGLE_WOOD, Material.ACACIA_WOOD, Material.DARK_OAK_WOOD,
        Material.MANGROVE_WOOD, Material.CHERRY_WOOD,
        Material.STRIPPED_OAK_LOG, Material.STRIPPED_SPRUCE_LOG,
        Material.STRIPPED_BIRCH_LOG, Material.STRIPPED_JUNGLE_LOG,
        Material.STRIPPED_ACACIA_LOG, Material.STRIPPED_DARK_OAK_LOG,
        Material.STRIPPED_MANGROVE_LOG, Material.STRIPPED_CHERRY_LOG
    ));
    
    // Set các loại lá cây
    private static final Set<Material> LEAVES = new HashSet<>(Arrays.asList(
        Material.OAK_LEAVES, Material.SPRUCE_LEAVES, Material.BIRCH_LEAVES,
        Material.JUNGLE_LEAVES, Material.ACACIA_LEAVES, Material.DARK_OAK_LEAVES,
        Material.MANGROVE_LEAVES, Material.CHERRY_LEAVES, Material.AZALEA_LEAVES,
        Material.FLOWERING_AZALEA_LEAVES
    ));
    
    // Set các loại rìu (Axe)
    private static final Set<Material> AXES = new HashSet<>(Arrays.asList(
        Material.WOODEN_AXE, Material.STONE_AXE, Material.IRON_AXE,
        Material.GOLDEN_AXE, Material.DIAMOND_AXE, Material.NETHERITE_AXE
    ));

    public TreeCapitatorListener(UtilityToolsPlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onBlockBreak(BlockBreakEvent event) {
        Player player = event.getPlayer();
        Block brokenBlock = event.getBlock();
        ItemStack tool = player.getInventory().getItemInMainHand();
        
        // Kiểm tra: block phải là gỗ và dùng rìu
        if (!WOOD_LOGS.contains(brokenBlock.getType())) return;
        if (!AXES.contains(tool.getType())) return;
        
        // Kiểm tra: người chơi không đang Sneak (tùy chọn: Sneak = không kích hoạt)
        // Bỏ qua kiểm tra này → tự động kích hoạt khi chặt bằng rìu
        
        // BFS tìm toàn bộ block gỗ và lá cây liên kết
        Material logType = brokenBlock.getType();
        List<Block> treBlocks = findTreeBlocks(brokenBlock, logType);
        
        if (treBlocks.isEmpty()) return;
        
        // Tính độ bền tiêu hao
        int durabilityUsed = treBlocks.size();
        
        // Kiểm tra có đủ độ bền không
        ItemMeta meta = tool.getItemMeta();
        if (meta instanceof Damageable damageable) {
            int maxDurability = tool.getType().getMaxDurability();
            int currentDamage = damageable.getDamage();
            
            // Nếu dùng hết độ bền, phá cây luôn nhưng vỡ rìu
            int newDamage = currentDamage + durabilityUsed;
            
            // Phá tất cả các block cây (không bao gồm block đang phá - đã được xử lý bởi event)
            for (Block block : treBlocks) {
                block.breakNaturally(tool);
            }
            
            // Cập nhật độ bền rìu
            if (newDamage >= maxDurability) {
                // Rìu bị vỡ
                player.getInventory().setItemInMainHand(null);
                // Hiệu ứng âm thanh khi rìu vỡ
                player.getWorld().playSound(player.getLocation(), 
                    org.bukkit.Sound.ENTITY_ITEM_BREAK, 1.0f, 1.0f);
                player.sendMessage("§c✖ Rìu của bạn đã bị vỡ do chặt quá nhiều cây!");
            } else {
                damageable.setDamage(newDamage);
                tool.setItemMeta(meta);
            }
        }
        
        // Thông báo nhỏ
        player.sendMessage(String.format("§a✔ TreeCapitator: Đã chặt %d block gỗ!", treBlocks.size() + 1));
    }
    
    /**
     * BFS tìm tất cả block gỗ và lá cây thuộc cùng 1 cây
     * Chỉ tìm gỗ cùng loại, lá cây bất kỳ loại
     */
    private List<Block> findTreeBlocks(Block startBlock, Material logType) {
        List<Block> result = new ArrayList<>();
        Set<Location> visited = new HashSet<>();
        Queue<Block> queue = new LinkedList<>();
        
        queue.add(startBlock);
        visited.add(startBlock.getLocation());
        
        while (!queue.isEmpty() && result.size() < MAX_BLOCKS) {
            Block current = queue.poll();
            
            // Kiểm tra 26 block xung quanh (3x3x3 cube)
            for (int dx = -1; dx <= 1; dx++) {
                for (int dy = -1; dy <= 1; dy++) {
                    for (int dz = -1; dz <= 1; dz++) {
                        if (dx == 0 && dy == 0 && dz == 0) continue;
                        
                        Block neighbor = current.getRelative(dx, dy, dz);
                        Location loc = neighbor.getLocation();
                        
                        if (visited.contains(loc)) continue;
                        visited.add(loc);
                        
                        // Chỉ theo cùng loại gỗ (không thêm lá vào queue để lan rộng)
                        if (neighbor.getType() == logType) {
                            result.add(neighbor);
                            queue.add(neighbor);
                        } else if (LEAVES.contains(neighbor.getType())) {
                            // Lá cây: thêm vào kết quả nhưng không lan tiếp
                            result.add(neighbor);
                        }
                    }
                }
            }
        }
        
        return result;
    }
}
