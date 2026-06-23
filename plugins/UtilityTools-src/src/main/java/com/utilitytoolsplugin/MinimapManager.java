package com.utilitytoolsplugin;

import org.bukkit.*;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.MapMeta;
import org.bukkit.map.*;
import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.*;

/**
 * MinimapManager
 *
 * Tạo bản đồ mini cho mỗi người chơi bằng MapRenderer tùy chỉnh.
 * Bản đồ được trang bị vào tay trái (off-hand) → hiển thị góc trái màn hình.
 *
 * Tính năng:
 * - Nền lưới tối màu phong cách radar
 * - Chấm trắng = chính mình (giữa bản đồ)
 * - Chấm xanh dương = đồng đội trong tầm nhìn (64 block)
 * - Mũi tên vàng = đồng đội ngoài tầm, chỉ hướng ở viền bản đồ
 * - Cập nhật mỗi 1 giây
 * - Lệnh /minimap để bật/tắt
 */
public class MinimapManager implements Listener, CommandExecutor {

    private final Plugin plugin;

    // UUID -> MapView (mỗi player có map riêng)
    private final Map<UUID, MapView> playerMaps = new HashMap<>();

    // UUID -> trạng thái bật/tắt minimap
    private final Set<UUID> minimapEnabled = new HashSet<>();

    // Bán kính hiển thị (blocks): 64 block từ tâm ra mép
    private static final int RADIUS = 64;
    // Kích thước map (pixels)
    private static final int MAP_SIZE = 128;

    public MinimapManager(Plugin plugin) {
        this.plugin = plugin;
        startUpdateTask();
    }

    // ========================== COMMAND /minimap ==========================

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player)) {
            sender.sendMessage("§cChỉ người chơi mới dùng được lệnh này!");
            return true;
        }
        Player player = (Player) sender;

        if (minimapEnabled.contains(player.getUniqueId())) {
            // Tắt minimap – xóa map khỏi off-hand
            minimapEnabled.remove(player.getUniqueId());
            player.getInventory().setItemInOffHand(new ItemStack(Material.AIR));
            player.sendMessage("§7Đã §ctắt §7bản đồ mini.");
        } else {
            // Bật minimap
            minimapEnabled.add(player.getUniqueId());
            giveMinimap(player);
            player.sendMessage("§b§lBản Đồ Mini §r§ađã bật! §7Cầm ở tay trái để xem.");
        }
        return true;
    }

    // ========================== JOIN EVENT ==========================

    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        // Tự động bật minimap khi vào server (sau 1 giây)
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (player.isOnline()) {
                minimapEnabled.add(player.getUniqueId());
                giveMinimap(player);
                player.sendMessage("§b§l[Minimap] §r§7Bản đồ mini đã được trang bị tay trái. §8(/minimap để bật/tắt)");
            }
        }, 40L);
    }

    // ========================== GIVE MAP ==========================

    public void giveMinimap(Player player) {
        // Tạo MapView nếu chưa có
        MapView mapView = playerMaps.computeIfAbsent(player.getUniqueId(), uuid -> {
            MapView view = Bukkit.createMap(player.getWorld());
            view.setScale(MapView.Scale.CLOSEST);
            view.setTrackingPosition(false);
            view.setUnlimitedTracking(true);

            // Xóa renderer mặc định
            for (MapRenderer r : new ArrayList<>(view.getRenderers())) {
                view.removeRenderer(r);
            }
            // Thêm renderer tùy chỉnh
            view.addRenderer(new MinimapRenderer(player));
            return view;
        });

        // Tạo item map
        ItemStack mapItem = new ItemStack(Material.FILLED_MAP);
        MapMeta meta = (MapMeta) mapItem.getItemMeta();
        if (meta != null) {
            meta.setMapView(mapView);
            meta.setDisplayName("§b§lBản Đồ Mini §8[Minimap]");
            meta.setLore(Arrays.asList(
                    "§7• §fChấm trắng §7= bạn (tâm bản đồ)",
                    "§7• §bChấm xanh §7= đồng đội (trong 64 block)",
                    "§7• §eVàng §7= mũi tên hướng đồng đội (xa)",
                    "§8/minimap để bật/tắt"
            ));
            mapItem.setItemMeta(meta);
        }

        player.getInventory().setItemInOffHand(mapItem);
    }

    // ========================== UPDATE TASK ==========================

    private void startUpdateTask() {
        new BukkitRunnable() {
            @Override
            public void run() {
                for (Player player : Bukkit.getOnlinePlayers()) {
                    if (!minimapEnabled.contains(player.getUniqueId())) continue;
                    MapView view = playerMaps.get(player.getUniqueId());
                    if (view == null) continue;

                    // Đánh dấu cần vẽ lại
                    for (MapRenderer renderer : view.getRenderers()) {
                        if (renderer instanceof MinimapRenderer) {
                            ((MinimapRenderer) renderer).markDirty();
                        }
                    }

                    // Gửi packet cập nhật map cho player
                    player.sendMap(view);
                }
            }
        }.runTaskTimer(plugin, 20L, 20L); // Mỗi 1 giây
    }

    // ========================== RENDERER ==========================

    /**
     * MinimapRenderer – vẽ bản đồ 128×128px
     * Tâm bản đồ = vị trí người chơi sở hữu map
     */
    static class MinimapRenderer extends MapRenderer {

        private final Player owner;
        private boolean dirty = true;

        // Màu sắc (dùng MapPalette.matchColor)
        private static final byte COLOR_BG_DARK   = MapPalette.matchColor(15, 20, 15);
        private static final byte COLOR_GRID       = MapPalette.matchColor(25, 40, 25);
        private static final byte COLOR_BORDER     = MapPalette.matchColor(10, 10, 10);
        private static final byte COLOR_SELF       = MapPalette.matchColor(255, 255, 255);
        private static final byte COLOR_TEAMMATE   = MapPalette.matchColor(80, 200, 255);
        private static final byte COLOR_ARROW      = MapPalette.matchColor(255, 210, 0);
        private static final byte COLOR_ARROW_OUT  = MapPalette.matchColor(255, 130, 0);
        private static final byte COLOR_COMPASS    = MapPalette.matchColor(200, 200, 200);
        private static final byte COLOR_NORTH      = MapPalette.matchColor(255, 80, 80);

        MinimapRenderer(Player owner) {
            super(true); // contextual = mỗi người xem nhận bản vẽ riêng
            this.owner = owner;
        }

        void markDirty() {
            this.dirty = true;
        }

        @Override
        public void render(MapView view, MapCanvas canvas, Player viewer) {
            if (!dirty) return;
            dirty = false;
            if (!owner.isOnline()) return;

            Location ownerLoc = owner.getLocation();
            int cx = MAP_SIZE / 2;
            int cy = MAP_SIZE / 2;

            // ── 1. Vẽ nền ──────────────────────────────────────────────
            for (int x = 0; x < MAP_SIZE; x++) {
                for (int y = 0; y < MAP_SIZE; y++) {
                    // Vòng tròn radar (nền viền mờ dần)
                    int ddx = x - cx;
                    int ddy = y - cy;
                    double dist = Math.sqrt(ddx * ddx + ddy * ddy);

                    if (dist > 62) {
                        // Ngoài viền tròn → màu tối nhất
                        canvas.setPixel(x, y, COLOR_BORDER);
                    } else {
                        // Lưới mỗi 8px
                        if (x % 16 == 0 || y % 16 == 0) {
                            canvas.setPixel(x, y, COLOR_GRID);
                        } else {
                            canvas.setPixel(x, y, COLOR_BG_DARK);
                        }
                    }
                }
            }

            // ── 2. Vẽ vòng tròn viền radar ─────────────────────────────
            drawCircle(canvas, cx, cy, 62, COLOR_BORDER);

            // ── 3. Vẽ chữ compass (N/S/E/W) ────────────────────────────
            canvas.drawText(cx - 2, 3,  MinecraftFont.Font, "N"); // Bắc
            canvas.drawText(cx - 2, 118, MinecraftFont.Font, "S"); // Nam
            canvas.drawText(3,  cy - 3, MinecraftFont.Font, "W"); // Tây
            canvas.drawText(118, cy - 3, MinecraftFont.Font, "E"); // Đông

            // Gạch chỉ hướng Bắc (đỏ nhỏ)
            canvas.setPixel(cx, 12, COLOR_NORTH);
            canvas.setPixel(cx - 1, 13, COLOR_NORTH);
            canvas.setPixel(cx + 1, 13, COLOR_NORTH);

            // ── 4. Vẽ đồng đội ─────────────────────────────────────────
            for (Player other : Bukkit.getOnlinePlayers()) {
                if (other.equals(owner)) continue;
                if (!other.getWorld().equals(owner.getWorld())) continue;

                double dx = other.getLocation().getX() - ownerLoc.getX();
                double dz = other.getLocation().getZ() - ownerLoc.getZ();

                // Scale: RADIUS block = 62 pixel (vừa trong vòng tròn)
                double scale = 62.0 / RADIUS;
                int px = cx + (int) Math.round(dx * scale);
                int py = cy + (int) Math.round(dz * scale);

                double distBlocks = Math.sqrt(dx * dx + dz * dz);

                if (distBlocks <= RADIUS) {
                    // Trong tầm → vẽ chấm xanh
                    drawDot(canvas, px, py, COLOR_TEAMMATE, 2);

                    // Tên rút gọn (4 ký tự)
                    String name = other.getName().length() > 5
                            ? other.getName().substring(0, 5) : other.getName();
                    int tx = Math.max(1, Math.min(px - 9, MAP_SIZE - 32));
                    int ty = Math.max(1, Math.min(py - 8, MAP_SIZE - 10));
                    canvas.drawText(tx, ty, MinecraftFont.Font, name);
                } else {
                    // Ngoài tầm → vẽ mũi tên ở viền tròn
                    drawArrowAtEdge(canvas, cx, cy, dx, dz, COLOR_ARROW, COLOR_ARROW_OUT);
                }
            }

            // ── 5. Vẽ bản thân (trung tâm, chấm trắng lớn hơn) ─────────
            drawSelfArrow(canvas, cx, cy, ownerLoc.getYaw());
        }

        // ── Vẽ chấm tròn ──────────────────────────────────────────────────
        private void drawDot(MapCanvas canvas, int x, int y, byte color, int radius) {
            for (int dx = -radius; dx <= radius; dx++) {
                for (int dy = -radius; dy <= radius; dy++) {
                    if (dx * dx + dy * dy <= radius * radius) {
                        int nx = x + dx;
                        int ny = y + dy;
                        if (nx >= 0 && nx < MAP_SIZE && ny >= 0 && ny < MAP_SIZE) {
                            canvas.setPixel(nx, ny, color);
                        }
                    }
                }
            }
        }

        // ── Vẽ mũi tên hướng của bản thân (theo yaw) ───────────────────
        private void drawSelfArrow(MapCanvas canvas, int cx, int cy, float yaw) {
            // Chấm trắng trung tâm
            drawDot(canvas, cx, cy, COLOR_SELF, 3);

            // Mũi tên nhỏ chỉ hướng mặt
            double rad = Math.toRadians(yaw + 180);
            double nx = Math.sin(rad);
            double nz = -Math.cos(rad);

            for (int i = 4; i <= 8; i++) {
                int ax = cx + (int) Math.round(nx * i);
                int ay = cy + (int) Math.round(nz * i);
                if (ax >= 0 && ax < MAP_SIZE && ay >= 0 && ay < MAP_SIZE) {
                    canvas.setPixel(ax, ay, COLOR_SELF);
                }
            }
            // Đầu mũi tên nhỏ
            int tx = cx + (int) Math.round(nx * 8);
            int ty = cy + (int) Math.round(nz * 8);
            drawDot(canvas, tx, ty, COLOR_SELF, 1);
        }

        // ── Vẽ mũi tên ở viền tròn chỉ hướng đồng đội ───────────────────
        private void drawArrowAtEdge(MapCanvas canvas, int cx, int cy,
                                     double dx, double dz,
                                     byte arrowColor, byte outlineColor) {
            double len = Math.sqrt(dx * dx + dz * dz);
            if (len < 0.001) return;
            double nx = dx / len;
            double nz = dz / len;

            // Điểm đặt mũi tên trên vòng tròn r=54 (hơi vào trong để không cắt viền)
            int edgeR = 54;
            int ax = cx + (int) Math.round(nx * edgeR);
            int ay = cy + (int) Math.round(nz * edgeR);

            // Vẽ mũi tên tam giác
            // Đỉnh mũi tên (hướng ra ngoài)
            int tipX = cx + (int) Math.round(nx * (edgeR + 5));
            int tipY = cy + (int) Math.round(nz * (edgeR + 5));

            // Vuông góc với hướng
            double perpX = -nz;
            double perpY = nx;

            // 2 cánh mũi tên
            int w1x = ax + (int) Math.round(perpX * 4);
            int w1y = ay + (int) Math.round(perpY * 4);
            int w2x = ax - (int) Math.round(perpX * 4);
            int w2y = ay - (int) Math.round(perpY * 4);

            // Vẽ đường từ cánh đến đỉnh
            drawLine(canvas, tipX, tipY, w1x, w1y, arrowColor);
            drawLine(canvas, tipX, tipY, w2x, w2y, arrowColor);
            drawLine(canvas, w1x, w1y, w2x, w2y, outlineColor);

            // Điểm sáng ở đỉnh
            drawDot(canvas, tipX, tipY, arrowColor, 1);
        }

        // ── Vẽ vòng tròn ──────────────────────────────────────────────────
        private void drawCircle(MapCanvas canvas, int cx, int cy, int r, byte color) {
            for (int angle = 0; angle < 360; angle++) {
                double rad = Math.toRadians(angle);
                int x = cx + (int) Math.round(r * Math.cos(rad));
                int y = cy + (int) Math.round(r * Math.sin(rad));
                if (x >= 0 && x < MAP_SIZE && y >= 0 && y < MAP_SIZE) {
                    canvas.setPixel(x, y, color);
                }
            }
        }

        // ── Vẽ đường thẳng (Bresenham) ───────────────────────────────────
        private void drawLine(MapCanvas canvas, int x0, int y0, int x1, int y1, byte color) {
            int dx = Math.abs(x1 - x0);
            int dy = Math.abs(y1 - y0);
            int sx = x0 < x1 ? 1 : -1;
            int sy = y0 < y1 ? 1 : -1;
            int err = dx - dy;

            while (true) {
                if (x0 >= 0 && x0 < MAP_SIZE && y0 >= 0 && y0 < MAP_SIZE) {
                    canvas.setPixel(x0, y0, color);
                }
                if (x0 == x1 && y0 == y1) break;
                int e2 = 2 * err;
                if (e2 > -dy) { err -= dy; x0 += sx; }
                if (e2 < dx)  { err += dx; y0 += sy; }
            }
        }
    }
}
