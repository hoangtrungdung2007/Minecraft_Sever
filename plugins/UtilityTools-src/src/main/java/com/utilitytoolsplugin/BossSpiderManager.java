package com.utilitytoolsplugin;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.block.Block;
import org.bukkit.entity.*;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.CreatureSpawnEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.entity.EntityRegainHealthEvent;
import org.bukkit.event.entity.ProjectileHitEvent;
import org.bukkit.event.player.PlayerTeleportEvent;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.Plugin;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.*;

public class BossSpiderManager implements Listener {

    private final Plugin plugin;
    private final EconomyManager economyManager;
    private final NamespacedKey BOSS_KEY;
    private final NamespacedKey WEB_PROJECTILE_KEY;
    private final NamespacedKey GROUNDED_KEY;

    private final Random random = new Random();
    private final Set<UUID> activeSpiders = new HashSet<>();
    private final Map<UUID, Long> webCooldowns = new HashMap<>();

    // Thông số Spider thường (base)
    private static final double BASE_HP = 16.0;

    // Thông số Boss Spider (Nhện Chúa Tà Ác)
    private static final double SCALE = 3.0; // Gấp chính xác 3 lần
    private static final double HP = 160.0; // Gấp 10 lần lượng máu nhện thường (16 * 10)
    private static final double ATTACK_DAMAGE = 25.0; // Sát thương chí mạng cực lớn
    private static final double FOLLOW_RANGE = 40.0; // Gấp 2.5 lần nhện thường (16 * 2.5)
    private static final double MOVEMENT_SPEED = 0.42; // Di chuyển giật cục, nhanh đáng sợ

    private static final double SPAWN_CHANCE = 0.01; // 1% cơ hội xuất hiện thay thế nhện thường

    public BossSpiderManager(Plugin plugin, EconomyManager economyManager) {
        this.plugin = plugin;
        this.economyManager = economyManager;
        this.BOSS_KEY = new NamespacedKey(plugin, "boss_spider");
        this.WEB_PROJECTILE_KEY = new NamespacedKey(plugin, "web_projectile");
        this.GROUNDED_KEY = new NamespacedKey(plugin, "grounded_web");

        startTask();
    }

    // ===================== TASK ĐỊNH KỲ =====================

    private void startTask() {
        new BukkitRunnable() {
            @Override
            public void run() {
                Iterator<UUID> iterator = activeSpiders.iterator();
                while (iterator.hasNext()) {
                    UUID uuid = iterator.next();
                    Entity entity = Bukkit.getEntity(uuid);
                    if (!(entity instanceof Spider spider) || !spider.isValid() || spider.isDead()) {
                        iterator.remove();
                        continue;
                    }

                    // 1. Hiệu ứng nhỏ giọt chất độc xanh neon xèo xèo mỗi khi chạm đất
                    Location headLoc = spider.getLocation().add(0, spider.getHeight() * 0.8, 0);
                    spider.getWorld().spawnParticle(Particle.FALLING_NECTAR, headLoc, 3, 0.4, 0.2, 0.4, 0.05);
                    if (random.nextDouble() < 0.3) {
                        spider.getWorld().playSound(spider.getLocation(), Sound.BLOCK_LAVA_EXTINGUISH, 0.4f, 1.5f);
                        spider.getWorld().spawnParticle(Particle.FALLING_NECTAR, spider.getLocation(), 5, 0.5, 0.1, 0.5, 0.1);
                    }

                    // 2. Cảm nhận rung động mặt đất (phát hiện mục tiêu xuyên tường/góc khuất trong 40m)
                    LivingEntity target = spider.getTarget();
                    if (target == null || !target.isValid() || target.isDead() || target.getWorld() != spider.getWorld() || target.getLocation().distance(spider.getLocation()) > FOLLOW_RANGE) {
                        // Tìm Player gần nhất trong 40m
                        Player bestTarget = null;
                        double bestDist = FOLLOW_RANGE * FOLLOW_RANGE;
                        for (Player p : spider.getWorld().getPlayers()) {
                            if (p.isDead() || !p.isValid()) continue;
                            double dSq = p.getLocation().distanceSquared(spider.getLocation());
                            if (dSq < bestDist) {
                                bestDist = dSq;
                                bestTarget = p;
                            }
                        }
                        if (bestTarget != null) {
                            spider.setTarget(bestTarget);
                            target = bestTarget;
                        }
                    }

                    // 3. Kỹ năng tầm xa: Phun bãi tơ nhện thối rữa (nếu khoảng cách > 5 block)
                    if (target instanceof Player player && target.isValid() && !target.isDead()) {
                        double dist = spider.getLocation().distance(target.getLocation());
                        if (dist > 5.0 && dist <= FOLLOW_RANGE) {
                            long now = System.currentTimeMillis();
                            long lastWeb = webCooldowns.getOrDefault(uuid, 0L);
                            if (now - lastWeb >= 12000L) { // Cooldown 12 giây
                                webCooldowns.put(uuid, now);

                                // Phun tơ (dùng Snowball giả lập bãi tơ)
                                Snowball webBall = spider.launchProjectile(Snowball.class);
                                webBall.getPersistentDataContainer().set(WEB_PROJECTILE_KEY, PersistentDataType.BYTE, (byte) 1);
                                webBall.setItem(new org.bukkit.inventory.ItemStack(Material.COBWEB));
                                webBall.setVelocity(target.getLocation().add(0, 1.0, 0).toVector().subtract(spider.getLocation().add(0, spider.getHeight() * 0.8, 0).toVector()).normalize().multiply(1.8));

                                spider.getWorld().playSound(spider.getLocation(), Sound.ENTITY_LLAMA_SPIT, 1.5f, 0.5f);
                                spider.getWorld().playSound(spider.getLocation(), Sound.ENTITY_SPIDER_STEP, 1.5f, 0.5f);
                            }
                        }
                    }
                }
            }
        }.runTaskTimer(plugin, 20L, 10L); // Chạy mỗi 10 ticks (0.5 giây)
    }

    // ===================== SPAWN =====================

    @EventHandler
    public void onCreatureSpawn(CreatureSpawnEvent event) {
        if (event.getEntityType() != EntityType.SPIDER) return;
        if (event.getSpawnReason() != CreatureSpawnEvent.SpawnReason.NATURAL
                && event.getSpawnReason() != CreatureSpawnEvent.SpawnReason.DEFAULT
                && event.getSpawnReason() != CreatureSpawnEvent.SpawnReason.SPAWNER_EGG
                && event.getSpawnReason() != CreatureSpawnEvent.SpawnReason.COMMAND) return;
        if (random.nextDouble() > SPAWN_CHANCE) return;

        Spider spider = (Spider) event.getEntity();
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (spider.isValid()) {
                makeBossSpider(spider);
            }
        }, 1L);
    }

    public void makeBossSpider(Spider spider) {
        spider.getPersistentDataContainer().set(BOSS_KEY, PersistentDataType.BYTE, (byte) 1);
        activeSpiders.add(spider.getUniqueId());

        // Máu (160 HP)
        setAttr(spider, Attribute.MAX_HEALTH, HP);
        spider.setHealth(HP);

        // Tên hiển thị kèm thanh máu
        updateHealthName(spider, "Nhện Chúa Tà Ác");
        spider.setCustomNameVisible(true);
        spider.setRemoveWhenFarAway(true);

        // Kích thước x3
        setAttr(spider, Attribute.SCALE, SCALE);

        // Sát thương x25
        setAttr(spider, Attribute.ATTACK_DAMAGE, ATTACK_DAMAGE);

        // Phạm vi phát hiện 40m
        setAttr(spider, Attribute.FOLLOW_RANGE, FOLLOW_RANGE);

        // Tốc độ di chuyển 0.42 (rất nhanh)
        setAttr(spider, Attribute.MOVEMENT_SPEED, MOVEMENT_SPEED);

        // Kháng bật lùi (Knockback Resistance)
        setAttr(spider, Attribute.KNOCKBACK_RESISTANCE, 0.85);

        // Hiệu ứng spawn
        spider.getWorld().spawnParticle(Particle.SMOKE, spider.getLocation().add(0, 1.5, 0), 30, 1.0, 1.0, 1.0, 0.1);
        spider.getWorld().spawnParticle(Particle.FALLING_NECTAR, spider.getLocation().add(0, 1.5, 0), 30, 1.0, 1.0, 1.0, 0.1);
        spider.getWorld().playSound(spider.getLocation(), Sound.ENTITY_WITHER_SPAWN, 0.8f, 1.5f);
        spider.getWorld().playSound(spider.getLocation(), Sound.ENTITY_SPIDER_AMBIENT, 1.5f, 0.5f);
    }

    // ===================== KỸ NĂNG & HIỆU ỨNG ĐÒN ĐÁNH =====================

    @EventHandler
    public void onEntityDamageByEntity(EntityDamageByEntityEvent event) {
        if (event.getDamager() instanceof Spider spider && isBossSpider(spider)) {
            if (event.getEntity() instanceof Player player) {
                // Sát thương vật lý chí mạng từ cú cắn (đã cấu hình ATTACK_DAMAGE = 25.0)
                
                // Trúng độc (Poison): Kéo dài 15 giây, bỏ qua 20% giáp
                // Thêm PotionEffect POISON
                player.addPotionEffect(new PotionEffect(PotionEffectType.POISON, 300, 1));
                
                // BukkitRunnable gây sát thương chuẩn (bỏ qua giáp) mỗi giây trong 15 giây
                new BukkitRunnable() {
                    int ticks = 0;
                    @Override
                    public void run() {
                        if (ticks >= 15 || !player.isOnline() || player.isDead()) {
                            this.cancel();
                            return;
                        }
                        ticks++;
                        // Trừ thẳng vào máu (bỏ qua 20% giáp / gây sát thương chuẩn)
                        double newHp = Math.max(0, player.getHealth() - 2.0);
                        player.setHealth(newHp);
                        player.getWorld().spawnParticle(Particle.FALLING_NECTAR, player.getLocation().add(0, 1, 0), 5, 0.3, 0.3, 0.3, 0.05);
                    }
                }.runTaskTimer(plugin, 20L, 20L);

                player.sendTitle("", "§c§l☠ Bị cắn! Trúng độc ăn mòn!", 4, 20, 8);
            }
        }
    }

    // Bắt sự kiện Projectile trúng đích (Bãi tơ thối rữa)
    @EventHandler
    public void onProjectileHit(ProjectileHitEvent event) {
        Projectile proj = event.getEntity();
        if (proj.getPersistentDataContainer().has(WEB_PROJECTILE_KEY, PersistentDataType.BYTE)) {
            Location loc = event.getHitEntity() != null ? event.getHitEntity().getLocation() : (event.getHitBlock() != null ? event.getHitBlock().getLocation().add(0, 1, 0) : proj.getLocation());
            
            proj.getWorld().playSound(loc, Sound.ENTITY_SLIME_SQUISH, 1.5f, 0.5f);
            proj.getWorld().spawnParticle(Particle.FALLING_NECTAR, loc, 30, 1.5, 1.0, 1.5, 0.1);

            // Gây Slow 60% và khóa cơ động cho các player gần đó trong bán kính 3 block
            for (Player player : loc.getWorld().getPlayers()) {
                if (player.getLocation().distanceSquared(loc) <= 16.0) {
                    // Giảm 60% tốc độ di chuyển (Slowness 4) trong 8 giây (160 ticks)
                    player.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, 160, 3));
                    
                    // Khóa kỹ năng cơ động (Grounded) trong 8 giây
                    player.getPersistentDataContainer().set(GROUNDED_KEY, PersistentDataType.BYTE, (byte) 1);
                    player.sendMessage("§c§l🕸️ Bị tơ nhện thối rữa trói chặt! Giảm 60% tốc độ và khóa lướt/dịch chuyển trong 8 giây!");
                    
                    Bukkit.getScheduler().runTaskLater(plugin, () -> {
                        if (player.isOnline()) {
                            player.getPersistentDataContainer().remove(GROUNDED_KEY);
                        }
                    }, 160L);
                }
            }

            // Đặt tạm 1 block COBWEB tại vị trí, tự xóa sau 8 giây để không dơ map
            Block block = loc.getBlock();
            if (block.getType() == Material.AIR) {
                block.setType(Material.COBWEB);
                Bukkit.getScheduler().runTaskLater(plugin, () -> {
                    if (block.getType() == Material.COBWEB) {
                        block.setType(Material.AIR);
                    }
                }, 160L);
            }

            proj.remove();
        }
    }

    // Bắt sự kiện Teleport để khóa kỹ năng cơ động (như lướt/dịch chuyển)
    @EventHandler
    public void onPlayerTeleport(PlayerTeleportEvent event) {
        Player player = event.getPlayer();
        if (player.getPersistentDataContainer().has(GROUNDED_KEY, PersistentDataType.BYTE)) {
            // Cho phép teleport nếu là plugin hệ thống quan trọng, nhưng chặn các kỹ năng cơ động/Ender Pearl
            if (event.getCause() == PlayerTeleportEvent.TeleportCause.ENDER_PEARL || 
                event.getCause() == PlayerTeleportEvent.TeleportCause.CHORUS_FRUIT || 
                event.getCause() == PlayerTeleportEvent.TeleportCause.COMMAND || 
                event.getCause() == PlayerTeleportEvent.TeleportCause.PLUGIN) {
                event.setCancelled(true);
                player.sendMessage("§c§l🕸️ Chân bạn đã bị tơ nhện ăn mòn trói chặt! Không thể dịch chuyển hay lướt!");
            }
        }
    }

    // ===================== CHỐNG KẸT TƯỜNG =====================

    @EventHandler
    public void onBossSuffocate(EntityDamageEvent event) {
        if (event.getCause() == EntityDamageEvent.DamageCause.CRAMMING || event.getCause() == EntityDamageEvent.DamageCause.SUFFOCATION) {
            if (isBossSpider(event.getEntity())) {
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
        if (!isBossSpider(event.getEntity())) return;
        UUID id = event.getEntity().getUniqueId();
        activeSpiders.remove(id);
        webCooldowns.remove(id);
        
        event.getDrops().clear();

        if (event.getEntity().getPersistentDataContainer().has(new org.bukkit.NamespacedKey(plugin, "suffocated"), PersistentDataType.BYTE)) {
            return;
        }

        Location loc = event.getEntity().getLocation();

        // Hiệu ứng chết & Nổ ra đàn nhện con từ trong bụng
        loc.getWorld().spawnParticle(Particle.EXPLOSION_EMITTER, loc.add(0, 1.5, 0), 3);
        loc.getWorld().playSound(loc, Sound.ENTITY_WITHER_DEATH, 1.0f, 0.8f);
        loc.getWorld().playSound(loc, Sound.ENTITY_SPIDER_DEATH, 1.5f, 0.5f);

        for (int i = 0; i < 6; i++) {
            CaveSpider baby = (CaveSpider) loc.getWorld().spawnEntity(loc, EntityType.CAVE_SPIDER);
            baby.setCustomName("§cNhện Con Tà Ác");
            baby.setCustomNameVisible(true);
        }

        // Trao thưởng cho người kết liễu & Thông báo toàn Server
        Player killer = event.getEntity().getKiller();
        if (killer != null) {
            // Cộng thẳng +100 Cấp độ
            killer.giveExpLevels(100);
            
            // Cộng 1.000.000 Tiền trong game
            economyManager.addBalance(killer, 1000000);
            
            killer.sendMessage("§e§l=================================");
            killer.sendMessage("§a✨ §lCHIẾN CÔNG HUYỀN THOẠI §a✨");
            killer.sendMessage("§7Bạn đã tiêu diệt §4§l☠ Nhện Chúa Tà Ác ☠");
            killer.sendMessage("");
            killer.sendMessage("§f🎁 §ePhần thưởng nhận được:");
            killer.sendMessage("§7- §a+100 Level Kinh Nghiệm");
            killer.sendMessage("§7- §6+1,000,000 Tiền");
            killer.sendMessage("§e§l=================================");

            // Thông báo toàn Server (Siêu ngầu): Gửi một dòng thông báo chữ màu đỏ đậm, hiệu ứng nhấp nháy nổi bật
            String broadcastMsg = "§4§l§k|§r §4§l[HUYỀN THOẠI] ⚔️ Kẻ Hủy Diệt " + killer.getName() + " đã xé toạc bóng đêm, nghiền nát Nhện Chúa Tà Ác và chấm dứt cơn ác mộng ngàn năm! 🕸️💀 §4§l§k|";
            Bukkit.broadcastMessage(broadcastMsg);
            
            for (Player p : Bukkit.getOnlinePlayers()) {
                p.playSound(p.getLocation(), Sound.UI_TOAST_CHALLENGE_COMPLETE, 1.0f, 1.0f);
            }
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
    public void onBossDamageUpdateHealth(EntityDamageEvent event) {
        if (event.isCancelled()) return;
        if (isBossSpider(event.getEntity())) {
            LivingEntity boss = (LivingEntity) event.getEntity();
            Bukkit.getScheduler().runTaskLater(plugin, () -> updateHealthName(boss, "Nhện Chúa Tà Ác"), 1L);
        }
    }

    @EventHandler
    public void onBossRegainHealth(EntityRegainHealthEvent event) {
        if (event.isCancelled()) return;
        if (isBossSpider(event.getEntity())) {
            LivingEntity boss = (LivingEntity) event.getEntity();
            Bukkit.getScheduler().runTaskLater(plugin, () -> updateHealthName(boss, "Nhện Chúa Tà Ác"), 1L);
        }
    }

    private void setAttr(LivingEntity entity, Attribute attr, double value) {
        AttributeInstance inst = entity.getAttribute(attr);
        if (inst != null) inst.setBaseValue(value);
    }

    public boolean isBossSpider(Entity entity) {
        if (!(entity instanceof Spider)) return false;
        return entity.getPersistentDataContainer().has(BOSS_KEY, PersistentDataType.BYTE);
    }
}
