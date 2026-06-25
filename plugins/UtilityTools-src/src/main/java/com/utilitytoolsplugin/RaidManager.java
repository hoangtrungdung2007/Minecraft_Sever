package com.utilitytoolsplugin;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.boss.BarColor;
import org.bukkit.boss.BarStyle;
import org.bukkit.boss.BossBar;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import org.bukkit.entity.Skeleton;
import org.bukkit.entity.Spider;
import org.bukkit.entity.Zombie;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.util.Vector;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.UUID;

public class RaidManager implements CommandExecutor, Listener {

    private final UtilityToolsPlugin plugin;
    private final EconomyManager economyManager;
    private final Random random = new Random();

    private boolean isRaidActive = false;
    private Location raidCenter = null;
    private BukkitTask raidTask = null;
    private BossBar raidBossBar = null;

    private final Set<UUID> raidBosses = new HashSet<>();
    private final Set<UUID> participants = new HashSet<>();
    private final Map<UUID, Long> lastWallWarning = new HashMap<>();

    public RaidManager(UtilityToolsPlugin plugin, EconomyManager economyManager) {
        this.plugin = plugin;
        this.economyManager = economyManager;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(ChatColor.RED + "Lệnh này chỉ dành cho người chơi!");
            return true;
        }

        if (!player.hasPermission("utilitytoolsplugin.raid") && !player.isOp()) {
            player.sendMessage(ChatColor.RED + "Bạn không có quyền dùng lệnh này!");
            return true;
        }

        if (args.length > 0 && args[0].equalsIgnoreCase("stop")) {
            if (isRaidActive) {
                stopRaid();
                player.sendMessage(ChatColor.GREEN + "Đã hủy Raid hiện tại!");
            } else {
                player.sendMessage(ChatColor.RED + "Không có Raid nào đang diễn ra!");
            }
            return true;
        }

        if (isRaidActive) {
            player.sendMessage(ChatColor.RED + "Một cuộc Raid đang diễn ra! Hãy hoàn thành nó hoặc gõ /raid stop.");
            return true;
        }

        startRaid(player);
        return true;
    }

    private void startRaid(Player player) {
        isRaidActive = true;
        raidBosses.clear();
        participants.clear();
        lastWallWarning.clear();

        raidCenter = player.getLocation();
        World world = raidCenter.getWorld();

        // 1. Trời tối lại và bắt đầu bão tố
        world.setTime(18000); // Nửa đêm
        world.setStorm(true);
        world.setThundering(true);

        // 2. Tạo Boss Bar hiển thị tiến trình Raid
        if (raidBossBar != null) raidBossBar.removeAll();
        raidBossBar = Bukkit.createBossBar("§4§l⚔ CUỘC ĐỘT KÍCH CỦA QUỶ VƯƠNG ⚔ §c(5/5 Boss)", BarColor.RED, BarStyle.SEGMENTED_10);
        raidBossBar.setProgress(1.0);
        for (Player p : world.getPlayers()) {
            raidBossBar.addPlayer(p);
        }

        for (int i = 0; i < 5; i++) {
            // Spawn random offset
            double offsetX = (random.nextDouble() - 0.5) * 20;
            double offsetZ = (random.nextDouble() - 0.5) * 20;
            Location spawnLoc = raidCenter.clone().add(offsetX, 1, offsetZ);

            int r = random.nextInt(3);
            if (r == 0) {
                Zombie zombie = (Zombie) world.spawnEntity(spawnLoc, EntityType.ZOMBIE);
                plugin.getBossZombieManager().makeBossZombie(zombie);
                zombie.setRemoveWhenFarAway(false);
                raidBosses.add(zombie.getUniqueId());
            } else if (r == 1) {
                Skeleton skeleton = (Skeleton) world.spawnEntity(spawnLoc, EntityType.SKELETON);
                plugin.getBossSkeletonManager().makeBossSkeleton(skeleton);
                skeleton.setRemoveWhenFarAway(false);
                raidBosses.add(skeleton.getUniqueId());
            } else {
                Spider spider = (Spider) world.spawnEntity(spawnLoc, EntityType.SPIDER);
                plugin.getBossSpiderManager().makeBossSpider(spider);
                spider.setRemoveWhenFarAway(false);
                raidBosses.add(spider.getUniqueId());
            }
        }

        Bukkit.broadcastMessage("§c§l=================================");
        Bukkit.broadcastMessage("§4§l⚠ CẢNH BÁO: CUỘC ĐỘT KÍCH ĐÃ BẮT ĐẦU ⚠");
        Bukkit.broadcastMessage("§eMột nhóm 5 Quỷ Vương đã xuất hiện tại:");
        Bukkit.broadcastMessage("§fX: " + raidCenter.getBlockX() + ", Y: " + raidCenter.getBlockY() + ", Z: " + raidCenter.getBlockZ());
        Bukkit.broadcastMessage("§c§l🛑 KHU VỰC ĐÃ BỊ PHONG TỎA! GIỚI HẠN BÁN KÍNH 50 BLOCK & ĐỘ CAO 50 BLOCK!");
        Bukkit.broadcastMessage("§aTham gia tiêu diệt để nhận thưởng: §6§l+10,000,000 Tiền & +100 Level!");
        Bukkit.broadcastMessage("§c§l=================================");

        for (Player p : Bukkit.getOnlinePlayers()) {
            p.playSound(p.getLocation(), Sound.ENTITY_ENDER_DRAGON_GROWL, 1.0f, 0.8f);
            p.sendTitle("§4§lRAID BOSS", "§c5 Quỷ Vương Đã Xuất Hiện!", 10, 70, 20);
        }

        // 3. Task định kỳ: Sét đánh, giữ boss trong vùng, hiển thị tường mờ mờ và cập nhật BossBar
        if (raidTask != null) raidTask.cancel();
        raidTask = new BukkitRunnable() {
            int tickCounter = 0;
            @Override
            public void run() {
                if (!isRaidActive || raidCenter == null) {
                    this.cancel();
                    return;
                }
                tickCounter++;

                // Giữ trời luôn tối trong suốt quá trình Raid
                if (world.getTime() < 14000 || world.getTime() > 22000) {
                    world.setTime(18000);
                }
                world.setStorm(true);
                world.setThundering(true);

                // Thêm người chơi mới vào BossBar
                if (raidBossBar != null) {
                    for (Player p : world.getPlayers()) {
                        if (!raidBossBar.getPlayers().contains(p)) {
                            raidBossBar.addPlayer(p);
                        }
                    }
                }

                // A. Sét đánh ngẫu nhiên và tỉ lệ trúng người chơi (mỗi 30 ticks = 1.5 giây)
                if (tickCounter % 3 == 0) {
                    double rX = (random.nextDouble() - 0.5) * 60;
                    double rZ = (random.nextDouble() - 0.5) * 60;
                    Location lightningLoc = raidCenter.clone().add(rX, 0, rZ);
                    world.strikeLightningEffect(world.getHighestBlockAt(lightningLoc).getLocation());

                    if (random.nextDouble() < 0.25) {
                        for (Player p : world.getPlayers()) {
                            if (p.isDead() || !p.isValid()) continue;
                            if (p.getLocation().distanceSquared(raidCenter) <= 2500.0) { // Trong bán kính 50 block
                                if (random.nextBoolean()) {
                                    world.strikeLightning(p.getLocation());
                                    // Đã tắt thông báo sét đánh trúng người chơi
                                    break;
                                }
                            }
                        }
                    }
                }

                // B. Giữ Boss không được ra khỏi vùng Raid (bán kính 50, độ cao 50)
                for (UUID bossUuid : raidBosses) {
                    Entity boss = Bukkit.getEntity(bossUuid);
                    if (boss != null && boss.isValid() && !boss.isDead()) {
                        double distSq = boss.getLocation().distanceSquared(raidCenter);
                        if (distSq > 50.0 * 50.0 || boss.getLocation().getY() > raidCenter.getY() + 50.0) {
                            boss.teleport(raidCenter.clone().add(0, 1, 0));
                        }
                    }
                }

                // C. Tường Raid luôn hiện (bán kính 50) và trên cao (độ cao 50)
                // Spawn lồng trụ bảo vệ xung quanh toàn bộ chu vi 50 block để luôn nhìn thấy tường từ bất kỳ đâu
                double cX = raidCenter.getX();
                double cY = raidCenter.getY();
                double cZ = raidCenter.getZ();
                
                for (double angle = 0; angle < Math.PI * 2; angle += 0.25) {
                    double wX = cX + 50.0 * Math.cos(angle);
                    double wZ = cZ + 50.0 * Math.sin(angle);
                    
                    // Tạo các điểm hạt theo chiều dọc của tường (các tầng cách nhau 5 block)
                    for (int yOffset = 0; yOffset <= 50; yOffset += 5) {
                        Location wallLoc = new Location(world, wX, cY + yOffset, wZ);
                        world.spawnParticle(Particle.CLOUD, wallLoc, 1, 0, 0, 0, 0.01);
                        world.spawnParticle(Particle.PORTAL, wallLoc, 1, 0.1, 0.1, 0.1, 0.05);
                    }
                }

                // Hiển thị lưới trần trên cao (Y = cY + 50)
                double ceilY = cY + 50.0;
                for (double oX = -40; oX <= 40; oX += 10) {
                    for (double oZ = -40; oZ <= 40; oZ += 10) {
                        if (oX * oX + oZ * oZ <= 50.0 * 50.0) {
                            Location ceilLoc = new Location(world, cX + oX, ceilY, cZ + oZ);
                            world.spawnParticle(Particle.CLOUD, ceilLoc, 1, 0, 0, 0, 0.01);
                            world.spawnParticle(Particle.PORTAL, ceilLoc, 2, 0.1, 0, 0.1, 0.05);
                        }
                    }
                }
            }
        }.runTaskTimer(plugin, 10L, 10L); // Chạy mỗi 0.5 giây (10 ticks)
    }

    private void stopRaid() {
        isRaidActive = false;
        raidBosses.clear();
        participants.clear();
        lastWallWarning.clear();
        if (raidBossBar != null) {
            raidBossBar.removeAll();
            raidBossBar = null;
        }
        if (raidTask != null) {
            raidTask.cancel();
            raidTask = null;
        }
        if (raidCenter != null) {
            World world = raidCenter.getWorld();
            world.setTime(6000); // Trở lại ban ngày
            world.setStorm(false);
            world.setThundering(false);
            raidCenter = null;
        }
    }

    @EventHandler
    public void onPlayerMove(PlayerMoveEvent event) {
        if (!isRaidActive || raidCenter == null) return;
        Player player = event.getPlayer();
        if (player.getWorld() != raidCenter.getWorld()) return;

        Location to = event.getTo();
        if (to == null) return;

        // Bán kính phong tỏa: 50 block
        double distX = to.getX() - raidCenter.getX();
        double distZ = to.getZ() - raidCenter.getZ();
        double distSq = distX * distX + distZ * distZ;
        double maxRadius = 50.0;

        boolean outOfBounds = false;
        String warningMsg = null;

        if (distSq > maxRadius * maxRadius) {
            outOfBounds = true;
            warningMsg = "§c§l🛑 LỰC LƯỢNG HẮC ÁM ĐÃ PHONG TỎA KHU VỰC! BẠN KHÔNG THỂ THÁO CHẠY KHI RAID CHƯA KẾT THÚC!";
        }

        // Giới hạn độ cao bay: 50 block so với tâm Raid
        if (to.getY() > raidCenter.getY() + 50.0) {
            outOfBounds = true;
            warningMsg = "§c§l🛑 KHÔNG PHẬN ĐÃ BỊ PHONG TỎA! BẠN KHÔNG THỂ BAY CAO QUÁ 50 BLOCK TRONG RAID!";
        }

        if (outOfBounds) {
            // Hất người chơi giật ngược lại về phía tâm Raid (tắt âm thanh theo yêu cầu để tránh ồn)
            Vector push = raidCenter.toVector().subtract(player.getLocation().toVector()).normalize().multiply(0.8).setY(0.2);
            player.setVelocity(push);
            event.setCancelled(true);

            // Gửi tin nhắn cảnh báo (giãn cách 3 giây để tránh spam)
            long now = System.currentTimeMillis();
            long lastWarn = lastWallWarning.getOrDefault(player.getUniqueId(), 0L);
            if (now - lastWarn > 3000L && warningMsg != null) {
                lastWallWarning.put(player.getUniqueId(), now);
                player.sendMessage(warningMsg);
            }
        }
    }

    @EventHandler
    public void onPlayerDeath(PlayerDeathEvent event) {
        if (!isRaidActive || raidCenter == null) return;
        Player player = event.getEntity();
        if (player.getWorld() != raidCenter.getWorld()) return;

        // Kiểm tra nếu chết trong vùng Raid (bán kính 50 block)
        if (player.getLocation().distanceSquared(raidCenter) <= 50.0 * 50.0) {
            // Khi chết sẽ mất đồ
            event.setKeepInventory(false);
            event.getDrops().clear();

            // Bị trừ 1M tiền
            if (!economyManager.removeBalance(player, 1000000)) {
                economyManager.addBalance(player, -economyManager.getBalance(player)); // Trừ sạch nếu không đủ 1M
            }

            player.sendMessage("§c§l☠ BẠN ĐÃ TỬ TRẬN TRONG CUỘC ĐỘT KÍCH! Toàn bộ trang bị đã bị phá hủy và bị trừ 1,000,000 Tiền!");
        }
    }

    @EventHandler
    public void onEntityDamage(EntityDamageByEntityEvent event) {
        if (!isRaidActive) return;

        Entity victim = event.getEntity();
        if (raidBosses.contains(victim.getUniqueId())) {
            Entity damager = event.getDamager();
            
            if (damager instanceof Player player) {
                participants.add(player.getUniqueId());
            } else if (damager instanceof org.bukkit.entity.Projectile proj) {
                if (proj.getShooter() instanceof Player player) {
                    participants.add(player.getUniqueId());
                }
            }
        }
    }

    @EventHandler
    public void onEntityDeath(EntityDeathEvent event) {
        if (!isRaidActive) return;

        UUID deadId = event.getEntity().getUniqueId();
        if (raidBosses.contains(deadId)) {
            raidBosses.remove(deadId);

            int remaining = raidBosses.size();
            Bukkit.broadcastMessage("§e⚔ Một Boss Raid đã bị tiêu diệt! Còn lại: §c" + remaining + " Boss");

            if (raidBossBar != null) {
                raidBossBar.setProgress((double) remaining / 5.0);
                raidBossBar.setTitle("§4§l⚔ CUỘC ĐỘT KÍCH CỦA QUỶ VƯƠNG ⚔ §c(" + remaining + "/5 Boss)");
            }

            if (remaining == 0) {
                // Raid hoàn thành
                finishRaid();
            }
        }
    }

    private void finishRaid() {
        isRaidActive = false;
        if (raidBossBar != null) {
            raidBossBar.removeAll();
            raidBossBar = null;
        }
        if (raidTask != null) {
            raidTask.cancel();
            raidTask = null;
        }

        if (raidCenter != null) {
            World world = raidCenter.getWorld();
            world.setTime(6000); // Trở lại ban ngày (trời quang mây tạnh)
            world.setStorm(false);
            world.setThundering(false);
            raidCenter = null;
        }

        Bukkit.broadcastMessage("§a§l=================================");
        Bukkit.broadcastMessage("§6§l🎉 CUỘC ĐỘT KÍCH ĐÃ BỊ ĐẨY LÙI 🎉");
        Bukkit.broadcastMessage("§eCả 5 Quỷ Vương đã bị tiêu diệt! Bầu trời đã trong xanh trở lại!");
        Bukkit.broadcastMessage("§a§l=================================");

        for (UUID uuid : participants) {
            Player p = Bukkit.getPlayer(uuid);
            if (p != null && p.isOnline()) {
                // Thắng mỗi người sẽ đc cộng 10M và 100lv kinh nghiệm
                p.giveExpLevels(100);
                economyManager.addBalance(p, 10000000); // 10M
                
                p.sendMessage("§e§l=================================");
                p.sendMessage("§a✨ §lPHẦN THƯỞNG CHIẾN THẮNG RAID BOSS §a✨");
                p.sendMessage("§f🎁 §eBạn nhận được:");
                p.sendMessage("§7- §a+100 Level Kinh Nghiệm");
                p.sendMessage("§7- §6+10,000,000 Tiền");
                p.sendMessage("§e§l=================================");
                
                p.playSound(p.getLocation(), Sound.UI_TOAST_CHALLENGE_COMPLETE, 1.0f, 1.0f);
            }
        }

        participants.clear();
        lastWallWarning.clear();
    }
}
