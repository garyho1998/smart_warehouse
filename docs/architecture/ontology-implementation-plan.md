# Ontology Implementation Plan — DB-Driven Engine

## 目標

建一個可以 `mvn spring-boot:run` 即跑嘅 ontology demo。核心 architectural 決定：**ontology schema 存喺 DB meta-tables，唔係 hardcode 喺 Java code，亦唔係靜態 YAML file**。同 PLTR 嘅 Ontology Metadata Service（OMS）概念一致 — schema 係 runtime metadata，可以即時改動。

### 點解用 DB 唔用 YAML

| | YAML file | DB meta-tables |
|---|---|---|
| 加新 Object Type | 改 YAML → **重啟** | INSERT → **即時生效** |
| Version control | Git | DB trigger → schema_history ✅ |
| Runtime 改 schema | ❌ | ✅ |
| 可以建 Admin UI | ❌ | ✅ |
| 同 PLTR OMS 對齊 | ⚠️ 靜態 file | ✅ Runtime metadata service |

`ontology.yml` 保留作為 **bootstrap seed**（首次啟動時 load 入 DB），之後一切以 DB 為準。

## Scope

**做：** DB-driven ontology engine + Layer 1（Canonical Model）+ Layer 2（Graph Traversal）+ Layer 3 taste（Action + Audit）+ Schema version history
**唔做：** WMS adapters、robot control、workflow engine、frontend、authentication、index pipeline

## 核心設計

```
ontology.yml（bootstrap seed，只用一次）
       │
       │ 首次啟動
       v
┌─────────────────────────────────────────────────────┐
│   DB Meta-Tables（= mini OMS）                       │
│                                                      │
│   object_type_def   ← Object Type 定義               │
│   property_def      ← Property 定義                  │
│   link_type_def     ← Relationship 定義              │
│   action_type_def   ← Action 定義                    │
│   schema_history    ← Trigger-based version control  │
└──────────────────────┬──────────────────────────────┘
                       │
                       │ SchemaMetaRepository 讀 DB（cached）
                       v
┌─────────────────────────────────────────────────────┐
│   Ontology Engine（通用，唔係 per-entity code）        │
│   - DataTableSyncer     schema → 建/改 data tables    │
│   - GenericRepository   通用 CRUD for 任何 object type │
│   - GraphService        沿 link 定義 traverse          │
│   - ActionExecutor      按 action 定義 orchestrate     │
│   - SideEffectExecutor  跨 object 寫入                 │
│   - AuditService        審計記錄                       │
│                                                       │
│   SchemaService（service 層）                          │
│   - schema write orchestration                        │
│   - 寫 meta → sync table → invalidate cache           │
└───────────────────────┬─────────────────────────────┘
                        │
                        v
┌─────────────────────────────────────────────────────┐
│   Generic REST API                                   │
│   GET  /api/schema/types              ← 讀 + 寫 schema│
│   POST /api/schema/types              ← runtime 加 type│
│   GET  /api/objects/{type}            ← 通用 CRUD      │
│   GET  /api/graph/traverse/{type}/{id}                │
│   POST /api/actions/{actionName}                      │
└───────────────────────┬─────────────────────────────┘
                        │
                        v
┌─────────────────────────────────────────────────────┐
│   H2 / PostgreSQL                                    │
│   ├── Meta-tables（schema 定義）                      │
│   ├── Data tables（每個 Object Type 一張）             │
│   └── schema_history（trigger-based audit）           │
└─────────────────────────────────────────────────────┘
```

**加一個新 Object Type = INSERT 入 meta-table → 即時生效，唔使重啟，唔使寫 Java。**

---

## DB Meta-Tables

### object_type_def

```sql
CREATE TABLE object_type_def (
    id           VARCHAR(100) PRIMARY KEY,  -- e.g. 'Task'
    description  VARCHAR(500),
    primary_key  VARCHAR(100) NOT NULL DEFAULT 'id',
    created_at   TIMESTAMP DEFAULT NOW(),
    updated_at   TIMESTAMP DEFAULT NOW()
);
```

### property_def

```sql
CREATE TABLE property_def (
    id              SERIAL PRIMARY KEY,
    object_type_id  VARCHAR(100) NOT NULL REFERENCES object_type_def(id),
    name            VARCHAR(100) NOT NULL,     -- e.g. 'status'
    type            VARCHAR(50)  NOT NULL,     -- string, integer, decimal, boolean, enum, timestamp
    required        BOOLEAN DEFAULT false,
    unique_col      BOOLEAN DEFAULT false,
    default_value   VARCHAR(255),
    enum_values     VARCHAR(1000),             -- comma-separated: 'PENDING,ASSIGNED,IN_PROGRESS,...'
    created_at      TIMESTAMP DEFAULT NOW(),
    UNIQUE(object_type_id, name)
);
```

### link_type_def

```sql
CREATE TABLE link_type_def (
    id           VARCHAR(100) PRIMARY KEY,   -- e.g. 'task_assigned_to_robot'
    from_type    VARCHAR(100) NOT NULL REFERENCES object_type_def(id),
    to_type      VARCHAR(100) NOT NULL REFERENCES object_type_def(id),
    foreign_key  VARCHAR(100) NOT NULL,      -- e.g. 'robotId'
    cardinality  VARCHAR(20)  NOT NULL,      -- one_to_many, many_to_one
    description  VARCHAR(500),
    created_at   TIMESTAMP DEFAULT NOW()
);
```

### action_type_def

```sql
CREATE TABLE action_type_def (
    id              VARCHAR(100) PRIMARY KEY,  -- e.g. 'completeTask'
    description     VARCHAR(500),
    object_type_id  VARCHAR(100) NOT NULL REFERENCES object_type_def(id),
    parameters      TEXT,         -- JSON: {"actor": {"type":"string","required":true}}
    preconditions   TEXT,         -- JSON: ["status in [ASSIGNED, IN_PROGRESS]"]
    mutations       TEXT,         -- JSON: [{"set":{"status":"COMPLETED","completedAt":"NOW"}}]
    side_effects    TEXT,         -- JSON: [{"target":"Robot","via":"robotId","set":{"status":"IDLE"}}]
    audit           BOOLEAN DEFAULT true,
    created_at      TIMESTAMP DEFAULT NOW()
);
```

### schema_history（Trigger-based version control）

```sql
CREATE TABLE schema_history (
    id          SERIAL PRIMARY KEY,
    table_name  VARCHAR(100),       -- 'object_type_def', 'property_def', etc.
    record_id   VARCHAR(255),
    operation   VARCHAR(10),        -- INSERT / UPDATE / DELETE
    old_value   TEXT,               -- JSON (H2 冇 JSONB，用 TEXT)
    new_value   TEXT,               -- JSON
    changed_at  TIMESTAMP DEFAULT NOW()
);

-- Trigger function（PG 語法，H2 用 Java trigger）
CREATE FUNCTION track_schema_change() RETURNS TRIGGER AS $$
BEGIN
    INSERT INTO schema_history(table_name, record_id, operation, old_value, new_value)
    VALUES (
        TG_TABLE_NAME,
        COALESCE(NEW.id::TEXT, OLD.id::TEXT),
        TG_OP,
        CASE WHEN TG_OP != 'INSERT' THEN row_to_json(OLD)::TEXT END,
        CASE WHEN TG_OP != 'DELETE' THEN row_to_json(NEW)::TEXT END
    );
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

-- Attach to all meta-tables
CREATE TRIGGER trg_object_type  AFTER INSERT OR UPDATE OR DELETE ON object_type_def
    FOR EACH ROW EXECUTE FUNCTION track_schema_change();
CREATE TRIGGER trg_property     AFTER INSERT OR UPDATE OR DELETE ON property_def
    FOR EACH ROW EXECUTE FUNCTION track_schema_change();
CREATE TRIGGER trg_link_type    AFTER INSERT OR UPDATE OR DELETE ON link_type_def
    FOR EACH ROW EXECUTE FUNCTION track_schema_change();
CREATE TRIGGER trg_action_type  AFTER INSERT OR UPDATE OR DELETE ON action_type_def
    FOR EACH ROW EXECUTE FUNCTION track_schema_change();
```

> Note: H2 唔支持 PG-style trigger。MVP 用 H2 時，schema_history 由 `OntologySchemaLoader` 嘅 Java code 寫入。Production 切 PG 後用真 trigger。

---

## Bootstrap Flow

```
首次啟動：
  1. MetaTableInitializer 建 meta-tables（object_type_def, property_def, ...）
  2. SchemaBootstrap 檢查 meta-tables 係咪空
  3. 如果空 → SchemaBootstrap 讀 ontology.yml → INSERT 入 meta-tables
  4. 如果已有數據 → 跳過，以 DB 為準
  5. SchemaMetaRepository 讀 meta-tables → 建 + cache OntologySchema object
  6. DataTableSyncer 用 OntologySchema 建 data tables
  7. 如果 seed-data.sql 存在 → load seed data

之後修改 schema（runtime，唔使重啟）：
  → SchemaService 寫 meta-tables + schema_history（@Transactional）
  → DataTableSyncer 同步建/改 data tables
  → SchemaMetaRepository.invalidateCache()
  → 下一個 request 拿到最新 schema
```

---

## ontology.yml（Bootstrap Seed）

首次啟動時 load 入 DB，之後唔再讀。完整定義 9 個 Object Types、12 個 Link Types、1 個 Action Type。

```yaml
objectTypes:

  Warehouse:
    description: "倉庫設施"
    primaryKey: id
    properties:
      id:       { type: string, required: true }
      code:     { type: string, required: true, unique: true }
      name:     { type: string, required: true }
      address:  { type: string }
      wmsType:  { type: enum, values: [FLUX, WDT, CSV, MANUAL] }

  Zone:
    description: "倉庫內功能區域"
    primaryKey: id
    properties:
      id:           { type: string, required: true }
      warehouseId:  { type: string, required: true }
      code:         { type: string, required: true }
      name:         { type: string, required: true }
      type:         { type: enum, values: [RECEIVE, STORAGE, PICK, DOCK, STAGING, QC] }
      capacity:     { type: integer }

  Location:
    description: "物理儲位"
    primaryKey: id
    properties:
      id:          { type: string, required: true }
      zoneId:      { type: string, required: true }
      code:        { type: string, required: true }
      floor:       { type: integer, default: 1 }
      type:        { type: enum, values: [BIN, SHELF, FLOOR, DOCK_DOOR] }
      maxWeightKg: { type: decimal }
      occupied:    { type: boolean, default: false }

  Sku:
    description: "庫存單位"
    primaryKey: id
    properties:
      id:        { type: string, required: true }
      code:      { type: string, required: true, unique: true }
      name:      { type: string, required: true }
      barcode:   { type: string }
      weightKg:  { type: decimal }
      category:  { type: string }

  Inventory:
    description: "庫存記錄（Location + SKU + 數量）"
    primaryKey: id
    properties:
      id:         { type: string, required: true }
      locationId: { type: string, required: true }
      skuId:      { type: string, required: true }
      quantity:   { type: integer, required: true, default: 0 }
      lotNumber:  { type: string }

  WarehouseOrder:
    description: "倉庫訂單"
    primaryKey: id
    properties:
      id:          { type: string, required: true }
      warehouseId: { type: string, required: true }
      externalId:  { type: string }
      type:        { type: enum, values: [INBOUND, OUTBOUND, TRANSFER] }
      status:      { type: enum, values: [PENDING, IN_PROGRESS, COMPLETED, CANCELLED] }
      priority:    { type: integer, default: 0 }

  OrderLine:
    description: "訂單明細行"
    primaryKey: id
    properties:
      id:              { type: string, required: true }
      orderId:         { type: string, required: true }
      skuId:           { type: string, required: true }
      quantity:        { type: integer, required: true }
      pickedQuantity:  { type: integer, default: 0 }

  Robot:
    description: "自動化機器人"
    primaryKey: id
    properties:
      id:                { type: string, required: true }
      code:              { type: string, required: true, unique: true }
      brand:             { type: enum, values: [HIKROBOT, GEEKPLUS, QUICKTRON, SIMULATED] }
      model:             { type: string }
      status:            { type: enum, values: [IDLE, BUSY, CHARGING, OFFLINE, ERROR] }
      batteryPct:        { type: integer }
      currentLocationId: { type: string }
      assignedZoneId:    { type: string }

  Task:
    description: "工作任務 — 核心 entity，連結 Order + Location + Robot"
    primaryKey: id
    properties:
      id:             { type: string, required: true }
      orderId:        { type: string }
      type:           { type: enum, values: [MOVE, PICK, PUTAWAY, SORT, TRANSFER] }
      status:         { type: enum, values: [PENDING, ASSIGNED, IN_PROGRESS, COMPLETED, FAILED, CANCELLED] }
      priority:       { type: integer, default: 0 }
      robotId:        { type: string }
      fromLocationId: { type: string }
      toLocationId:   { type: string }
      assignedAt:     { type: timestamp }
      completedAt:    { type: timestamp }

linkTypes:

  warehouse_has_zones:
    from: Warehouse
    to: Zone
    foreignKey: warehouseId
    cardinality: one_to_many
    description: "倉庫包含多個區域"

  zone_has_locations:
    from: Zone
    to: Location
    foreignKey: zoneId
    cardinality: one_to_many

  location_has_inventory:
    from: Location
    to: Inventory
    foreignKey: locationId
    cardinality: one_to_many

  sku_in_inventory:
    from: Sku
    to: Inventory
    foreignKey: skuId
    cardinality: one_to_many

  warehouse_has_orders:
    from: Warehouse
    to: WarehouseOrder
    foreignKey: warehouseId
    cardinality: one_to_many

  order_has_lines:
    from: WarehouseOrder
    to: OrderLine
    foreignKey: orderId
    cardinality: one_to_many

  order_has_tasks:
    from: WarehouseOrder
    to: Task
    foreignKey: orderId
    cardinality: one_to_many

  task_assigned_to_robot:
    from: Task
    to: Robot
    foreignKey: robotId
    cardinality: many_to_one

  task_from_location:
    from: Task
    to: Location
    foreignKey: fromLocationId
    cardinality: many_to_one

  task_to_location:
    from: Task
    to: Location
    foreignKey: toLocationId
    cardinality: many_to_one

  robot_at_location:
    from: Robot
    to: Location
    foreignKey: currentLocationId
    cardinality: many_to_one

  robot_in_zone:
    from: Robot
    to: Zone
    foreignKey: assignedZoneId
    cardinality: many_to_one

actionTypes:

  completeTask:
    description: "完成一個 task"
    objectType: Task
    parameters:
      actor: { type: string, required: true }
    preconditions:
      - "status in [ASSIGNED, IN_PROGRESS]"
    mutations:
      - set: { status: COMPLETED, completedAt: NOW }
    sideEffects:
      - target: Robot
        via: robotId
        set: { status: IDLE }
    audit: true
```

---

## Project Structure

```
ontology-demo/
├── pom.xml                                  ← Spring Boot 3.4 + Java 21 + H2
├── src/main/java/com/warehouse/ontology/
│   ├── OntologyDemoApplication.java
│   │
│   ├── schema/                              ← Schema 層（= mini OMS）
│   │   ├── OntologySchema.java              ← 頂層 POJO：持有所有 types/links/actions（cached）
│   │   ├── ObjectTypeDef.java               ← Object Type 定義
│   │   ├── PropertyDef.java                 ← Property 定義
│   │   ├── LinkTypeDef.java                 ← Link 定義
│   │   ├── ActionTypeDef.java               ← Action 定義
│   │   ├── SchemaMetaRepository.java        ← ★ 讀 DB meta-tables → OntologySchema（= DAO + cache）
│   │   └── SchemaBootstrap.java             ← 首次啟動：讀 ontology.yml → INSERT 入 meta-tables
│   │
│   ├── engine/                              ← 通用引擎（核心，唔知 schema 來源）
│   │   ├── MetaTableInitializer.java        ← 建 meta-tables DDL（固定，跑一次）
│   │   ├── DataTableSyncer.java             ← schema → CREATE TABLE / ALTER TABLE / FK / index
│   │   ├── GenericRepository.java           ← 通用 CRUD（JdbcTemplate）
│   │   ├── GraphService.java                ← ★ 沿 linkTypes traverse
│   │   ├── ActionExecutor.java              ← 按 actionTypes orchestrate 執行
│   │   ├── SideEffectExecutor.java          ← 跨 object type 寫入（從 ActionExecutor 拆出）
│   │   └── AuditService.java                ← 寫 audit_trail table
│   │
│   ├── service/                             ← Orchestration 層
│   │   └── SchemaService.java               ← ★ Schema write orchestration（寫 meta → sync table → invalidate cache）
│   │
│   ├── api/                                 ← REST Controllers（thin，只做 request/response mapping）
│   │   ├── SchemaController.java            ← Schema CRUD API → delegate to SchemaService
│   │   ├── ObjectController.java            ← 通用 data CRUD per type
│   │   ├── GraphController.java             ← Graph traversal endpoints
│   │   └── ActionController.java            ← POST /api/actions/{name}
│   │
│   └── (audit/ 已合併入 engine/)
│
├── src/main/resources/
│   ├── application.yml
│   ├── schema/
│   │   └── meta-tables.sql                  ← Meta-table DDL + trigger
│   ├── ontology.yml                         ← Bootstrap seed（首次啟動用）
│   └── seed-data.sql                        ← 深圳工廠場景
│
└── src/test/java/
    ├── schema/SchemaMetaRepositoryTest.java  ← 從 DB 讀 schema 正確
    ├── schema/SchemaBootstrapTest.java       ← YAML → DB bootstrap 正確
    ├── engine/DataTableSyncerTest.java       ← Tables 建得啱
    ├── engine/GenericRepositoryTest.java
    ├── engine/GraphServiceTest.java          ← ★ 重點
    ├── engine/ActionExecutorTest.java
    └── service/SchemaServiceTest.java        ← Runtime schema change 全流程
```

### 關鍵設計：Engine 唔知 schema 來源

Engine 層只認 `OntologySchema` Java object，唔知佢從邊度嚟：

```
Bootstrap seed:  ontology.yml → SchemaBootstrap → DB meta-tables
                                                       │
Runtime:                                               │
  SchemaMetaRepository 讀 DB meta-tables ──→ OntologySchema（cached）──→ Engine
                                                ↑              │
                                    Engine 只認呢個 object      invalidate on schema write
                                    唔知 source 係 DB / YAML / API
```

### Cache Invalidation 策略

`SchemaMetaRepository` 維護 `OntologySchema` cache。Schema 改動時由 `SchemaService` 負責 invalidate：

```
SchemaService.createType():
  ① @Transactional: INSERT into meta-tables + 寫 schema_history
  ② DataTableSyncer.syncTable() → CREATE TABLE
  ③ SchemaMetaRepository.invalidateCache() → 下次 read 重新 SELECT
```

Engine 永遠透過 `SchemaMetaRepository.getSchema()` 拿 cached `OntologySchema`，唔使理 invalidation。

JdbcTemplate 唔用 JPA — JPA 需要 compile-time entity classes，同 dynamic schema 矛盾。

---

## Key Java Classes

### SchemaBootstrap（新）

首次啟動時將 `ontology.yml` seed 入 DB meta-tables。

```java
@Component
public class SchemaBootstrap {
    // 1. 檢查 object_type_def 係咪空
    // 2. 如果空 → 讀 ontology.yml → INSERT 入 meta-tables
    // 3. 如果已有數據 → skip（DB 係 source of truth）
    public void seedIfEmpty() { ... }
}
```

### SchemaMetaRepository（原 OntologySchemaLoader，改名反映 DAO 本質）

```java
@Component
public class SchemaMetaRepository {
    private volatile OntologySchema cached;

    // SELECT from object_type_def, property_def, link_type_def, action_type_def
    // → 組裝成 OntologySchema POJO → cache
    public OntologySchema getSchema() { ... }

    // Schema write 後由 SchemaService 呼叫
    public void invalidateCache() { cached = null; }

    // Direct meta-table CRUD（供 SchemaService 使用）
    public void insertObjectType(ObjectTypeDef def) { ... }
    public void insertProperty(PropertyDef def) { ... }
    public void insertLinkType(LinkTypeDef def) { ... }
    public void insertActionType(ActionTypeDef def) { ... }
}
```

### MetaTableInitializer（從 SchemaManager 拆出 — 固定 DDL）

```java
@Component
public class MetaTableInitializer {
    // 建 meta-tables：object_type_def, property_def, link_type_def,
    //   action_type_def, schema_history
    // 跑一次，CREATE TABLE IF NOT EXISTS
    // 喺 @PostConstruct 或 ApplicationRunner 執行
    public void initialize() { ... }
}
```

### DataTableSyncer（從 SchemaManager 拆出 — dynamic DDL）

```java
@Component
public class DataTableSyncer {
    // 讀 OntologySchema → 對每個 Object Type：
    //   CREATE TABLE IF NOT EXISTS（data table）
    //   ALTER TABLE ADD COLUMN（如果 schema 加咗新 property）
    //
    // Property type → SQL type：
    //   string → VARCHAR(255), integer → INTEGER, decimal → DECIMAL(15,4)
    //   boolean → BOOLEAN, enum → VARCHAR(50), timestamp → TIMESTAMP
    //
    // 自動加 FK constraints from linkTypes
    // 自動加 B-tree indexes on FK columns
    public void syncAll(OntologySchema schema) { ... }
    public void syncTable(String typeName, ObjectTypeDef def) { ... }  // 單個 type
}
```

### GenericRepository

一個 class 服務所有 object types。

```java
@Repository
public class GenericRepository {
    Map<String, Object> findById(String typeName, String id);
    List<Map<String, Object>> findAll(String typeName);
    List<Map<String, Object>> findByProperty(String typeName, String prop, Object value);
    String insert(String typeName, Map<String, Object> data);
    void update(String typeName, String id, Map<String, Object> data);
}
```

camelCase property names → snake_case column names（e.g., `warehouseId` → `warehouse_id`）。

### GraphService（★ 重點）

```java
@Service
public class GraphService {
    // 通用：由任何 object 出發，行 N 層
    GraphResult traverse(String startType, String startId, int maxDepth);

    // 專門：由 failed task 出發，搵原因 + 影響
    // Task → Robot（check battery）→ Location → Zone → 同 zone 其他 tasks → Order
    GraphResult traceAnomaly(String taskId);
}
```

返回結構化 `GraphResult`（nodes + edges + insights）。

### ActionExecutor（瘦身 — orchestrate only）

```java
@Service
public class ActionExecutor {
    // Orchestrate 執行流程，每步 delegate 到專門嘅 class：
    // 1. 讀 actionType 定義（from SchemaMetaRepository）
    // 2. Validate preconditions（inline，簡單邏輯）
    // 3. Execute mutations（via GenericRepository）
    // 4. Execute sideEffects（delegate → SideEffectExecutor）
    // 5. Write audit trail（delegate → AuditService）
    ActionResult execute(String actionName, Map<String, Object> params);
}
```

### SideEffectExecutor（從 ActionExecutor 拆出）

```java
@Service
public class SideEffectExecutor {
    // 跨 object type 寫入。例如 completeTask 嘅 side effect：
    //   target: Robot, via: robotId, set: { status: IDLE }
    //
    // 流程：
    //   1. 從 source object 讀 FK value（e.g., task.robotId）
    //   2. Lookup target object（e.g., Robot by id）
    //   3. Apply mutations（e.g., status = IDLE）
    //
    // 獨立可測試：唔使跑成個 ActionExecutor 就可以 test side effect 邏輯
    void execute(List<SideEffectDef> effects, Map<String, Object> sourceObject);
}
```

### SchemaService（新 — schema write orchestration）

```java
@Service
public class SchemaService {
    // Schema 寫入嘅 single entry point。所有 schema mutation 經呢度：
    //   ① @Transactional: 寫 meta-tables + 寫 schema_history（H2 冇 trigger，Java 寫）
    //   ② DataTableSyncer.syncTable() → CREATE TABLE / ALTER TABLE
    //   ③ SchemaMetaRepository.invalidateCache()
    //
    // Controller 唔直接操作 meta-tables — 全部 delegate 到呢度

    ObjectTypeDef createType(ObjectTypeDef def, List<PropertyDef> properties);
    void addProperty(String typeName, PropertyDef property);
    void createLinkType(LinkTypeDef def);
    void createActionType(ActionTypeDef def);
    List<Map<String, Object>> getHistory(String tableName);  // 讀 schema_history
}
```

### SchemaController（瘦身 — thin controller, delegate to SchemaService）

```java
@RestController
@RequestMapping("/api/schema")
public class SchemaController {
    // 讀（from SchemaMetaRepository）
    GET  /api/schema/types                  → 列出所有 Object Types
    GET  /api/schema/types/{name}           → 一個 type 嘅完整定義
    GET  /api/schema/links                  → 列出所有 Link Types
    GET  /api/schema/actions                → 列出所有 Action Types
    GET  /api/schema/history                → Schema 變更歷史

    // 寫（delegate to SchemaService）
    POST /api/schema/types                  → 新增 Object Type + properties
    PUT  /api/schema/types/{name}           → 修改 Object Type
    POST /api/schema/types/{name}/properties → 加 property（= ALTER TABLE ADD COLUMN）
    POST /api/schema/links                  → 新增 Link Type
    POST /api/schema/actions                → 新增 Action Type
}
```

---

## Seed Data

深圳工廠真實場景快照（`seed-data.sql`，bootstrap 後自動 load）：

| Data | 數量 | 用途 |
|---|---|---|
| Warehouse | 1（SZ-WH-001 深圳工廠） | 頂層 |
| Zone | 4（RECEIVE, STORAGE, PICK, DOCK） | 展示區域分類 |
| Location | 16（每 zone 4 個） | 展示 zone → location 關係 |
| SKU | 6（重量 0.02kg ~ 45kg） | 展示 weight 差異 |
| Inventory | 10 | 展示 location + SKU 關係 |
| Robot | 2（R-HIK-001 BUSY / R-GEK-001 IDLE 低電量 15%） | 展示 robot 狀態 |
| Order | 3（1 inbound / 2 outbound） | 展示訂單流程 |
| Task | 5（TSK-005 = FAILED） | **★ Graph traversal demo 起點** |

TSK-005 = FAILED 係 demo 關鍵。Graph traversal 由呢個 task 出發會發現：
- Robot R-GEK-001 電量只有 15% → 可能係失敗原因
- 同一個 robot 仲有其他 tasks → 都有風險
- 呢個 task 屬於 order ORD-OUT-001 → 客戶訂單受影響

---

## Demo Endpoints

### Schema CRUD（★ 新 — runtime 改 schema）
```
GET  /api/schema/types                          → 列出所有 object types
GET  /api/schema/types/Task                     → Task 嘅完整定義
POST /api/schema/types                          → runtime 新增 object type
POST /api/schema/types/Task/properties          → 加 property → ALTER TABLE
GET  /api/schema/links                          → 列出所有 link types
GET  /api/schema/actions                        → 列出所有 action types
GET  /api/schema/history                        → schema 變更記錄
GET  /api/schema/history?table=object_type_def  → 過濾特定 table
```

### Generic Object CRUD
```
GET /api/objects/Warehouse                → 列出所有 warehouse
GET /api/objects/Task/{id}                → 一個 task 嘅詳情
GET /api/objects/Task?status=FAILED       → 按 property 過濾
GET /api/objects/Task/{id}/links          → 呢個 task 嘅所有關係
```

### ★ Graph Traversal
```
GET /api/graph/traverse/Task/{id}?depth=3      → 由 task 出發行 3 層
GET /api/graph/trace-anomaly/Task/{id}         → 專門嘅異常追蹤
GET /api/graph/impact/Robot/{id}               → robot 故障影響範圍
```

### Actions
```
POST /api/actions/completeTask            → body: { taskId, actor }
GET  /api/audit?entityType=Task&entityId=x → 審計記錄
```

---

## Implementation Steps

### Step 1：Scaffold + Meta-Tables（~3h）
- `pom.xml`（Spring Boot 3.4 + JdbcTemplate + SnakeYAML + H2）
- `application.yml`（H2 MODE=PostgreSQL）
- `meta-tables.sql` — DDL for object_type_def, property_def, link_type_def, action_type_def, schema_history
- Schema POJOs：`OntologySchema`, `ObjectTypeDef`, `PropertyDef`, `LinkTypeDef`, `ActionTypeDef`
- `MetaTableInitializer` — 讀 meta-tables.sql → CREATE TABLE
- Test: meta-tables 建得啱

### Step 2：Schema Bootstrap + Meta Repository（~3.5h）
- `ontology.yml`（bootstrap seed）
- `SchemaBootstrap` — 讀 YAML → INSERT 入 meta-tables（只係首次）
- `SchemaMetaRepository` — SELECT from meta-tables → `OntologySchema` POJO + cache
- Schema history：Java-based（H2 冇 PG trigger，由 SchemaService 寫 schema_history）
- Test: bootstrap 正確 + repository 讀到完整 schema + cache invalidation work

### Step 3：Data Table Syncer（~3h）
- `DataTableSyncer` — 讀 `OntologySchema` → CREATE TABLE per object type
- Property type → SQL type mapping
- 自動加 FK constraints from linkTypes
- 自動加 B-tree indexes on FK columns
- `syncTable()` — 單個 type（runtime schema change 用）
- ALTER TABLE support（schema 加 property → 自動加 column）
- `seed-data.sql` — 深圳工廠場景
- Test: data tables 建得啱 + seed data 存在

### Step 4：Generic Repository（~2.5h）
- `GenericRepository` — JdbcTemplate-based 通用 CRUD
- `findById`, `findAll`, `findByProperty`, `insert`, `update`
- camelCase → snake_case 轉換
- Test: CRUD 對每個 object type 都 work

### Step 5：Graph Service（~4h）★ 重點
- `GraphService` — 讀 linkTypes → 沿 FK traverse
- `traverse(typeName, id, depth)` — 通用 graph walk
- `traceAnomaly(taskId)` — 專門嘅異常追蹤
- 返回結構化 `GraphResult`（nodes + edges + insights）
- Test: TSK-005 traversal 返回完整路徑

### Step 6：Action Executor + Side Effects + Audit（~3h）
- `ActionExecutor` — orchestrate：讀 actionType → validate → mutate → side effects → audit
- `SideEffectExecutor` — 跨 object type 寫入（e.g., Robot.status = IDLE）
- `AuditService` — 寫 audit_trail table
- `completeTask` action end-to-end
- Test: action 執行 + side effect 生效 + audit 有記錄

### Step 7：Schema Service + REST Controllers（~3.5h）
- `SchemaService` — ★ schema write orchestration（寫 meta → write history → sync table → invalidate cache）
- `SchemaController` — thin controller, delegate to SchemaService
- `ObjectController` — 通用 data CRUD
- `GraphController` — traversal endpoints
- `ActionController` — action execution endpoint

### Step 8：Tests + Polish（~3h）
- Integration tests（全流程）
- Runtime schema change test：POST 新 type → table 自動出現 → CRUD 即用
- Edge cases（unknown type、missing required field、invalid action）
- README

---

## 時間估算

| Component | Hours |
|-----------|-------|
| Scaffold + MetaTableInitializer + DDL | 3 |
| SchemaBootstrap + SchemaMetaRepository + cache | 3.5 |
| DataTableSyncer + ALTER TABLE | 3 |
| GenericRepository | 2.5 |
| GraphService（★重點）| 4 |
| ActionExecutor + SideEffectExecutor + AuditService | 3 |
| SchemaService + REST Controllers | 3.5 |
| Tests + polish + README | 3 |
| **Total** | **~25.5h** |

比 YAML 版多 ~5h（meta-tables + bootstrap + SchemaService + cache + ALTER TABLE），但得到 runtime schema management + 清晰嘅 SRP。

---

## Tech Decisions

| Decision | Choice | Reason |
|----------|--------|--------|
| Schema storage | DB meta-tables | 同 PLTR OMS 一致：runtime metadata，唔使重啟 |
| Schema seed | ontology.yml → DB | 首次 bootstrap 用，之後 DB 係 source of truth |
| Schema versioning | schema_history table | DB trigger（PG）/ SchemaService Java code（H2）自動記錄變更 |
| Schema cache | SchemaMetaRepository cached OntologySchema | 避免每次 operation 都 SELECT meta-tables。SchemaService write 後 invalidate |
| DB access | JdbcTemplate | Dynamic schema 需要 dynamic SQL |
| DB | H2 MODE=PostgreSQL | 零 setup。Production 切 PG → 加真 trigger |
| Table 策略 | 每個 Object Type 一張真實 table | Query 性能好（vs EAV） |
| DDL 拆分 | MetaTableInitializer（固定）+ DataTableSyncer（dynamic） | SRP：基礎設施 DDL vs schema-driven DDL 係唔同嘅 concern |
| Action side effects | 獨立 SideEffectExecutor | 跨 object type 寫入同 self-mutation 分離，獨立可測試 |
| Controller pattern | Thin controller + Service 層 | SchemaController delegate to SchemaService，orchestration 唔放 controller |
| Index 層 | 冇（直接 query PG） | 萬級數據，B-tree index 夠用 |
| Java | Target 21（LTS） | Spring Boot 3.4 官方支持 |

---

## MVP vs PLTR 對比

### 概念對齊（✅ = 同 PLTR 一致）

| 概念 | PLTR | 我哋嘅 MVP | Match |
|---|---|---|---|
| Schema 係 data，唔係 code | OMS metadata service | DB meta-tables | ✅ |
| Schema runtime 可改 | Ontology Manager UI | Schema CRUD API | ✅ |
| Schema 同 data 分離 | OMS vs OSv2 | Meta-tables vs data tables | ✅ |
| Schema version control | Git-style branching | schema_history trigger | ✅ |
| 通用引擎 | Funnel + OSS | GenericRepository + Engine | ✅ |
| Relationships first-class | Link Types in OMS | link_type_def table | ✅ |
| Actions 有結構 | Action Types | action_type_def table | ✅ |
| Graph traversal | Object Explorer (UI) | GraphService (API) | ✅（冇 UI） |

### 唔做嘅（MVP 唔需要）

| PLTR Feature | 點解唔做 | 幾時做 |
|---|---|---|
| Index pipeline (Funnel) | 萬級數據，PG 直接 query 夠用 | Phase 4+（百萬級） |
| Multi-source merge | MVP 只有 1 個 WMS source | Phase 3+ |
| Edit Queue + offset | 直接 UPDATE PG，transaction 即可見 | Phase 4+ |
| Workshop (app builder) | 唔喺 scope | Phase 5+ |
| AIP (AI on ontology) | 唔喺 scope | Phase 4+ |
| Shared multi-tenant ontology | MVP 只服務 1 個客戶 | Phase 3+ |

### 升級路線

```
Phase 0-2（MVP）:
  DB meta-tables → data tables → 直接讀寫
  Schema CRUD API → runtime 改 schema

Phase 3（第 2 客戶）:
  加 Admin UI on top of Schema API
  加 Materialized View（輕量 index）
  Multi-tenant schema isolation

Phase 4+（多客戶、百萬級）:
  加 Elasticsearch（search layer）
  加 Debezium CDC（= Funnel pipeline）
  接近 PLTR 完整架構
```

---

## Verification Checklist

- [ ] `mvn spring-boot:run` 成功啟動
- [ ] Meta-tables 有 seed data（from ontology.yml bootstrap）
- [ ] Data tables 自動建立 + seed data loaded
- [ ] `GET /api/schema/types` 返回所有 object types
- [ ] `GET /api/schema/history` 返回 bootstrap INSERT 記錄
- [ ] `GET /api/objects/Warehouse` 返回 warehouses
- [ ] `GET /api/graph/trace-anomaly/Task/TSK-005` 返回完整 traversal JSON
- [ ] `POST /api/actions/completeTask` 成功 + audit trail 有記錄
- [ ] **★ `POST /api/schema/types` 新增 type → table 即時出現 → CRUD API 即用（唔使重啟）**
- [ ] `GET /api/schema/history` 顯示新 type 嘅 INSERT 記錄

## Related Docs

- [PLTR Ontology 內部架構](pltr-ontology-internals.md) — 讀寫分離 + Index Pipeline + Query-Time Merge
- [Ontology 四層價值](ontology-adapter-pattern.md) — Layer 1-4 價值模型
- [多 WMS 整合設計](integration-design.md) — Adapter pattern + Java code examples
- [Ontology SDK 筆記](../../ontology-sdk-notes.md) — Java ↔ Ontology 概念對照
