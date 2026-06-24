package com.utilitytoolsplugin;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.*;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.CreatureSpawnEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.entity.EntityShootBowEvent;
import org.bukkit.event.entity.ProjectileHitEvent;
import org.bukkit.inventory.EntityEquipment;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.Plugin;

import java.util.HashMap;
import java.util.Map;
import java.util.Random;
import java.util.UUID;

public class BossSkeletonManager implements Listener {

    private final Plugin plugin;
    private final EconomyManager economyManager;
    private final NamespacedKey BOSS_KEY;
    private final NamespacedKey EXPLOSIVE_BOW_KEY;
    private final NamespacedKey EXPLOSIVE_ARROW_KEY;
    private final NamespacedKey ANCHOR_ARROW_KEY;

    // hit count: UUID boss -> số lần bắn
    private final Map<UUID, Integer> hitCountMap = new HashMap<>();
    private final Random random = new Random();

    // Thông số Skeleton thường
    private static final double BASE_HP = 20.0;
    
    // Thông số Boss Skeleton
    private static final double SCALE = 2.0; // Giảm Scale từ 3.0 xuống 2.0 để tránh lag A*
    private static final double HP = 100.0; // Máu của Boss Skeleton
    private static final double FOLLOW_RANGE = 20.0; // Tầm nhìn 20 block
    
    // Xác suất mỗi skeleton tự nhiên → boss
    private static final double SPAWN_CHANCE = 0.03; // 3%

    public BossSkeletonManager(Plugin plugin, EconomyManager economyManager) {
        this.plugin = plugin;
        this.economyManager = economyManager;
        this.BOSS_KEY = new NamespacedKey(plugin, "boss_skeleton");
        this.EXPLOSIVE_BOW_KEY = new NamespacedKey(plugin, "explosive_bow");
        this.EXPLOSIVE_ARROW_KEY = new NamespacedKey(plugin, "explosive_arrow");
        this.ANCHOR_ARROW_KEY = new NamespacedKey(plugin, "anchor_arrow");
    }

    // ===================== SPAWN =====================

    @EventHandler
    public void onCreatureSpawn(CreatureSpawnEvent event) {
        if (event.getEntityType() != EntityType.SKELETON) return;
        if (event.getSpawnReason() != CreatureSpawnEvent.SpawnReason.NATURAL
                && event.getSpawnReason() != CreatureSpawnEvent.SpawnReason.DEFAULT
                && event.getSpawnReason() != CreatureSpawnEvent.SpawnReason.SPAWNER_EGG
                && event.getSpawnReason() != CreatureSpawnEvent.SpawnReason.COMMAND) return;
        if (random.nextDouble() > SPAWN_CHANCE) return;

        Skeleton skeleton = (Skeleton) event.getEntity();
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (skeleton.isValid()) {
                makeBossSkeleton(skeleton);
                
                // 10% cơ hội xuất hiện 2 con boss cùng lúc
                if (random.nextDouble() < 0.10) {
                    Skeleton secondBoss = (Skeleton) skeleton.getWorld().spawnEntity(skeleton.getLocation(), EntityType.SKELETON);
                    makeBossSkeleton(secondBoss);
                }
                
                // 10% cơ hội Quỷ Vương Zombie xuất hiện cùng
                if (random.nextDouble() < 0.10) {
                    Zombie zombieBoss = (Zombie) skeleton.getWorld().spawnEntity(skeleton.getLocation(), EntityType.ZOMBIE);
                    UtilityToolsPlugin.getInstance().getBossZombieManager().makeBossZombie(zombieBoss);
                }
            }
        }, 1L);
    }

    public void makeBossSkeleton(Skeleton skeleton) {
        skeleton.getPersistentDataContainer().set(BOSS_KEY, PersistentDataType.BYTE, (byte) 1);

        // Máu (40 HP)
        setAttr(skeleton, Attribute.MAX_HEALTH, HP);
        skeleton.setHealth(HP);

        // Tên hiển thị kèm thanh máu
        updateHealthName(skeleton, "Quỷ Vương Xương");
        skeleton.setCustomNameVisible(true);
        skeleton.setRemoveWhenFarAway(true); // Tránh kẹt server do quái có tên không despawn

        // Kích thước x3
        setAttr(skeleton, Attribute.SCALE, SCALE);

        // Phạm vi phát hiện (gấp đôi bình thường)
        setAttr(skeleton, Attribute.FOLLOW_RANGE, FOLLOW_RANGE);

        // Tốc độ di chuyển nhanh bằng người chơi chạy (Sprinting)
        setAttr(skeleton, Attribute.MOVEMENT_SPEED, 0.35);

        // Trang bị: Full Netherite + Cung Enchant
        EntityEquipment eq = skeleton.getEquipment();
        if (eq != null) {
            // Cung Hủy Diệt
            ItemStack bow = new ItemStack(Material.BOW);
            ItemMeta bowMeta = bow.getItemMeta();
            if (bowMeta != null) {
                bowMeta.setDisplayName("§c§lCung Hủy Diệt");
                bowMeta.addEnchant(Enchantment.POWER, 5, true);
                bowMeta.addEnchant(Enchantment.FLAME, 1, true);
                bowMeta.getPersistentDataContainer().set(EXPLOSIVE_BOW_KEY, PersistentDataType.BYTE, (byte) 1);
                bow.setItemMeta(bowMeta);
            }
            eq.setItemInMainHand(bow);
            eq.setItemInMainHandDropChance(0.0f);

            // Full giáp Netherite
            ItemStack helmet = new ItemStack(Material.NETHERITE_HELMET);
            helmet.addUnsafeEnchantment(Enchantment.PROTECTION, 4);
            eq.setHelmet(helmet);
            eq.setHelmetDropChance(0.0f);

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
        skeleton.getWorld().spawnParticle(Particle.FLAME, skeleton.getLocation().add(0, 1.5, 0), 20, 0.5, 0.5, 0.5, 0.1);
        skeleton.getWorld().playSound(skeleton.getLocation(), Sound.ENTITY_WITHER_SPAWN, 0.5f, 1.2f);
    }

    // ===================== BẮN CUNG NỔ =====================

    @EventHandler
    public void onEntityShootBow(EntityShootBowEvent event) {
        ItemStack bow = event.getBow();
        if (bow == null || bow.getItemMeta() == null) return;
        
        // Nếu người bắn dùng Cung Hủy Diệt -> Đánh dấu mũi tên này là mũi tên nổ
        if (bow.getItemMeta().getPersistentDataContainer().has(EXPLOSIVE_BOW_KEY, PersistentDataType.BYTE)) {
            event.getProjectile().getPersistentDataContainer().set(EXPLOSIVE_ARROW_KEY, PersistentDataType.BYTE, (byte) 1);
            
            // Nếu người bắn là Player, đếm lượt bắn kích hoạt Neo Hồi Sinh ở đòn thứ 3
            if (event.getEntity() instanceof Player player) {
                UUID id = player.getUniqueId();
                int shots = hitCountMap.getOrDefault(id, 0) + 1;
                hitCountMap.put(id, shots);

                if (shots % 3 == 0) {
                    event.getProjectile().getPersistentDataContainer().set(ANCHOR_ARROW_KEY, PersistentDataType.BYTE, (byte) 1);
                    player.sendMessage("§c§l💥 Mũi tên thứ 3: §4§lSỨC MẠNH NEO HỒI SINH ĐÃ ĐƯỢC KÍCH HOẠT!");
                }
            }
        }

        // Nếu người bắn là Boss Skeleton, đếm số lần bắn để kích hoạt mũi tên Neo Hồi Sinh (đòn thứ 3)
        if (event.getEntity() instanceof Skeleton skeleton && isBossSkeleton(skeleton)) {
            UUID id = skeleton.getUniqueId();
            int shots = hitCountMap.getOrDefault(id, 0) + 1;
            hitCountMap.put(id, shots);

            if (shots % 3 == 0) {
                event.getProjectile().getPersistentDataContainer().set(ANCHOR_ARROW_KEY, PersistentDataType.BYTE, (byte) 1);
            }
        }
    }

    @EventHandler
    public void onProjectileHit(ProjectileHitEvent event) {
        Projectile proj = event.getEntity();
        if (!(proj instanceof Arrow arrow)) return;
        
        boolean isExplosive = false;
        boolean isAnchorExplosion = false;

        if (arrow.getPersistentDataContainer().has(ANCHOR_ARROW_KEY, PersistentDataType.BYTE)) {
            isAnchorExplosion = true;
        } else if (arrow.getPersistentDataContainer().has(EXPLOSIVE_ARROW_KEY, PersistentDataType.BYTE)) {
            isExplosive = true;
        } else if (arrow.getShooter() instanceof Skeleton skeleton && isBossSkeleton(skeleton)) {
            isExplosive = true;
        }

        if (isAnchorExplosion) {
            Entity shooter = arrow.getShooter() instanceof Entity ? (Entity) arrow.getShooter() : null;
            // Vụ nổ của Neo Hồi Sinh (Respawn Anchor) có power = 5.0F, kèm theo lửa (setFire = true) và phá block
            arrow.getWorld().createExplosion(arrow.getLocation(), 5.0f, true, true, shooter);
            
            // Hiệu ứng âm thanh và hạt của Neo Hồi Sinh / Nổ mạnh
            arrow.getWorld().playSound(arrow.getLocation(), Sound.BLOCK_RESPAWN_ANCHOR_DEPLETE, 2.0f, 0.5f);
            arrow.getWorld().playSound(arrow.getLocation(), Sound.ENTITY_GENERIC_EXPLODE, 1.5f, 0.8f);
            arrow.getWorld().spawnParticle(Particle.EXPLOSION_EMITTER, arrow.getLocation(), 3);
            arrow.getWorld().spawnParticle(Particle.FLAME, arrow.getLocation(), 50, 1.0, 1.0, 1.0, 0.2);

            // Gửi thông báo cho người chơi nếu bắn trúng người chơi
            if (event.getHitEntity() instanceof Player p) {
                p.sendTitle("", "§4§l💥 Vụ nổ Neo Hồi Sinh!", 4, 20, 8);
            }
            arrow.remove();
        } else if (isExplosive) {
            Entity shooter = arrow.getShooter() instanceof Entity ? (Entity) arrow.getShooter() : null;
            arrow.getWorld().createExplosion(arrow.getLocation(), 2.6f, false, true, shooter);
            arrow.remove();
        }
    }

    // ===================== CHỐNG KẸT TƯỜNG =====================

    @EventHandler
    public void onBossSuffocate(EntityDamageEvent event) {
        if (event.getCause() == org.bukkit.event.entity.EntityDamageEvent.DamageCause.CRAMMING || event.getCause() == org.bukkit.event.entity.EntityDamageEvent.DamageCause.SUFFOCATION) {
            if (isBossSkeleton(event.getEntity())) {
                event.setCancelled(true);
                LivingEntity boss = (LivingEntity) event.getEntity();
                boss.getPersistentDataContainer().set(new org.bukkit.NamespacedKey(plugin, "suffocated"), PersistentDataType.BYTE, (byte) 1);
                boss.setHealth(0);
            }
        }
    }

    // ===================== CHẾT & PHẦN THƯỞNG =====================

    @EventHandler
    public void onEntityDeath(EntityDeathEvent event) {
        if (!isBossSkeleton(event.getEntity())) return;
        UUID id = event.getEntity().getUniqueId();
        hitCountMap.remove(id);
        
        event.getDrops().clear();

        if (event.getEntity().getPersistentDataContainer().has(new org.bukkit.NamespacedKey(plugin, "suffocated"), PersistentDataType.BYTE)) {
            return;
        }

        // Rớt Cung Hủy Diệt
        ItemStack bow = new ItemStack(Material.BOW);
        ItemMeta bowMeta = bow.getItemMeta();
        if (bowMeta != null) {
            bowMeta.setDisplayName("§c§lCung Hủy Diệt");
            bowMeta.addEnchant(Enchantment.POWER, 5, true);
            bowMeta.addEnchant(Enchantment.FLAME, 1, true);
            bowMeta.getPersistentDataContainer().set(EXPLOSIVE_BOW_KEY, PersistentDataType.BYTE, (byte) 1);
            bow.setItemMeta(bowMeta);
        }
        event.getDrops().add(bow);

        // Hiệu ứng chết
        event.getEntity().getWorld().spawnParticle(Particle.EXPLOSION_EMITTER, event.getEntity().getLocation().add(0, 1.5, 0), 3);
        event.getEntity().getWorld().playSound(event.getEntity().getLocation(), Sound.ENTITY_WITHER_DEATH, 0.8f, 1.0f);
        
        // Trao thưởng
        Player killer = event.getEntity().getKiller();
        if (killer != null) {
            killer.giveExpLevels(30);
            economyManager.addBalance(killer, 100000);
            
            killer.sendMessage("§e§l=================================");
            killer.sendMessage("§a✨ §lCHIẾN CÔNG HIỂN HÁCH §a✨");
            killer.sendMessage("§7Bạn đã tiêu diệt §4§l☠ Quỷ Vương Xương ☠");
            killer.sendMessage("");
            killer.sendMessage("§f🎁 §ePhần thưởng nhận được:");
            killer.sendMessage("§7- §a+30 Level Kinh Nghiệm");
            killer.sendMessage("§7- §6+100,000 Tiền");
            killer.sendMessage("§7- §c1x Cung Hủy Diệt §7(Rớt dưới đất)");
            killer.sendMessage("§e§l=================================");
        }
    }

    // ===================== HELPER =====================

    private void updateHealthName(LivingEntity boss, String baseName) {
        if (!boss.isValid()) return;
        double hp = Math.max(0, boss.getHealth());
        double maxHp = 0;
        AttributeInstance maxHpAttr = boss.getAttribute(Attribute.MAX_HEALTH);
        if (maxHpAttr != null) {
            maxHp = maxHpAttr.getValue();
        }
        boss.setCustomName("§4§l☠ " + baseName + " ☠ §c[ " + Math.round(hp) + "/" + Math.round(maxHp) + " ❤ ]");
    }

    @EventHandler
    public void onBossDamageUpdateHealth(org.bukkit.event.entity.EntityDamageEvent event) {
        if (event.isCancelled()) return;
        if (isBossSkeleton(event.getEntity())) {
            LivingEntity boss = (LivingEntity) event.getEntity();
            Bukkit.getScheduler().runTaskLater(plugin, () -> updateHealthName(boss, "Quỷ Vương Xương"), 1L);
        }
    }

    @EventHandler
    public void onBossSkeletonDamage(org.bukkit.event.entity.EntityDamageByEntityEvent event) {
        // Tăng sát thương gấp 3.5 lần khi Boss Skeleton bắn trúng hoặc vụ nổ của nó gây sát thương
        if (event.getDamager() instanceof Arrow arrow) {
            if (arrow.getShooter() instanceof Skeleton skeleton && isBossSkeleton(skeleton)) {
                event.setDamage(event.getDamage() * 3.5);
            }
        } else if (event.getDamager() instanceof Skeleton skeleton && isBossSkeleton(skeleton)) {
            event.setDamage(event.getDamage() * 3.5);
        }
    }

    @EventHandler
    public void onBossRegainHealth(org.bukkit.event.entity.EntityRegainHealthEvent event) {
        if (event.isCancelled()) return;
        if (isBossSkeleton(event.getEntity())) {
            LivingEntity boss = (LivingEntity) event.getEntity();
            Bukkit.getScheduler().runTaskLater(plugin, () -> updateHealthName(boss, "Quỷ Vương Xương"), 1L);
        }
    }

    private void setAttr(LivingEntity entity, Attribute attr, double value) {
        AttributeInstance inst = entity.getAttribute(attr);
        if (inst != null) inst.setBaseValue(value);
    }

    public boolean isBossSkeleton(Entity entity) {
        if (!(entity instanceof Skeleton)) return false;
        return entity.getPersistentDataContainer().has(BOSS_KEY, PersistentDataType.BYTE);
    }
}
