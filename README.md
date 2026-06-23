# 🎮 Minecraft Server 1.21.4 - UtilityTools

## 📁 Cấu Trúc Thư Mục

```
Minecraft/
├── paper-1.21.4-232.jar        # Server JAR (Paper 1.21.4 build 232)
├── run.bat                     # Script khởi động server
├── eula.txt                    # Chấp nhận EULA tự động
├── build-plugin.bat            # Script build plugin Java
├── plugins/
│   ├── Chunky-Bukkit-1.4.40.jar    # Pre-generate map, giảm lag
│   ├── spark-1.10.173-bukkit.jar   # Monitor CPU/RAM/TPS
│   ├── UtilityTools-1.0.0.jar      # Plugin tự viết (sau khi build)
│   └── UtilityTools-src/           # Source code plugin
│       ├── pom.xml
│       └── src/main/java/com/utilitytoolsplugin/
│           ├── UtilityToolsPlugin.java     # Main class
│           ├── TreeCapitatorListener.java  # Chặt cây nhanh
│           ├── OreVeinMinerListener.java   # Đào mạch quặng
│           ├── HammerListener.java         # Hammer 3x3
│           └── HammerCommand.java          # Lệnh /hammer
└── world/                      # World data (tạo tự động khi chạy server)
```

---

## 🚀 Hướng Dẫn Khởi Động Server

### Yêu Cầu
- **Java 21** hoặc mới hơn (download: https://adoptium.net/)
- RAM tối thiểu: **2GB**, khuyến nghị **4GB+**

### Khởi Động
1. Double-click `run.bat`
2. Chờ server khởi động hoàn toàn
3. Kết nối tại `localhost:25565`

---

## 🔧 Plugin Tự Viết - UtilityTools

### 1. 🪓 TreeCapitator (Chặt Cây Nhanh)

**Cách dùng:** Cầm rìu (Axe) và phá bất kỳ block gỗ nào → tự động chặt cả cây!

**Logic kỹ thuật:**
- Dùng **BFS (Breadth-First Search)** từ block bị phá
- Tìm tất cả gỗ cùng loại trong 26 block xung quanh (3x3x3 cube)
- Lá cây được thêm vào kết quả nhưng không được dùng để lan tiếp
- Giới hạn **500 block** tối đa để tránh lag server
- **Tiêu hao 1 độ bền** cho mỗi block bị phá

```
Ví dụ: Cây cao 8 block + 50 lá → phá 1 block → tự động phá 57 block
       Rìu tiêu hao 57 độ bền
```

### 2. ⛏️ OreVeinMiner (Đào Mạch Quặng Nhanh)

**Cách dùng:** Giữ **Shift + đào** quặng bằng cuốc (Pickaxe)

**Logic kỹ thuật:**
- Dùng **BFS 6 hướng** (trước, sau, trái, phải, trên, dưới)
- Chỉ tìm quặng **cùng loại** kề nhau trực tiếp
- Giới hạn **64 block** (1 stack) tối đa
- **Tiêu hao 1 độ bền** cho mỗi block quặng bị phá

**Quặng được hỗ trợ:**
- Coal, Iron, Copper, Gold, Redstone, Lapis, Diamond, Emerald
- Deepslate variants của tất cả quặng trên
- Nether Quartz, Nether Gold, Ancient Debris

```
Ví dụ: Mạch 8 viên Iron Ore kề nhau → Shift + đào 1 viên → phá cả 8
       Cuốc tiêu hao 8 độ bền
```

### 3. 🔨 Hammer 3x3 (Cuốc Phá 3x3)

**Cách nhận Hammer:**
```
/hammer give               - Cho bản thân Hammer kim cương
/hammer give TenPlayer     - Cho người chơi khác
/hammer give TenPlayer iron    - Cho Hammer sắt
/hammer give TenPlayer netherite - Cho Hammer netherite
```

**Hoặc:** Đặt tên cuốc là **"Hammer"** trong Anvil

**Cách dùng:** Cầm Hammer và đào bất kỳ block đá/đất/quặng

**Logic kỹ thuật:**
- Dùng **ray trace** để xác định mặt (face) bị tấn công
- Xác định **mặt phẳng 3x3 vuông góc** với hướng nhìn:
  - Nhìn lên/xuống → phá mặt phẳng **XZ** (ngang)
  - Nhìn sang NORTH/SOUTH → phá mặt phẳng **XY**
  - Nhìn sang EAST/WEST → phá mặt phẳng **ZY**
- Phá 8 block xung quanh trong mặt phẳng đó
- **Tiêu hao 1 độ bền** cho mỗi block thực sự bị phá

---

## 📦 Build Plugin (Cho Developer)

**Yêu cầu:** Maven 3.6+ và Java 21+

```bash
# Cách 1: Dùng script
double-click build-plugin.bat

# Cách 2: Thủ công
cd plugins/UtilityTools-src
mvn clean package
cp target/UtilityTools-1.0.0.jar ../
```

---

## 🛠️ Plugins Hỗ Trợ

### Chunky 1.4.40 - Pre-generate World
```
/chunky start          - Bắt đầu generate
/chunky world world    - Chọn world
/chunky radius 1000    - Bán kính 1000 block
/chunky start          - Chạy!
/chunky status         - Xem tiến độ
```

### Spark 1.10.173 - Monitor Hiệu Năng
```
/spark tps             - Xem TPS (target: 20 TPS)
/spark profiler start  - Bắt đầu profiler
/spark profiler info   - Xem kết quả
/spark heapsummary     - Xem RAM usage
```

---

## 📱 Bedrock/Mobile Hỗ Trợ (GeyserMC 2.10.1)

Cho phép **Bedrock Edition** (PE, Mobile, Windows 10/11, Xbox, PS, Switch) chơi cùng Java server.

### Cách kết nối từ Minecraft Bedrock/PE:
```
1. Mở Minecraft Bedrock Edition (PE/Mobile/Console)
2. Chọn "Play" → "Servers" → "Add Server"
3. Điền thông tin:
   - Server Name: (tùy ý)
   - Server Address: <IP của máy chạy server>
   - Port: 19132          ← PORT BEDROCK (khác Java!)
4. Nhấn "Play"
```

### Port:
| Phiên bản | Port | Giao thức |
|---|---|---|
| Java Edition | 25565 | TCP |
| Bedrock/PE | **19132** | **UDP** |

### Lưu ý:
- Cùng máy: dùng `127.0.0.1` hoặc `localhost`
- Qua internet: port forward **19132 UDP** trên router
- Tên Bedrock player có prefix `.` (ví dụ: `.Steve`)
- File cấu hình: `plugins/Geyser-Spigot/config.yml`

---

## 🔦 Đuốc Sáng Động (TorchLight)

Tự viết — khi **cầm đuốc/vật sáng**, server đặt block LIGHT ẩn theo vị trí player.

| Vật phẩm | Mức sáng |
|---|---|
| Torch, Lantern, Glowstone | 15 (tối đa) |
| Sea Lantern, Shroomlight, Lava Bucket | 15 |
| End Rod | 14 |
| Blaze Rod | 12 |
| Soul Torch, Soul Lantern | 10 |
| Glow Berries, Redstone Torch | 7 |
| Magma Block | 3 |

**Cách hoạt động:** `Material.LIGHT` (invisible block, 1.17+) đặt tại chân player,
cập nhật mỗi 3 tick, tự xóa khi đổi item. Server-side → Bedrock player cũng thấy!

---

## ⚙️ Cấu Hình Server Khuyến Nghị

Chỉnh sửa `server.properties`:
```properties
view-distance=10
simulation-distance=8
max-players=20
gamemode=survival
difficulty=normal
```

---

*Được tạo tự động bởi Antigravity AI Agent*
*Paper 1.21.4 | Geyser 2.10.1 | Floodgate 2.2.5 | Chunky 1.4.40 | Spark 1.10.173*

