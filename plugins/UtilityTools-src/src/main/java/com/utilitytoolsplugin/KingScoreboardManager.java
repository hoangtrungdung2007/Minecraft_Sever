package com.utilitytoolsplugin;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Statistic;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scoreboard.DisplaySlot;
import org.bukkit.scoreboard.Objective;
import org.bukkit.scoreboard.Scoreboard;
import org.bukkit.scoreboard.Team;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerQuitEvent;

public class KingScoreboardManager implements Listener {

    private final Plugin plugin;
    private final EconomyManager economyManager;
    private final Map<UUID, Scoreboard> scoreboards = new HashMap<>();

    public KingScoreboardManager(Plugin plugin, EconomyManager economyManager) {
        this.plugin = plugin;
        this.economyManager = economyManager;
        startUpdateTask();
        Bukkit.getPluginManager().registerEvents(this, plugin);
    }

    public void setupScoreboard(Player player) {
        Scoreboard board = Bukkit.getScoreboardManager().getNewScoreboard();
        Objective obj = board.registerNewObjective("kingmc", "dummy", ChatColor.YELLOW + ChatColor.BOLD.toString() + "DŨNG ĐẸP TRAI");
        obj.setDisplaySlot(DisplaySlot.SIDEBAR);

        // Blank line
        obj.getScore("§1").setScore(7);

        // Money
        Team moneyTeam = board.registerNewTeam("money");
        String moneyKey = "§aTiền: §f";
        moneyTeam.addEntry(moneyKey);
        obj.getScore(moneyKey).setScore(6);

        // Days Played
        Team daysTeam = board.registerNewTeam("days");
        String daysKey = "§bNgày chơi: §f";
        daysTeam.addEntry(daysKey);
        obj.getScore(daysKey).setScore(5);

        // Kills
        Team killsTeam = board.registerNewTeam("kills");
        String killsKey = "§cSố kill: §f";
        killsTeam.addEntry(killsKey);
        obj.getScore(killsKey).setScore(4);

        // Deaths
        Team deathsTeam = board.registerNewTeam("deaths");
        String deathsKey = "§7Số lần chết: §f";
        deathsTeam.addEntry(deathsKey);
        obj.getScore(deathsKey).setScore(3);

        // Bosses
        Team bossesTeam = board.registerNewTeam("bosses");
        String bossesKey = "§6Số Boss: §f";
        bossesTeam.addEntry(bossesKey);
        obj.getScore(bossesKey).setScore(2);

        // Ping
        Team pingTeam = board.registerNewTeam("ping");
        String pingKey = "§dPing: §f";
        pingTeam.addEntry(pingKey);
        obj.getScore(pingKey).setScore(1);

        player.setScoreboard(board);
        scoreboards.put(player.getUniqueId(), board);
        updateScoreboard(player, 0);
    }

    private String formatMoney(double amount) {
        if (amount >= 1_000_000_000) return String.format("%.1fB", amount / 1_000_000_000.0);
        if (amount >= 1_000_000) return String.format("%.1fM", amount / 1_000_000.0);
        if (amount >= 1_000) return String.format("%.1fK", amount / 1_000.0);
        return String.format("%,.1f", amount);
    }

    public void updateScoreboard(Player player, int activeBosses) {
        Scoreboard board = scoreboards.get(player.getUniqueId());
        if (board == null) return;

        double bal = economyManager.getBalance(player);
        long ticksPlayed = player.getStatistic(Statistic.PLAY_ONE_MINUTE);
        long inGameDays = ticksPlayed / 24000;

        int kills = player.getStatistic(Statistic.PLAYER_KILLS);
        int deaths = player.getStatistic(Statistic.DEATHS);
        int ping = player.getPing();

        board.getTeam("money").setSuffix(formatMoney(bal));
        board.getTeam("days").setSuffix(String.valueOf(inGameDays));
        board.getTeam("kills").setSuffix(String.valueOf(kills));
        board.getTeam("deaths").setSuffix(String.valueOf(deaths));
        board.getTeam("bosses").setSuffix(String.valueOf(activeBosses));
        board.getTeam("ping").setSuffix(ping + " ms");

        if (player.getScoreboard() != board) {
            player.setScoreboard(board);
        }
    }

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        removeScoreboard(event.getPlayer());
    }

    public void removeScoreboard(Player player) {
        scoreboards.remove(player.getUniqueId());
    }

    private void startUpdateTask() {
        new BukkitRunnable() {
            @Override
            public void run() {
                int activeBosses = 0;
                org.bukkit.NamespacedKey zombieKey = new org.bukkit.NamespacedKey(plugin, "boss_zombie");
                org.bukkit.NamespacedKey skeletonKey = new org.bukkit.NamespacedKey(plugin, "boss_skeleton");
                for (org.bukkit.World world : Bukkit.getWorlds()) {
                    for (org.bukkit.entity.LivingEntity entity : world.getLivingEntities()) {
                        if (entity.getPersistentDataContainer().has(zombieKey, org.bukkit.persistence.PersistentDataType.BYTE) ||
                            entity.getPersistentDataContainer().has(skeletonKey, org.bukkit.persistence.PersistentDataType.BYTE)) {
                            activeBosses++;
                        }
                    }
                }

                for (Player player : Bukkit.getOnlinePlayers()) {
                    if (!scoreboards.containsKey(player.getUniqueId())) {
                        setupScoreboard(player);
                    } else {
                        updateScoreboard(player, activeBosses);
                    }
                }
            }
        }.runTaskTimer(plugin, 20L, 20L); // Update every second
    }
}
