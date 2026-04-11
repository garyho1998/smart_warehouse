# 多 WMS 整合設計

## 場景：3 個倉，3 套唔同系統

| 倉庫 | 用緊嘅系統 | 點叫「儲位」 | API 返回格式 |
|---|---|---|---|
| 倉庫 A（深圳） | 富勒 WMS | `bin_location` | `{"bin_code": "A-01-03", "zone_name": "揀貨區"}` |
| 倉庫 B（上海） | 旺店通 WMS | `slot` | `{"slot_id": "SH_R2_S3", "area": "storage", "floor": 2}` |
| 倉庫 C（廣州） | Excel 手動管理 | 冇 API | CSV: `位置編號, 區域\nGZ-001, 收貨區` |

同一個概念「儲位」，三個系統叫法、格式、結構完全唔同。

---

## 架構概覽

```
            你嘅 Platform
         ┌──────────────────┐
         │  Canonical Model │ ← 呢個就係你嘅 Ontology
         │  (統一數據模型)    │
         └───────┬──────────┘
                 │
    ┌────────────┼────────────┐
    ▼            ▼            ▼
 Adapter A    Adapter B    Adapter C
    │            │            │
    ▼            ▼            ▼
 WMS-富勒    WMS-旺店通    CSV/Excel
```

---

## Step 1：定義統一模型（Ontology）

```java
// platform-core/model/Location.java
// 全平台只認呢個格式

@Entity
public class Location {
    @Id
    private String id;           // 統一 ID
    private String warehouseId;  // 屬於邊個倉
    private String code;         // 儲位編碼
    private String zone;         // 區域
    private Integer floor;       // 樓層
    private LocationType type;   // PICK, STORAGE, DOCK, RECEIVE
}
```

---

## Step 2：每個系統寫一個 Adapter

### 統一接口

```java
public interface WmsAdapter {
    List<Location> getLocations();
    List<Order> getOrders();
    void updateTaskStatus(String taskId, TaskStatus status);
}
```

### 富勒 Adapter

```java
// integration-svc/adapter/FluxWmsAdapter.java

public class FluxWmsAdapter implements WmsAdapter {

    public List<Location> getLocations() {
        // 富勒 API 返回: {"bin_code": "A-01-03", "zone_name": "揀貨區"}
        var bins = fluxClient.get("/api/v2/bins");

        return bins.stream().map(bin -> {
            Location loc = new Location();
            loc.setId("WH-A_" + bin.getBinCode());
            loc.setWarehouseId("WH-A");
            loc.setCode(bin.getBinCode());              // "A-01-03"
            loc.setZone(bin.getZoneName());             // "揀貨區"
            loc.setFloor(1);                            // 富勒冇樓層概念，默認 1
            loc.setType(mapZoneToType(bin.getZoneName()));
            return loc;
        }).toList();
    }

    private LocationType mapZoneToType(String zone) {
        if (zone.contains("揀貨")) return LocationType.PICK;
        if (zone.contains("存儲")) return LocationType.STORAGE;
        return LocationType.OTHER;
    }
}
```

### 旺店通 Adapter

```java
// integration-svc/adapter/WdtWmsAdapter.java

public class WdtWmsAdapter implements WmsAdapter {

    public List<Location> getLocations() {
        // 旺店通 API 返回: {"slot_id": "SH_R2_S3", "area": "storage", "floor": 2}
        var slots = wdtClient.get("/open/api/warehouse/slots");

        return slots.stream().map(slot -> {
            Location loc = new Location();
            loc.setId("WH-B_" + slot.getSlotId());
            loc.setWarehouseId("WH-B");
            loc.setCode(slot.getSlotId());              // "SH_R2_S3"
            loc.setZone(slot.getArea());                // "storage"
            loc.setFloor(slot.getFloor());              // 2
            loc.setType(mapAreaToType(slot.getArea()));
            return loc;
        }).toList();
    }

    private LocationType mapAreaToType(String area) {
        return switch (area) {
            case "storage" -> LocationType.STORAGE;
            case "picking" -> LocationType.PICK;
            case "dock"    -> LocationType.DOCK;
            default        -> LocationType.OTHER;
        };
    }
}
```

### CSV Adapter（Excel 倉庫）

```java
// integration-svc/adapter/CsvAdapter.java

public class CsvAdapter implements WmsAdapter {

    public List<Location> getLocations() {
        // CSV: 位置編號, 區域
        //      GZ-001, 收貨區
        return CsvParser.parse(sftpDownload("/warehouse_c/locations.csv"))
            .stream().map(row -> {
                Location loc = new Location();
                loc.setId("WH-C_" + row.get("位置編號"));
                loc.setWarehouseId("WH-C");
                loc.setCode(row.get("位置編號"));
                loc.setZone(row.get("區域"));
                loc.setFloor(1);
                loc.setType(mapZoneToType(row.get("區域")));
                return loc;
            }).toList();
    }
}
```

### 機器人 Adapter（同理）

```java
public interface RobotAdapter {
    void dispatchTask(MoveTask task);
    RobotStatus getStatus(String robotId);
}

// 海康
public class HikrobotAdapter implements RobotAdapter { ... }
// 極智嘉
public class GeekPlusAdapter implements RobotAdapter { ... }
```

---

## Step 3：上層功能只認統一模型

```java
// operations-svc/DashboardService.java
// 完全唔知底層係富勒定旺店通定 CSV

public class DashboardService {

    private final List<WmsAdapter> adapters;  // Spring 自動注入所有 Adapter

    public List<Location> getAllLocations() {
        return adapters.stream()
            .flatMap(a -> a.getLocations().stream())
            .toList();
    }

    public Map<String, Long> getLocationCountByType() {
        return getAllLocations().stream()
            .collect(groupingBy(
                loc -> loc.getType().name(),
                counting()
            ));
        // 結果: {PICK: 150, STORAGE: 480, DOCK: 30, RECEIVE: 20}
        // 三個倉嘅數據混喺一齊，唔使理佢哋原本叫咩
    }
}
```

---

## 數據流圖

```
富勒 API
{"bin_code":"A-01-03","zone_name":"揀貨區"}
        │
        ▼
   FluxAdapter（翻譯）
        │
        ▼
Location{id="WH-A_A-01-03", zone="揀貨區", type=PICK}  ──┐
                                                          │
旺店通 API                                                │
{"slot_id":"SH_R2_S3","area":"storage","floor":2}         │
        │                                                 │    統一格式
        ▼                                                 ├──→ Dashboard
   WdtAdapter（翻譯）                                      │    Workflow
        │                                                 │    Alert
        ▼                                                 │    Report
Location{id="WH-B_SH_R2_S3", zone="storage", type=STORAGE}──┤
                                                          │
CSV 文件                                                   │
"GZ-001, 收貨區"                                           │
        │                                                 │
        ▼                                                 │
   CsvAdapter（翻譯）                                      │
        │                                                 │
        ▼                                                 │
Location{id="WH-C_GZ-001", zone="收貨區", type=RECEIVE}  ──┘
```

---

## 每個 Adapter 翻譯咩

| 翻譯項 | 例子 |
|---|---|
| **欄位名** | `bin_code` → `code`，`slot_id` → `code`，`位置編號` → `code` |
| **值嘅格式** | `"揀貨區"` → `PICK`，`"storage"` → `STORAGE`，`"收貨區"` → `RECEIVE` |
| **缺失欄位** | 富勒冇 `floor` → 默認填 1 |
| **ID 衝突** | 加倉庫前綴 `"WH-A_"` 避免唔同倉嘅 ID 撞 |

---

## 接入方式視乎廠商

| 接入方式 | 適用場景 | 例子 |
|---|---|---|
| REST API | 現代 SaaS WMS | 旺店通、聚水潭、科箭 |
| Database polling | 老式本地 WMS | 直接讀 WMS 數據庫 |
| CSV / SFTP | 傳統 ERP | 定時導出文件 |
| Message Queue | 實時場景 | Kafka / RabbitMQ |
| WebSocket | 機器人實時狀態 | 海康 iWMS、極智嘉 |

---

## 點解 Ontology 喺整合場景更加需要

| | 冇 Ontology | 有 Ontology |
|---|---|---|
| 接 1 個 WMS | OK，直接用佢嘅格式 | 多此一舉 |
| 接 2–3 個 WMS | 開始痛苦 | 值得 |
| 接 N 個 WMS + M 個機器人 | **地獄**（N×M 個翻譯） | **必須**（N+M 個 Adapter） |

加第 4 個倉？**只寫一個新 Adapter**，Dashboard / Workflow / Alert 一行代碼都唔使改。呢個就係 Ontology 嘅價值。
