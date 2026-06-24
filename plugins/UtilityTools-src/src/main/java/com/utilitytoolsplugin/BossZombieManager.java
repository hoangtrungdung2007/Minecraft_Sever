package com.utilitytoolsplugin;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.entity.*;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.CreatureSpawnEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.inventory.EntityEquipment;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.Plugin;
import org.bukkit.util.Vector;

import java.util.HashMap;
import java.util.Map;
import java.util.Random;
import java.util.UUID;

public class BossZombieManager implements Listener {

    private final Plugin plugin;
    private final EconomyManager economyManager;
    private final NamespacedKey BOSS_KEY;
    private final NamespacedKey MACE_KEY;
    private final NamespacedKey IMMUNE_KEY;

    // hit count: UUID boss -> số lần đánh
    private final Map<UUID, Integer> hitCountMap = new HashMap<>();
    private final Random random = new Random();

    // Zombie thường (base)
    private static final double BASE_HP = 20.0;
    private static final double BASE_DAMAGE = 3.0;
    private static final double BASE_RANGE = 50.0; // Tầm nhìn 50 block

    // Boss multiplier
    private static final double SCALE = 2.0; // Giảm Scale từ 3.0 xuống 2.0 (cao ~4 block) để Pathfinder không bị điên
    private static final double HP_MULT = 10.0; // 200 HP
    private static final double DAMAGE_MULT = 17.5; // Sát thương x17.5 (52.5 damage - tăng gấp 3.5 lần)
    private static final double RANGE_MULT = 1.0; // Tầm nhìn 50 block

    // Xác suất mỗi zombie tự nhiên → boss (giữ tương đương spawn rate zombie)
    private static final double SPAWN_CHANCE = 0.03; // 3%

    public BossZombieManager(Plugin plugin, EconomyManager economyManager) {
        this.plugin = plugin;
        this.economyManager = economyManager;
        this.BOSS_KEY = new NamespacedKey(plugin, "boss_zombie");
        this.MACE_KEY = new NamespacedKey(plugin, "mace_weapon");
        this.IMMUNE_KEY = new NamespacedKey(plugin, "explosion_immune");
    }

    // ===================== SPAWN =====================

    @EventHandler
    public void onCreatureSpawn(CreatureSpawnEvent event) {
        if (event.getEntityType() != EntityType.ZOMBIE)
            return;
        if (event.getSpawnReason() != CreatureSpawnEvent.SpawnReason.NATURAL
                && event.getSpawnReason() != CreatureSpawnEvent.SpawnReason.DEFAULT
                && event.getSpawnReason() != CreatureSpawnEvent.SpawnReason.SPAWNER_EGG
                && event.getSpawnReason() != CreatureSpawnEvent.SpawnReason.COMMAND)
            return;
        if (random.nextDouble() > SPAWN_CHANCE)
            return;

        Zombie zombie = (Zombie) event.getEntity();
        // Chờ 1 tick để entity tồn tại hoàn toàn
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (zombie.isValid()) {
                makeBossZombie(zombie);

                // 10% cơ hội xuất hiện 2 con boss cùng lúc
                if (random.nextDouble() < 0.10) {
                    Zombie secondBoss = (Zombie) zombie.getWorld().spawnEntity(zombie.getLocation(), EntityType.ZOMBIE);
                    makeBossZombie(secondBoss);
                }

                // 10% cơ hội Quỷ Vương Xương xuất hiện cùng
                if (random.nextDouble() < 0.10) {
                    Skeleton skeletonBoss = (Skeleton) zombie.getWorld().spawnEntity(zombie.getLocation(),
                            EntityType.SKELETON);
                    UtilityToolsPlugin.getInstance().getBossSkeletonManager().makeBossSkeleton(skeletonBoss);
                }
            }
        }, 1L);
    }

    public void makeBossZombie(Zombie zombie) {
        // Đánh dấu NBT
        zombie.getPersistentDataContainer().set(BOSS_KEY, PersistentDataType.BYTE, (byte) 1);

        // Máu
        setAttr(zombie, Attribute.MAX_HEALTH, BASE_HP * HP_MULT);
        zombie.setHealth(BASE_HP * HP_MULT);

        // Tên hiển thị kèm thanh máu
        updateHealthName(zombie, "Quỷ Vương Zombie");
        zombie.setCustomNameVisible(true);
        zombie.setRemoveWhenFarAway(true); // Tránh kẹt server do quái có tên không despawn
        zombie.setBaby(false);

        // Scale × 3 (Paper 1.20.5+)
        setAttr(zombie, Attribute.SCALE, SCALE);

        // Dame × 5 (15 Dame)
        setAttr(zombie, Attribute.ATTACK_DAMAGE, BASE_DAMAGE * DAMAGE_MULT);

        // Attack speed cao hơn để hoạt ảnh mượt, không đơ
        setAttr(zombie, Attribute.ATTACK_SPEED, 2.0);

        // Phạm vi phát hiện 50 block
        setAttr(zombie, Attribute.FOLLOW_RANGE, BASE_RANGE * RANGE_MULT);

        // Knockback strength nhỏ (đòn 3 sẽ boost riêng)
        setAttr(zombie, Attribute.ATTACK_KNOCKBACK, 0.5);

        // Tốc độ di chuyển nhanh bằng zombie con (0.36)
        setAttr(zombie, Attribute.MOVEMENT_SPEED, 0.36);

        // Trang bị giáp Netherite & Đầu Wither Skeleton
        EntityEquipment eq = zombie.getEquipment();
        if (eq != null) {
            // Vũ khí: Chùy Quỷ Dữ (Mace) có Enchantment để phát sáng
            ItemStack mace = new ItemStack(Material.MACE);
            ItemMeta maceMeta = mace.getItemMeta();
            if (maceMeta != null) {
                maceMeta.setDisplayName("§4§lChùy Quỷ Dữ");
                maceMeta.addEnchant(Enchantment.KNOCKBACK, 2, true);
                maceMeta.getPersistentDataContainer().set(MACE_KEY, PersistentDataType.BYTE, (byte) 1);
                mace.setItemMeta(maceMeta);
            }
            eq.setItemInMainHand(mace);
            eq.setItemInMainHandDropChance(0.0f); // không drop

            // Đầu: Wither Skeleton
            eq.setHelmet(new ItemStack(Material.WITHER_SKELETON_SKULL));
            eq.setHelmetDropChance(0.0f);

            // Áo, Quần, Giày: Netherite (Có Enchantment phát sáng)
            ItemStack chest = new ItemStack(Material.NETHERITE_CHESTPLATE);
            chest.addUnsafeEnchantment(Enchantment.PROTECTION, 4);
            eq.setChestplate(chest);
            eq.setChestplateDropChance(0.0f);

            ItemStack legs = new ItemStack(Material.NETHERITE_LEGGINGS);
            legs.addUnsafeEnchantment(Enchantment.PROTECTION, 4);
            eq.setLeggings(legs);
            eq.setLeggingsDropChance(0.0f);

            ItemStack boots = new ItemStack(Material.NETHERITE_BOOTS);
            boots.addUnsafeEnchantment(Enchantment.PROTECTION, 4);
            eq.setBoots(boots);
            eq.setBootsDropChance(0.0f);
        }

        // Hiệu ứng spawn
        zombie.getWorld().spawnParticle(Particle.EXPLOSION_EMITTER,
                zombie.getLocation().add(0, 1.5, 0), 2);
        zombie.getWorld().playSound(zombie.getLocation(),
                Sound.ENTITY_WITHER_SPAWN, 0.5f, 1.2f);
    }

    // ===================== ĐÁNH: NỔ VÀ HẤT TUNG =====================

    @EventHandler
    public void onPlayerExplosionDamage(EntityDamageEvent event) {
        if (!(event.getEntity() instanceof Player p)) return;
        if (event.getCause() == EntityDamageEvent.DamageCause.BLOCK_EXPLOSION || event.getCause() == EntityDamageEvent.DamageCause.ENTITY_EXPLOSION) {
            if (p.getPersistentDataContainer().has(IMMUNE_KEY, PersistentDataType.BYTE)) {
                event.setCancelled(true);
            }
        }
    }

    @EventHandler
    public void onEntityDamage(EntityDamageByEntityEvent event) {
        // Chỉ xử lý đòn đánh tay (tránh loop khi vụ nổ gây sát thương)
        if (event.getCause() != EntityDamageEvent.DamageCause.ENTITY_ATTACK)
            return;

        Entity damager = event.getDamager();

        // --- XỬ LÝ NGƯỜI CHƠI DÙNG CHÙY QUỶ DỮ ---
        if (damager instanceof Player p) {
            ItemStack hand = p.getInventory().getItemInMainHand();
            if (hand.hasItemMeta() && hand.getItemMeta().getPersistentDataContainer().has(MACE_KEY, PersistentDataType.BYTE)) {
                Entity victim = event.getEntity();
                
                // Đánh dấu người chơi để miễn sát thương nổ
                p.getPersistentDataContainer().set(IMMUNE_KEY, PersistentDataType.BYTE, (byte) 1);
                
                // Gây nổ tại vị trí mục tiêu (power = 2.0, không cháy, không phá block)
                victim.getWorld().createExplosion(victim.getLocation(), 2.0f, false, false, p);
                
                // Đẩy người chơi lên không trung
                p.setVelocity(new Vector(0, 1.2, 0));
                
                // Xóa đánh dấu miễn sát thương sau 2 tick
                Bukkit.getScheduler().runTaskLater(plugin, () -> {
                    p.getPersistentDataContainer().remove(IMMUNE_KEY);
                }, 2L);
            }
            return;
        }

        // --- XỬ LÝ BOSS ZOMBIE ĐÁNH ---
        if (!(damager instanceof Zombie zombie))
            return;
        if (!zombie.getPersistentDataContainer().has(BOSS_KEY, PersistentDataType.BYTE))
            return;

        UUID id = zombie.getUniqueId();
        int hits = hitCountMap.getOrDefault(id, 0) + 1;
        hitCountMap.put(id, hits);

        Entity victim = event.getEntity();

        // Đánh phát nào cũng nổ, hất tung cực mạnh và gây cháy
        victim.setFireTicks(60); // Cháy 3 giây
        victim.getWorld().createExplosion(victim.getLocation(), 1.3f, false, true, zombie);

        Vector dir = victim.getLocation().toVector()
                .subtract(zombie.getLocation().toVector())
                .normalize()
                .multiply(3.2)
                .setY(1.1);
        victim.setVelocity(dir);

        // Hiệu ứng + âm thanh mạnh
        victim.getWorld().spawnParticle(Particle.EXPLOSION_EMITTER,
                victim.getLocation().add(0, 1, 0), 2);
        victim.getWorld().playSound(victim.getLocation(),
                Sound.ENTITY_IRON_GOLEM_ATTACK, 1.5f, 0.4f);
        victim.getWorld().playSound(victim.getLocation(),
                Sound.ENTITY_GENERIC_EXPLODE, 0.8f, 1.5f);

        if (victim instanceof Player p) {
            p.sendTitle("", "§c§l⚡ Bị hất tung!", 4, 18, 8);
        }
    }

    // ===================== CHỐNG KẸT TƯỜNG =====================

    @EventHandler
    public void onBossSuffocate(EntityDamageEvent event) {
        if (event.getCause() == EntityDamageEvent.DamageCause.CRAMMING || event.getCause() == EntityDamageEvent.DamageCause.SUFFOCATION) {
            if (isBossZombie(event.getEntity())) {
                event.setCancelled(true);
                LivingEntity boss = (LivingEntity) event.getEntity();
                boss.getPersistentDataContainer().set(new org.bukkit.NamespacedKey(plugin, "suffocated"), PersistentDataType.BYTE, (byte) 1);
                boss.setHealth(0);
            }
        }
    }

    // Dọn map khi boss chết → tránh memory leak và trao phần thưởng
    @EventHandler
    public void onEntityDeath(EntityDeathEvent event) {
        UUID id = event.getEntity().getUniqueId();
        if (hitCountMap.containsKey(id) || isBossZombie(event.getEntity())) {
            hitCountMap.remove(id);

            // Xóa rớt đồ mặc định (để rớt đồ tùy chỉnh)
            event.getDrops().clear();

            if (event.getEntity().getPersistentDataContainer().has(new org.bukkit.NamespacedKey(plugin, "suffocated"), PersistentDataType.BYTE)) {
                return;
            }

            // Rớt Chùy Quỷ Dữ
            ItemStack mace = new ItemStack(Material.MACE);
            ItemMeta maceMeta = mace.getItemMeta();
            if (maceMeta != null) {
                maceMeta.setDisplayName("§4§lChùy Quỷ Dữ");
                maceMeta.addEnchant(Enchantment.KNOCKBACK, 2, true);
                maceMeta.getPersistentDataContainer().set(MACE_KEY, PersistentDataType.BYTE, (byte) 1);
                // Có thể thêm độ sát thương hoặc enchant khác nếu muốn
                mace.setItemMeta(maceMeta);
            }
            event.getDrops().add(mace);

            // Hiệu ứng chết
            event.getEntity().getWorld().spawnParticle(
                    Particle.EXPLOSION_EMITTER,
                    event.getEntity().getLocation().add(0, 1.5, 0), 3);
            event.getEntity().getWorld().playSound(
                    event.getEntity().getLocation(),
                    Sound.ENTITY_WITHER_DEATH, 0.8f, 1.0f);

            // Trao thưởng cho người kết liễu
            Player killer = event.getEntity().getKiller();
            if (killer != null) {
                // Tặng 30 Level
                killer.giveExpLevels(30);

                // Tặng 100,000 tiền
                economyManager.addBalance(killer, 100000);

                killer.sendMessage("§e§l=================================");
                killer.sendMessage("§a✨ §lCHIẾN CÔNG HIỂN HÁCH §a✨");
                killer.sendMessage("§7Bạn đã tiêu diệt §4§l☠ Quỷ Vương Zombie ☠");
                killer.sendMessage("");
                killer.sendMessage("§f🎁 §ePhần thưởng nhận được:");
                killer.sendMessage("§7- §a+30 Level Kinh Nghiệm");
                killer.sendMessage("§7- §6+100,000 Tiền");
                killer.sendMessage("§7- §c1x Chùy Quỷ Dữ §7(Rớt dưới đất)");
                killer.sendMessage("§e§l=================================");
            }
        }
    }

    // ===================== HELPER =====================

    private void updateHealthName(LivingEntity boss, String baseName) {
        if (!boss.isValid())
            return;
        double hp = Math.max(0, boss.getHealth());
        double maxHp = 0;
        AttributeInstance maxHpAttr = boss.getAttribute(Attribute.MAX_HEALTH);
        if (maxHpAttr != null) {
            maxHp = maxHpAttr.getValue();
        }
        boss.setCustomName("§4§l☠ " + baseName + " ☠ §c[ " + Math.round(hp) + "/" + Math.round(maxHp) + " ❤ ]");
    }

    @EventHandler
    public void onBossDamageUpdateHealth(EntityDamageEvent event) {
        if (event.isCancelled())
            return;
        if (isBossZombie(event.getEntity())) {
            LivingEntity boss = (LivingEntity) event.getEntity();
            Bukkit.getScheduler().runTaskLater(plugin, () -> updateHealthName(boss, "Quỷ Vương Zombie"), 1L);
        }
    }

    @EventHandler
    public void onBossRegainHealth(org.bukkit.event.entity.EntityRegainHealthEvent event) {
        if (event.isCancelled())
            return;
        if (isBossZombie(event.getEntity())) {
            LivingEntity boss = (LivingEntity) event.getEntity();
            Bukkit.getScheduler().runTaskLater(plugin, () -> updateHealthName(boss, "Quỷ Vương Zombie"), 1L);
        }
    }

    private void setAttr(LivingEntity entity, Attribute attr, double value) {
        AttributeInstance inst = entity.getAttribute(attr);
        if (inst != null)
            inst.setBaseValue(value);
    }

    public boolean isBossZombie(Entity entity) {
        if (!(entity instanceof Zombie))
            return false;
        return entity.getPersistentDataContainer().has(BOSS_KEY, PersistentDataType.BYTE);
    }
}
