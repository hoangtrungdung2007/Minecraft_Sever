package com.utilitytoolsplugin;

import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class EconomyManager {
    private final Plugin plugin;
    private final File dataFile;
    private FileConfiguration dataConfig;
    private final Map<UUID, Double> balances = new HashMap<>();

    public EconomyManager(Plugin plugin) {
        this.plugin = plugin;
        this.dataFile = new File(plugin.getDataFolder(), "economy.yml");
        loadData();
    }

    public void loadData() {
        if (!dataFile.exists()) {
            try {
                dataFile.getParentFile().mkdirs();
                dataFile.createNewFile();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
        dataConfig = YamlConfiguration.loadConfiguration(dataFile);
        if (dataConfig.contains("balances")) {
            for (String key : dataConfig.getConfigurationSection("balances").getKeys(false)) {
                balances.put(UUID.fromString(key), dataConfig.getDouble("balances." + key));
            }
        }
    }

    public void saveData() {
        for (Map.Entry<UUID, Double> entry : balances.entrySet()) {
            dataConfig.set("balances." + entry.getKey().toString(), entry.getValue());
        }
        try {
            dataConfig.save(dataFile);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public double getBalance(Player player) {
        return balances.getOrDefault(player.getUniqueId(), 0.0);
    }

    public void addBalance(Player player, double amount) {
        balances.put(player.getUniqueId(), getBalance(player) + amount);
        saveData(); // Save immediately or periodically. For simplicity, save immediately.
    }

    public boolean removeBalance(Player player, double amount) {
        double current = getBalance(player);
        if (current >= amount) {
            balances.put(player.getUniqueId(), current - amount);
            saveData();
            return true;
        }
        return false;
    }

    public boolean hasEnough(Player player, double amount) {
        return getBalance(player) >= amount;
    }

    /** Lấy balance theo UUID (dùng cho /stats khi player offline) */
    public double getBalanceByUUID(UUID uuid) {
        return balances.getOrDefault(uuid, 0.0);
    }
}
