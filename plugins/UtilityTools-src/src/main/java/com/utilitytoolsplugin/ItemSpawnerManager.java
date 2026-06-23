package com.utilitytoolsplugin;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.CreatureSpawner;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.SpawnerSpawnEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.Vector;
import org.bukkit.util.io.BukkitObjectInputStream;
import org.bukkit.util.io.BukkitObjectOutputStream;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.util.*;

public class ItemSpawnerManager implements Listener {

    private final Plugin plugin;
    private final File dataFile;
    private FileConfiguration dataConfig;

    // Dữ liệu lồng: locString -> SpawnerData
    private final Map<String, SpawnerData> spawners = new HashMap<>();

    // GUI title prefix
    private static final String GUI_TITLE_PREFIX = "§6§lRương Lồng§r §7- ";

    // Số slot item trên mỗi trang GUI
    private static final int ITEMS_PER_PAGE = 21;

    // ===================== INNER CLASS =====================

    private static class SpawnerData {
        String type; // "skeleton" | "blaze"
        int count; // số lồng gộp
        long totalItems = 0; // kho không giới hạn

        SpawnerData(String type, int count) {
            this.type = type;
            this.count = count;
        }

        void addItems(int amount) {
            if (amount > 0) totalItems += amount;
        }

        int totalPages() {
            long totalStacks = (totalItems + 63) / 64;
            return Math.max(1, (int) Math.ceil((double) totalStacks / ITEMS_PER_PAGE));
        }
    }

    // ===================== CONSTRUCTOR =====================

    public ItemSpawnerManager(Plugin plugin) {
        this.plugin = plugin;
        this.dataFile = new File(plugin.getDataFolder(), "spawners.yml");
        loadData();
        startSpawnerTask();
    }

    // ===================== LOCATION HELPER =====================
    // Dùng "|" làm separator để tránh xung đột với dấu "-" trong tọa độ âm

    private String locToString(Location loc) {
        return loc.getWorld().getName()
                + "|" + loc.getBlockX()
                + "|" + loc.getBlockY()
                + "|" + loc.getBlockZ();
    }

    private Location stringToLoc(String s) {
        // Hỗ trợ cả định dạng cũ dùng "_" lẫn mới dùng "|"
        String[] parts = s.contains("|") ? s.split("\\|") : s.split("_", 4);
        if (parts.length < 4)
            return null;
        World world = Bukkit.getWorld(parts[0]);
        if (world == null)
            return null;
        try {
            return new Location(world,
                    Integer.parseInt(parts[1]),
                    Integer.parseInt(parts[2]),
                    Integer.parseInt(parts[3]));
        } catch (NumberFormatException e) {
            return null;
        }
    }

    // ===================== LOAD / SAVE =====================

    private void loadData() {
        if (!dataFile.exists()) {
            try {
                dataFile.getParentFile().mkdirs();
                dataFile.createNewFile();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
        dataConfig = YamlConfiguration.loadConfiguration(dataFile);
        spawners.clear();

        if (!dataConfig.contains("spawners"))
            return;

        for (String rawKey : dataConfig.getConfigurationSection("spawners").getKeys(false)) {
            String path = "spawners." + rawKey;
            String type = dataConfig.getString(path + ".type", "skeleton");
            int count = dataConfig.getInt(path + ".count", 1);
            SpawnerData data = new SpawnerData(type, count);

            if (dataConfig.contains(path + ".storage")) {
                long total = 0;
                for (String enc : dataConfig.getStringList(path + ".storage")) {
                    ItemStack item = decodeItem(enc);
                    if (item != null) total += item.getAmount();
                }
                data.totalItems = total;
                dataConfig.set(path + ".storage", null);
            } else {
                data.totalItems = dataConfig.getLong(path + ".totalItems", 0);
            }

            // Chuẩn hoá key sang định dạng mới "|"
            String normKey = rawKey.replace("_", "|");
            // Nếu key cũ dùng "_" và world name không có "_", cần parse lại đúng
            Location loc = stringToLoc(rawKey);
            String finalKey = (loc != null) ? locToString(loc) : normKey;
            spawners.put(finalKey, data);
        }
    }

    private void saveData() {
        dataConfig.set("spawners", null);
        for (Map.Entry<String, SpawnerData> entry : spawners.entrySet()) {
            String path = "spawners." + entry.getKey();
            SpawnerData data = entry.getValue();
            dataConfig.set(path + ".type", data.type);
            dataConfig.set(path + ".count", data.count);
            dataConfig.set(path + ".totalItems", data.totalItems);
        }
        try {
            dataConfig.save(dataFile);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    // ===================== SERIALIZE =====================

    private String encodeItem(ItemStack item) {
        try {
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            BukkitObjectOutputStream boos = new BukkitObjectOutputStream(baos);
            boos.writeObject(item);
            boos.close();
            return Base64.getEncoder().encodeToString(baos.toByteArray());
        } catch (IOException e) {
            return null;
        }
    }

    private ItemStack decodeItem(String encoded) {
        try {
            byte[] bytes = Base64.getDecoder().decode(encoded);
            BukkitObjectInputStream bois = new BukkitObjectInputStream(new ByteArrayInputStream(bytes));
            ItemStack item = (ItemStack) bois.readObject();
            bois.close();
            return item;
        } catch (Exception e) {
            return null;
        }
    }

    // ===================== HOẠT ẢNH MOB TRONG LỒNG =====================

    private void applySpawnerAnimation(Block block, String type) {
        if (!(block.getState() instanceof CreatureSpawner))
            return;
        CreatureSpawner cs = (CreatureSpawner) block.getState();
        EntityType et;
        switch (type) {
            case "blaze":
                et = EntityType.BLAZE;
                break;
            default:
                et = EntityType.SKELETON;
                break;
        }
        cs.setSpawnedType(et);
        // Phải set Max trước Min — Paper validate min <= max mỗi lần gọi setter
        cs.setMaxSpawnDelay(99999);
        cs.setMinSpawnDelay(99999);
        cs.setDelay(99999);
        cs.update(true, false);
    }

    // ===================== EVENTS — ĐẶT & PHÁ =====================

    /** Priority LOWEST để xử lý trước các plugin khác */
    @EventHandler(priority = EventPriority.LOWEST)
    public void onBlockPlace(BlockPlaceEvent event) {
        if (event.isCancelled())
            return;
        ItemStack item = event.getItemInHand();
        if (item.getType() != Material.SPAWNER || !item.hasItemMeta())
            return;

        ItemMeta meta = item.getItemMeta();
        if (!meta.getPersistentDataContainer().has(ShopManager.SPAWNER_TYPE_KEY, PersistentDataType.STRING))
            return;

        // Nếu player đang SHIFT → bỏ qua, để PlayerInteractEvent xử lý gộp
        if (event.getPlayer().isSneaking()) {
            event.setCancelled(true);
            return;
        }

        String type = meta.getPersistentDataContainer().get(ShopManager.SPAWNER_TYPE_KEY, PersistentDataType.STRING);
        Location loc = event.getBlockPlaced().getLocation();
        String locStr = locToString(loc);

        if (spawners.containsKey(locStr)) {
            // Vị trí đã có lồng → không thể đặt chồng
            SpawnerData ex = spawners.get(locStr);
            event.getPlayer().sendMessage("§cVị trí này đã có Lồng §f" + getTypeName(ex.type)
                    + "§c! Hãy §eSHIFT + Chuột Phải §ccầm lồng để gộp.");
            event.setCancelled(true);
            return;
        }

        // Tạo mới
        SpawnerData data = new SpawnerData(type, 1);
        spawners.put(locStr, data);
        saveData();

        // Áp hoạt ảnh sau 1 tick
        final String finalType = type;
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            Block b = loc.getBlock();
            if (b.getType() == Material.SPAWNER)
                applySpawnerAnimation(b, finalType);
        }, 1L);

        event.getPlayer().sendMessage("§aBạn đã đặt §fLồng " + getTypeName(type)
                + " §a(x1)!\n§7• Đứng + Chuột Phải → mở rương.\n§7• SHIFT + Chuột Phải (cầm lồng) → gộp thêm lồng.");
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onBlockBreak(BlockBreakEvent event) {
        if (event.isCancelled())
            return;
        Block block = event.getBlock();
        if (block.getType() != Material.SPAWNER)
            return;

        String locStr = locToString(block.getLocation());
        if (!spawners.containsKey(locStr))
            return;

        SpawnerData data = spawners.remove(locStr);
        saveData();

        event.setExpToDrop(0);
        event.setDropItems(false);

        for (int i = 0; i < data.count; i++) {
            block.getWorld().dropItemNaturally(block.getLocation(), createSpawnerItem(data.type));
        }
        event.getPlayer().sendMessage("§aBạn đã phá §fLồng " + getTypeName(data.type)
                + " §7(x" + data.count + ")§a! Nhận lại §e" + data.count + " lồng.");
    }

    @EventHandler
    public void onSpawnerSpawn(SpawnerSpawnEvent event) {
        if (spawners.containsKey(locToString(event.getSpawner().getLocation()))) {
            event.setCancelled(true);
        }
    }

    // ===================== INTERACT — MỞ RƯƠNG / GỘP LỒNG =====================

    @EventHandler(priority = EventPriority.LOWEST)
    public void onPlayerInteract(PlayerInteractEvent event) {
        if (event.getAction() != Action.RIGHT_CLICK_BLOCK)
            return;
        if (event.getHand() != EquipmentSlot.HAND)
            return;

        Block block = event.getClickedBlock();
        if (block == null || block.getType() != Material.SPAWNER)
            return;

        event.setCancelled(true); // Luôn cancel mở GUI spawner mặc định

        String locStr = locToString(block.getLocation());
        Player player = event.getPlayer();
        boolean sneaking = player.isSneaking();
        ItemStack inHand = player.getInventory().getItemInMainHand();

        // ── SHIFT + Chuột Phải + cầm lồng plugin → GỘP LỒNG ──
        if (sneaking) {
            if (inHand.getType() == Material.SPAWNER && inHand.hasItemMeta()) {
                ItemMeta hm = inHand.getItemMeta();
                if (hm.getPersistentDataContainer().has(ShopManager.SPAWNER_TYPE_KEY, PersistentDataType.STRING)) {
                    String handType = hm.getPersistentDataContainer()
                            .get(ShopManager.SPAWNER_TYPE_KEY, PersistentDataType.STRING);

                    if (!spawners.containsKey(locStr)) {
                        player.sendMessage("§cLồng này không thuộc plugin! Đặt lại lồng từ /shop.");
                        return;
                    }

                    SpawnerData target = spawners.get(locStr);
                    if (!target.type.equals(handType)) {
                        player.sendMessage("§c✗ Không thể gộp! Lồng tay: §f" + getTypeName(handType)
                                + "§c ≠ Lồng đặt: §f" + getTypeName(target.type));
                        return;
                    }

                    int add = inHand.getAmount();
                    target.count += add;
                    saveData();

                    // Xoá toàn bộ stack trong tay
                    player.getInventory().setItemInMainHand(new ItemStack(Material.AIR));

                    player.sendMessage("§a§l✔ Gộp thành công! §fLồng §e" + getTypeName(target.type)
                            + " §fx" + target.count
                            + " §7| Tốc độ: §e" + target.count + " item/5s"
                            + " §7| Kho: §eKhông giới hạn");
                    return;
                }
            }
            // Đang shift nhưng không cầm lồng → không làm gì
            return;
        }

        // ── Đứng + Chuột Phải → MỞ RƯƠNG ──
        if (!spawners.containsKey(locStr))
            return;
        openChestGUI(player, locStr, 0);
    }

    // ===================== GUI =====================

    private final Map<UUID, Integer> playerPageMap = new HashMap<>();
    private final Map<UUID, String> playerSpawnerMap = new HashMap<>();

    private void openChestGUI(Player player, String locStr, int page) {
        SpawnerData data = spawners.get(locStr);
        if (data == null)
            return;

        int totalPages = data.totalPages();
        page = Math.max(0, Math.min(page, totalPages - 1));

        String title = GUI_TITLE_PREFIX + getTypeName(data.type) + " x" + data.count;
        Inventory inv = Bukkit.createInventory(null, 27, title);

        // 21 slot đầu: item trong kho
        int start = page * ITEMS_PER_PAGE;
        long totalStacks = (data.totalItems + 63) / 64;
        for (int i = 0; i < ITEMS_PER_PAGE; i++) {
            int idx = start + i;
            if (idx < totalStacks) {
                long itemsRemaining = data.totalItems - (idx * 64L);
                int amount = (int) Math.min(64L, itemsRemaining);
                ItemStack display = getDropItem(data.type);
                if (display != null) {
                    display.setAmount(amount);
                    inv.setItem(i, display);
                }
            } else {
                inv.setItem(i, makeGlass(Material.WHITE_STAINED_GLASS_PANE, " "));
            }
        }

        // Điều hướng
        inv.setItem(21, page > 0
                ? makeNavButton(Material.ARROW, "§e§l◀ Trang Trước", "§7Trang " + page + "/" + totalPages)
                : makeGlass(Material.GRAY_STAINED_GLASS_PANE, " "));

        inv.setItem(22, makeInfoButton(data, page, totalPages));

        inv.setItem(23, makeNavButton(Material.CHEST, "§c§l⬇ Vứt 27 Stack Ra Trước Mặt",
                "§7Click để lấy tối đa 27 stack",
                "§7ra phía trước mặt bạn."));

        inv.setItem(24, makeGlass(Material.GRAY_STAINED_GLASS_PANE, " "));

        inv.setItem(25, page < totalPages - 1
                ? makeNavButton(Material.ARROW, "§e§l▶ Trang Sau", "§7Trang " + (page + 2) + "/" + totalPages)
                : makeGlass(Material.GRAY_STAINED_GLASS_PANE, " "));

        inv.setItem(26, makeNavButton(Material.BARRIER, "§c§lĐóng", "§7Click để đóng."));

        player.openInventory(inv);
        playerPageMap.put(player.getUniqueId(), page);
        playerSpawnerMap.put(player.getUniqueId(), locStr);
    }

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player))
            return;
        Player player = (Player) event.getWhoClicked();

        if (!event.getView().getTitle().startsWith(GUI_TITLE_PREFIX))
            return;
        event.setCancelled(true);

        String locStr = playerSpawnerMap.get(player.getUniqueId());
        if (locStr == null)
            return;
        SpawnerData data = spawners.get(locStr);
        if (data == null)
            return;

        int slot = event.getRawSlot();
        int page = playerPageMap.getOrDefault(player.getUniqueId(), 0);

        if (slot >= 0 && slot < ITEMS_PER_PAGE)
            return; // chỉ xem

        switch (slot) {
            case 21:
                if (page > 0)
                    openChestGUI(player, locStr, page - 1);
                break;
            case 23:
                dropItemsInFront(player, data, locStr);
                openChestGUI(player, locStr, Math.min(page, data.totalPages() - 1));
                break;
            case 25:
                if (page < data.totalPages() - 1)
                    openChestGUI(player, locStr, page + 1);
                break;
            case 26:
                player.closeInventory();
                playerPageMap.remove(player.getUniqueId());
                playerSpawnerMap.remove(player.getUniqueId());
                break;
        }
    }

    /** Vứt tối đa 27 stack ra phía trước mặt người chơi */
    private void dropItemsInFront(Player player, SpawnerData data, String locStr) {
        if (data.totalItems <= 0) {
            player.sendMessage("§cRương lồng đang trống!");
            return;
        }
        Vector dir = player.getLocation().getDirection().normalize().multiply(2);
        Location dropLoc = player.getLocation().clone().add(dir).add(0, 1, 0);

        int dropped = 0;
        ItemStack dropItem = getDropItem(data.type);
        if (dropItem == null) return;

        while (data.totalItems > 0 && dropped < 27) {
            int take = (int) Math.min(64L, data.totalItems);
            ItemStack toDrop = dropItem.clone();
            toDrop.setAmount(take);
            player.getWorld().dropItemNaturally(dropLoc, toDrop);
            data.totalItems -= take;
            dropped++;
        }
        saveData();
        player.sendMessage("§a§l✔ Vứt §e" + dropped + " stack " + getItemName(data.type)
                + " §ara phía trước mặt!");
    }

    // ===================== SPAWN TASK =====================

    private void startSpawnerTask() {
        // Sinh item mỗi 5 giây
        new BukkitRunnable() {
            @Override
            public void run() {
                for (SpawnerData data : new ArrayList<>(spawners.values())) {
                    data.addItems(data.count);
                }
            }
        }.runTaskTimer(plugin, 100L, 100L);

        // Auto-save mỗi 60 giây
        new BukkitRunnable() {
            @Override
            public void run() {
                saveData();
            }
        }.runTaskTimer(plugin, 1200L, 1200L);

        // Khôi phục hoạt ảnh sau 2 giây khi plugin load
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            for (Map.Entry<String, SpawnerData> entry : spawners.entrySet()) {
                Location loc = stringToLoc(entry.getKey());
                if (loc == null)
                    continue;
                Block b = loc.getBlock();
                if (b.getType() == Material.SPAWNER)
                    applySpawnerAnimation(b, entry.getValue().type);
            }
        }, 40L);
    }

    // ===================== HELPER =====================

    private ItemStack getDropItem(String type) {
        if ("skeleton".equals(type))
            return new ItemStack(Material.BONE, 1);
        if ("blaze".equals(type))
            return new ItemStack(Material.BLAZE_ROD, 1);
        return null;
    }

    private String getTypeName(String type) {
        if ("skeleton".equals(type))
            return "Skeleton";
        if ("blaze".equals(type))
            return "Quỷ Lửa";
        return type;
    }

    private String getItemName(String type) {
        if ("skeleton".equals(type))
            return "Xương";
        if ("blaze".equals(type))
            return "Que Quỷ Lửa";
        return type;
    }

    private ItemStack createSpawnerItem(String type) {
        ItemStack item = new ItemStack(Material.SPAWNER);
        ItemMeta meta = item.getItemMeta();
        if (meta == null)
            return item;
        meta.setDisplayName("skeleton".equals(type) ? "§fLồng Skeleton (Rương)" : "§cLồng Quỷ Lửa (Rương)");
        meta.setLore(Arrays.asList(
                "§7• Đứng + Chuột Phải → mở rương lưu trữ.",
                "§7• SHIFT + Chuột Phải (cầm lồng) → gộp thêm.",
                "§7• Kho chứa §aKhông Giới Hạn§7!"));
        meta.getPersistentDataContainer().set(ShopManager.SPAWNER_TYPE_KEY, PersistentDataType.STRING, type);
        item.setItemMeta(meta);
        return item;
    }

    private ItemStack makeGlass(Material mat, String name) {
        ItemStack item = new ItemStack(mat);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(name);
            item.setItemMeta(meta);
        }
        return item;
    }

    private ItemStack makeNavButton(Material mat, String name, String... lore) {
        ItemStack item = new ItemStack(mat);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(name);
            meta.setLore(Arrays.asList(lore));
            item.setItemMeta(meta);
        }
        return item;
    }

    private ItemStack makeInfoButton(SpawnerData data, int page, int totalPages) {
        ItemStack item = new ItemStack(Material.BOOK);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.setDisplayName("§b§lThông Tin Lồng");
            meta.setLore(Arrays.asList(
                    "§7Loại: §f" + getTypeName(data.type),
                    "§7Số lồng gộp: §e" + data.count,
                    "§7Tốc độ: §e" + data.count + " item §7/ 5 giây",
                    "§7Kho hiện tại: §e" + data.totalItems + " item §a(Không giới hạn)",
                    "§7Trang: §e" + (page + 1) + " §7/ §e" + totalPages));
            item.setItemMeta(meta);
        }
        return item;
    }
}
