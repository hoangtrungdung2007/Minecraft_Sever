package com.utilitytoolsplugin;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.Sound;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import org.bukkit.entity.Skeleton;
import org.bukkit.entity.Zombie;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDeathEvent;

import java.util.HashSet;
import java.util.Random;
import java.util.Set;
import java.util.UUID;

public class RaidManager implements CommandExecutor, Listener {

    private final UtilityToolsPlugin plugin;
    private final EconomyManager economyManager;
    private final Random random = new Random();

    private boolean isRaidActive = false;
    private final Set<UUID> raidBosses = new HashSet<>();
    private final Set<UUID> participants = new HashSet<>();

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

        Location center = player.getLocation();

        for (int i = 0; i < 5; i++) {
            // Spawn random offset
            double offsetX = (random.nextDouble() - 0.5) * 15;
            double offsetZ = (random.nextDouble() - 0.5) * 15;
            Location spawnLoc = center.clone().add(offsetX, 1, offsetZ);

            if (random.nextBoolean()) {
                Zombie zombie = (Zombie) center.getWorld().spawnEntity(spawnLoc, EntityType.ZOMBIE);
                plugin.getBossZombieManager().makeBossZombie(zombie);
                zombie.setRemoveWhenFarAway(false);
                raidBosses.add(zombie.getUniqueId());
            } else {
                Skeleton skeleton = (Skeleton) center.getWorld().spawnEntity(spawnLoc, EntityType.SKELETON);
                plugin.getBossSkeletonManager().makeBossSkeleton(skeleton);
                skeleton.setRemoveWhenFarAway(false);
                raidBosses.add(skeleton.getUniqueId());
            }
        }

        Bukkit.broadcastMessage("§c§l=================================");
        Bukkit.broadcastMessage("§4§l⚠ CẢNH BÁO: CUỘC ĐỘT KÍCH ĐÃ BẮT ĐẦU ⚠");
        Bukkit.broadcastMessage("§eMột nhóm 5 Quỷ Vương đã xuất hiện tại:");
        Bukkit.broadcastMessage("§fX: " + center.getBlockX() + ", Y: " + center.getBlockY() + ", Z: " + center.getBlockZ());
        Bukkit.broadcastMessage("§aTham gia tiêu diệt để nhận phần thưởng khổng lồ!");
        Bukkit.broadcastMessage("§c§l=================================");

        for (Player p : Bukkit.getOnlinePlayers()) {
            p.playSound(p.getLocation(), Sound.ENTITY_ENDER_DRAGON_GROWL, 1.0f, 0.8f);
            p.sendTitle("§4§lRAID BOSS", "§c5 Quỷ Vương Đã Xuất Hiện!", 10, 70, 20);
        }
    }

    private void stopRaid() {
        isRaidActive = false;
        raidBosses.clear();
        participants.clear();
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

            if (remaining == 0) {
                // Raid hoàn thành
                finishRaid();
            }
        }
    }

    private void finishRaid() {
        isRaidActive = false;

        Bukkit.broadcastMessage("§a§l=================================");
        Bukkit.broadcastMessage("§6§l🎉 CUỘC ĐỘT KÍCH ĐÃ BỊ ĐẨY LÙI 🎉");
        Bukkit.broadcastMessage("§eCả 5 Quỷ Vương đã bị tiêu diệt!");
        Bukkit.broadcastMessage("§a§l=================================");

        for (UUID uuid : participants) {
            Player p = Bukkit.getPlayer(uuid);
            if (p != null && p.isOnline()) {
                p.giveExpLevels(100);
                economyManager.addBalance(p, 100000);
                
                p.sendMessage("§e§l=================================");
                p.sendMessage("§a✨ §lPHẦN THƯỞNG RAID BOSS §a✨");
                p.sendMessage("§f🎁 §eBạn nhận được:");
                p.sendMessage("§7- §a+100 Level Kinh Nghiệm");
                p.sendMessage("§7- §6+100,000 Tiền");
                p.sendMessage("§e§l=================================");
                
                p.playSound(p.getLocation(), Sound.UI_TOAST_CHALLENGE_COMPLETE, 1.0f, 1.0f);
            }
        }

        participants.clear();
    }
}
