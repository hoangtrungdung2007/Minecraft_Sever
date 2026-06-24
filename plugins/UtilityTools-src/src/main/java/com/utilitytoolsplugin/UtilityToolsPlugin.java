package com.utilitytoolsplugin;

import org.bukkit.plugin.java.JavaPlugin;

/**
 * UtilityTools Plugin - Main Class
 * 
 * Plugin tự viết với 3 tính năng:
 * 1. TreeCapitator - Chặt cây nhanh
 * 2. OreVeinMiner - Đào mạch quặng nhanh
 * 3. Hammer 3x3 - Cuốc phá 3x3 block
 * 
 * Tương thích: Paper 1.21.4
 * Author: Antigravity AI Agent
 */
public class UtilityToolsPlugin extends JavaPlugin {

    private static UtilityToolsPlugin instance;
    private TreeCapitatorListener treeCapitatorListener;
    private OreVeinMinerListener oreVeinMinerListener;
    private HammerListener hammerListener;
    private TorchLightListener torchLightListener;
    
    // KingMC Features
    private EconomyManager economyManager;
    private KingScoreboardManager scoreboardManager;
    private ShopManager shopManager;
    private SellManager sellManager;
    private ItemSpawnerManager itemSpawnerManager;
    private MinimapManager minimapManager;
    private RTPManager rtpManager;
    private HomeManager homeManager;
    private StatsManager statsManager;
    private BossZombieManager bossZombieManager;
    private BossSkeletonManager bossSkeletonManager;
    private RaidManager raidManager;

    @Override
    public void onEnable() {
        instance = this;
        
        // Save default config
        saveDefaultConfig();
        
        // Register listeners
        treeCapitatorListener = new TreeCapitatorListener(this);
        oreVeinMinerListener = new OreVeinMinerListener(this);
        hammerListener = new HammerListener(this);
        torchLightListener = new TorchLightListener(this);
        
        getServer().getPluginManager().registerEvents(treeCapitatorListener, this);
        getServer().getPluginManager().registerEvents(oreVeinMinerListener, this);
        getServer().getPluginManager().registerEvents(hammerListener, this);
        getServer().getPluginManager().registerEvents(torchLightListener, this);
        
        // Initialize KingMC Features
        economyManager = new EconomyManager(this);
        scoreboardManager = new KingScoreboardManager(this, economyManager);
        shopManager = new ShopManager(this, economyManager);
        sellManager = new SellManager(economyManager);
        itemSpawnerManager = new ItemSpawnerManager(this);
        minimapManager = new MinimapManager(this);
        rtpManager = new RTPManager(this);
        homeManager = new HomeManager(this);
        statsManager = new StatsManager(this, economyManager);
        bossZombieManager = new BossZombieManager(this, economyManager);
        bossSkeletonManager = new BossSkeletonManager(this, economyManager);
        raidManager = new RaidManager(this, economyManager);
        
        // Register events for KingMC Features
        getServer().getPluginManager().registerEvents(shopManager, this);
        getServer().getPluginManager().registerEvents(sellManager, this);
        getServer().getPluginManager().registerEvents(itemSpawnerManager, this);
        getServer().getPluginManager().registerEvents(minimapManager, this);
        getServer().getPluginManager().registerEvents(rtpManager, this);
        getServer().getPluginManager().registerEvents(statsManager, this);
        getServer().getPluginManager().registerEvents(bossZombieManager, this);
        getServer().getPluginManager().registerEvents(bossSkeletonManager, this);
        getServer().getPluginManager().registerEvents(raidManager, this);
        
        // Register commands
        getCommand("hammer").setExecutor(new HammerCommand(this));
        getCommand("shop").setExecutor(shopManager);
        getCommand("sell").setExecutor(sellManager);
        getCommand("minimap").setExecutor(minimapManager);
        getCommand("rtp").setExecutor(rtpManager);
        getCommand("sethome").setExecutor(homeManager);
        getCommand("home").setExecutor(homeManager);
        getCommand("delhome").setExecutor(homeManager);
        getCommand("sethome").setTabCompleter(homeManager);
        getCommand("home").setTabCompleter(homeManager);
        getCommand("delhome").setTabCompleter(homeManager);
        getCommand("stats").setExecutor(statsManager);
        getCommand("stats").setTabCompleter(statsManager);
        
        TPAManager tpaManager = new TPAManager(this);
        getCommand("tpa").setExecutor(tpaManager);
        getCommand("tpa").setTabCompleter(tpaManager);
        
        getCommand("raid").setExecutor(raidManager);
        
        GiveWeaponCommand giveWeaponCmd = new GiveWeaponCommand(this);
        getCommand("givemace").setExecutor(giveWeaponCmd);
        getCommand("givebow").setExecutor(giveWeaponCmd);
        
        SpawnBossCommand spawnBossCmd = new SpawnBossCommand(this);
        getCommand("spawn").setExecutor(spawnBossCmd);
        getCommand("spawn").setTabCompleter(spawnBossCmd);
        
        getLogger().info("===========================================");
        getLogger().info(" UtilityTools Plugin da khoi dong!");
        getLogger().info(" TreeCapitator: HOAT DONG");
        getLogger().info(" OreVeinMiner: HOAT DONG");
        getLogger().info(" Hammer 3x3: HOAT DONG");
        getLogger().info(" TorchLight (Duoc Sang): HOAT DONG");
        getLogger().info(" KingMC Features (Economy, Shop, Spawners): HOAT DONG");
        getLogger().info("===========================================");
    }

    @Override
    public void onDisable() {
        // Don sach tat ca light block truoc khi tat
        if (torchLightListener != null) {
            torchLightListener.cleanup();
        }
        if (statsManager != null) {
            statsManager.saveData();
        }
        getLogger().info("UtilityTools Plugin da tat.");
        instance = null;
    }

    public static UtilityToolsPlugin getInstance() {
        return instance;
    }

    public BossZombieManager getBossZombieManager() {
        return bossZombieManager;
    }

    public BossSkeletonManager getBossSkeletonManager() {
        return bossSkeletonManager;
    }
}
