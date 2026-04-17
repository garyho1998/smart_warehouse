# WMS Adapter Demo Implementation Plan

> **For Claude:** REQUIRED SUB-SKILL: Use superpowers-extended-cc:executing-plans to implement this plan task-by-task.

**Goal:** Add 2 mock WMS servers (旺店通, 聚水潭) + 2 adapters that pull data into the ontology, demonstrating ontology's "deployment accelerator" value proposition.

**Architecture:** Mock WMS servers (Spring Boot, separate modules) expose API shapes mirroring real open.wangdian.cn / open.jushuitan.com. Adapters (in existing `ontology-demo` module) poll incrementally via `modified_after` timestamps and upsert into the ontology via existing `GenericRepository`. Existing graph / AI / rules / UI consume the unified ontology with zero changes.

**Tech Stack:** Java 21 / Spring Boot 3.4 / H2 (mock) / Existing ontology-demo infra / React for UI

**Design doc:** [`docs/plans/2026-04-17-wms-adapter-demo-design.md`](./2026-04-17-wms-adapter-demo-design.md)

---

## Phase 1 — Foundation

### Task 1: Create mock-wms Maven multi-module structure

**Files:**
- Create: `ontology-demo/mock-wms/pom.xml` (parent pom)
- Modify: `ontology-demo/pom.xml` (add `<modules>` declaration)

**Step 1: Create parent pom**

```xml
<!-- ontology-demo/mock-wms/pom.xml -->
<project xmlns="http://maven.apache.org/POM/4.0.0">
    <modelVersion>4.0.0</modelVersion>
    <parent>
        <groupId>com.warehouse</groupId>
        <artifactId>ontology-demo</artifactId>
        <version>0.0.1-SNAPSHOT</version>
    </parent>
    <artifactId>mock-wms</artifactId>
    <packaging>pom</packaging>
    <modules>
        <module>wangdiantong-mock</module>
        <module>jushuitan-mock</module>
    </modules>
</project>
```

**Step 2: Register as module in ontology-demo pom**

Edit `ontology-demo/pom.xml` — change packaging to `pom` is NOT needed; add:

```xml
<modules>
    <module>mock-wms</module>
</modules>
```

**Step 3: Verify structure**

Run: `cd ontology-demo && mvn -pl mock-wms validate`
Expected: BUILD SUCCESS (empty module set is OK at this stage)

**Step 4: Commit**

```bash
git add ontology-demo/pom.xml ontology-demo/mock-wms/pom.xml
git commit -m "feat(wms-demo): scaffold mock-wms multi-module parent"
```

---

### Task 2: Define WmsAdapter interface with unit test

**Files:**
- Create: `ontology-demo/src/main/java/com/warehouse/ontology/adapter/WmsAdapter.java`
- Create: `ontology-demo/src/main/java/com/warehouse/ontology/adapter/OntologyRecord.java`
- Create: `ontology-demo/src/test/java/com/warehouse/ontology/adapter/WmsAdapterContractTest.java`

**Step 1: Write the failing test**

```java
// WmsAdapterContractTest.java
package com.warehouse.ontology.adapter;

import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;

class WmsAdapterContractTest {
    @Test
    void adapter_exposes_name_and_incremental_pull_methods() {
        WmsAdapter stub = new WmsAdapter() {
            public String name() { return "stub"; }
            public List<OntologyRecord> pullWarehouses(Instant since) { return List.of(); }
            public List<OntologyRecord> pullLocations(Instant since) { return List.of(); }
            public List<OntologyRecord> pullSkus(Instant since) { return List.of(); }
            public List<OntologyRecord> pullInventory(Instant since) { return List.of(); }
        };
        assertThat(stub.name()).isEqualTo("stub");
        assertThat(stub.pullWarehouses(Instant.EPOCH)).isEmpty();
    }
}
```

**Step 2: Run to fail**

Run: `cd ontology-demo && mvn test -Dtest=WmsAdapterContractTest`
Expected: FAIL — `WmsAdapter` class not found

**Step 3: Implement interface + record**

```java
// OntologyRecord.java
package com.warehouse.ontology.adapter;

import java.util.Map;

public record OntologyRecord(String type, Map<String, Object> properties) {}
```

```java
// WmsAdapter.java
package com.warehouse.ontology.adapter;

import java.time.Instant;
import java.util.List;

public interface WmsAdapter {
    String name();
    List<OntologyRecord> pullWarehouses(Instant since);
    List<OntologyRecord> pullLocations(Instant since);
    List<OntologyRecord> pullSkus(Instant since);
    List<OntologyRecord> pullInventory(Instant since);
}
```

**Step 4: Run test — PASS**

Run: `mvn test -Dtest=WmsAdapterContractTest`
Expected: PASS

**Step 5: Commit**

```bash
git add ontology-demo/src/main/java/com/warehouse/ontology/adapter/ ontology-demo/src/test/java/com/warehouse/ontology/adapter/
git commit -m "feat(adapter): define WmsAdapter contract + OntologyRecord"
```

---

## Phase 2 — WangDianTong (WDT) Mock + Adapter

### Task 3: Scaffold WangDianTong Mock Spring Boot app

**Files:**
- Create: `ontology-demo/mock-wms/wangdiantong-mock/pom.xml`
- Create: `ontology-demo/mock-wms/wangdiantong-mock/src/main/java/com/warehouse/mock/wdt/WdtMockApplication.java`
- Create: `ontology-demo/mock-wms/wangdiantong-mock/src/main/resources/application.yml`

**Step 1: Create pom**

```xml
<project xmlns="http://maven.apache.org/POM/4.0.0">
    <modelVersion>4.0.0</modelVersion>
    <parent>
        <groupId>com.warehouse</groupId>
        <artifactId>mock-wms</artifactId>
        <version>0.0.1-SNAPSHOT</version>
    </parent>
    <artifactId>wangdiantong-mock</artifactId>

    <dependencies>
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-web</artifactId>
        </dependency>
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-data-jpa</artifactId>
        </dependency>
        <dependency>
            <groupId>com.h2database</groupId>
            <artifactId>h2</artifactId>
        </dependency>
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-test</artifactId>
            <scope>test</scope>
        </dependency>
    </dependencies>
</project>
```

**Step 2: Main class**

```java
// WdtMockApplication.java
package com.warehouse.mock.wdt;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
public class WdtMockApplication {
    public static void main(String[] args) {
        SpringApplication.run(WdtMockApplication.class, args);
    }
}
```

**Step 3: application.yml (port 9001)**

```yaml
server:
  port: 9001
spring:
  application:
    name: wangdiantong-mock
  datasource:
    url: jdbc:h2:mem:wdt;MODE=PostgreSQL
    driver-class-name: org.h2.Driver
  jpa:
    hibernate:
      ddl-auto: create-drop
```

**Step 4: Verify boots**

Run: `cd ontology-demo/mock-wms/wangdiantong-mock && mvn spring-boot:run &`
Expected: "Started WdtMockApplication" in logs, port 9001 listening
Then: `curl http://localhost:9001/actuator/health` → 404 OK (no endpoints yet)
Kill process afterward.

**Step 5: Commit**

```bash
git add ontology-demo/mock-wms/wangdiantong-mock/
git commit -m "feat(wdt-mock): scaffold Spring Boot app on port 9001"
```

---

### Task 4: WDT entities + seed data

**Files:**
- Create: `.../wdt/entity/WdtWarehouse.java`, `WdtLocation.java`, `WdtSku.java`, `WdtStock.java`
- Create: `.../wdt/repo/WdtWarehouseRepository.java` (+ 3 others)
- Create: `.../wangdiantong-mock/src/main/resources/data.sql` (seed)

**Step 1: Create JPA entities with WDT-native field names**

Reference real API fields: `warehouse_no`, `bin_code`, `goods_no`, `spec_no`.

```java
// WdtWarehouse.java
@Entity @Table(name = "wdt_warehouse")
public class WdtWarehouse {
    @Id @Column(name = "warehouse_no") private String warehouseNo;
    @Column(name = "warehouse_name") private String warehouseName;
    @Column(name = "is_main") private Boolean isMain;
    @Column(name = "modified_at") private Instant modifiedAt;
    // getters/setters
}

// WdtLocation.java
@Entity @Table(name = "wdt_location")
public class WdtLocation {
    @Id @Column(name = "bin_code") private String binCode;
    @Column(name = "warehouse_no") private String warehouseNo;
    @Column(name = "zone_name") private String zoneName;  // Chinese: 揀貨區/儲存區
    @Column(name = "status") private String status;
    @Column(name = "modified_at") private Instant modifiedAt;
}

// WdtSku.java
@Entity @Table(name = "wdt_sku")
public class WdtSku {
    @Id @Column(name = "spec_no") private String specNo;
    @Column(name = "goods_no") private String goodsNo;
    @Column(name = "goods_name") private String goodsName;
    @Column(name = "unit") private String unit;
    @Column(name = "modified_at") private Instant modifiedAt;
}

// WdtStock.java
@Entity @Table(name = "wdt_stock")
public class WdtStock {
    @Id @GeneratedValue private Long id;
    @Column(name = "spec_no") private String specNo;
    @Column(name = "bin_code") private String binCode;
    @Column(name = "stock_num") private Integer stockNum;
    @Column(name = "lock_num") private Integer lockNum;
    @Column(name = "modified_at") private Instant modifiedAt;
}
```

**Step 2: Spring Data repositories**

```java
public interface WdtWarehouseRepository extends JpaRepository<WdtWarehouse, String> {
    List<WdtWarehouse> findByModifiedAtAfter(Instant since);
}
// ... same for Location, Sku, Stock
```

**Step 3: Seed data — match real Shenzhen factory scenario**

```sql
-- data.sql
INSERT INTO wdt_warehouse VALUES ('WDT-SZ-001','深圳自营仓',true,CURRENT_TIMESTAMP);
INSERT INTO wdt_location VALUES ('A-01-01','WDT-SZ-001','揀貨區','ACTIVE',CURRENT_TIMESTAMP);
INSERT INTO wdt_location VALUES ('A-01-02','WDT-SZ-001','揀貨區','ACTIVE',CURRENT_TIMESTAMP);
INSERT INTO wdt_location VALUES ('B-02-01','WDT-SZ-001','儲存區','ACTIVE',CURRENT_TIMESTAMP);
INSERT INTO wdt_location VALUES ('C-01-01','WDT-SZ-001','收貨區','ACTIVE',CURRENT_TIMESTAMP);

INSERT INTO wdt_sku VALUES ('WDT-SPEC-001','WDT-G-001','USB-C線 1m','件',CURRENT_TIMESTAMP);
INSERT INTO wdt_sku VALUES ('WDT-SPEC-002','WDT-G-002','無線滑鼠','件',CURRENT_TIMESTAMP);
INSERT INTO wdt_sku VALUES ('WDT-SPEC-003','WDT-G-003','機械鍵盤','件',CURRENT_TIMESTAMP);

INSERT INTO wdt_stock (spec_no,bin_code,stock_num,lock_num,modified_at) VALUES ('WDT-SPEC-001','A-01-01',200,10,CURRENT_TIMESTAMP);
INSERT INTO wdt_stock (spec_no,bin_code,stock_num,lock_num,modified_at) VALUES ('WDT-SPEC-002','A-01-02',150,5,CURRENT_TIMESTAMP);
INSERT INTO wdt_stock (spec_no,bin_code,stock_num,lock_num,modified_at) VALUES ('WDT-SPEC-003','B-02-01',80,0,CURRENT_TIMESTAMP);
```

**Step 4: Boot + verify seed loaded**

Run: `mvn spring-boot:run &`
Then: `curl http://localhost:9001/h2-console` (or inspect logs for "3 rows inserted")
Kill.

**Step 5: Commit**

```bash
git add ontology-demo/mock-wms/wangdiantong-mock/
git commit -m "feat(wdt-mock): add entities + seed data (warehouse, location, sku, stock)"
```

---

### Task 5: Implement `warehouse_query.php` endpoint (TDD)

**Files:**
- Create: `.../wdt/api/WdtWarehouseController.java`
- Create: `.../wdt/api/WdtApiResponse.java` (shared wrapper)
- Create: `.../test/java/.../wdt/api/WdtWarehouseControllerTest.java`

**Step 1: Write failing MockMvc test**

```java
// WdtWarehouseControllerTest.java
@SpringBootTest @AutoConfigureMockMvc
class WdtWarehouseControllerTest {
    @Autowired MockMvc mvc;

    @Test
    void warehouse_query_returns_all_warehouses_in_wdt_format() throws Exception {
        mvc.perform(post("/openapi2/warehouse_query.php")
                .param("sid", "demo")
                .param("appkey", "demo-key"))
           .andExpect(status().isOk())
           .andExpect(jsonPath("$.code").value("0"))
           .andExpect(jsonPath("$.warehouses[0].warehouse_no").value("WDT-SZ-001"))
           .andExpect(jsonPath("$.warehouses[0].warehouse_name").value("深圳自营仓"));
    }
}
```

**Step 2: Run — FAIL** (controller doesn't exist)

**Step 3: Implement controller**

```java
// WdtApiResponse.java
public record WdtApiResponse<T>(String code, String message, T data) {
    public static <T> WdtApiResponse<T> ok(T data) {
        return new WdtApiResponse<>("0", "success", data);
    }
}

// WdtWarehouseController.java
@RestController
@RequestMapping("/openapi2")
public class WdtWarehouseController {
    private final WdtWarehouseRepository repo;
    public WdtWarehouseController(WdtWarehouseRepository repo) { this.repo = repo; }

    @PostMapping(path = "/warehouse_query.php",
                 consumes = MediaType.APPLICATION_FORM_URLENCODED_VALUE)
    public Map<String, Object> query(@RequestParam String sid,
                                      @RequestParam String appkey) {
        List<WdtWarehouse> all = repo.findAll();
        return Map.of("code", "0", "message", "success", "warehouses", all);
    }
}
```

**Step 4: Run — PASS**

**Step 5: Commit**

```bash
git commit -am "feat(wdt-mock): warehouse_query.php endpoint"
```

---

### Task 6: Implement `stock_query.php` with `modified_after` (TDD)

**Files:**
- Create: `.../wdt/api/WdtStockController.java`
- Create: `.../test/.../wdt/api/WdtStockControllerTest.java`

**Step 1: Failing test — verifies time filter works**

```java
@Test
void stock_query_respects_start_time_filter() throws Exception {
    Instant future = Instant.now().plus(Duration.ofDays(1));
    mvc.perform(post("/openapi2/stock_query.php")
            .param("sid", "demo").param("appkey", "demo-key")
            .param("start_time", future.toString()))
       .andExpect(jsonPath("$.stocks").isEmpty());
}

@Test
void stock_query_no_filter_returns_all() throws Exception {
    mvc.perform(post("/openapi2/stock_query.php")
            .param("sid", "demo").param("appkey", "demo-key"))
       .andExpect(jsonPath("$.stocks.length()").value(3));
}
```

**Step 2: Run — FAIL**

**Step 3: Implement**

```java
@PostMapping(path = "/stock_query.php",
             consumes = MediaType.APPLICATION_FORM_URLENCODED_VALUE)
public Map<String, Object> query(@RequestParam String sid,
                                   @RequestParam String appkey,
                                   @RequestParam(required = false) String start_time) {
    Instant since = start_time == null ? Instant.EPOCH : Instant.parse(start_time);
    List<WdtStock> stocks = repo.findByModifiedAtAfter(since);
    return Map.of("code", "0", "message", "success", "stocks", stocks);
}
```

**Step 4: Run — PASS**

**Step 5: Commit**

```bash
git commit -am "feat(wdt-mock): stock_query.php with incremental start_time filter"
```

---

### Task 7: WangDianTong HTTP client (TDD with `@RestClientTest`)

**Files:**
- Create: `ontology-demo/src/main/java/.../adapter/wangdiantong/WangdiantongClient.java`
- Create: `.../wangdiantong/WdtStockResponse.java` (DTO)
- Create: `.../wangdiantong/WdtWarehouseResponse.java` (DTO)
- Create: `.../test/.../adapter/wangdiantong/WangdiantongClientTest.java`

**Step 1: Failing test with `MockRestServiceServer`**

```java
@RestClientTest(WangdiantongClient.class)
class WangdiantongClientTest {
    @Autowired WangdiantongClient client;
    @Autowired MockRestServiceServer server;

    @Test
    void fetches_and_deserialises_stock() {
        server.expect(requestTo("http://localhost:9001/openapi2/stock_query.php"))
              .andExpect(method(HttpMethod.POST))
              .andRespond(withSuccess("""
                  {"code":"0","message":"ok","stocks":[
                    {"specNo":"WDT-SPEC-001","binCode":"A-01-01","stockNum":200,"lockNum":10,"modifiedAt":"2026-04-17T00:00:00Z"}
                  ]}
                  """, MediaType.APPLICATION_JSON));

        var stocks = client.queryStock(Instant.EPOCH);
        assertThat(stocks).hasSize(1);
        assertThat(stocks.get(0).binCode()).isEqualTo("A-01-01");
    }
}
```

**Step 2: Run — FAIL**

**Step 3: Implement client**

```java
// WdtStockDto.java
public record WdtStockDto(String specNo, String binCode, Integer stockNum,
                          Integer lockNum, Instant modifiedAt) {}

// WangdiantongClient.java
@Component
public class WangdiantongClient {
    private final RestClient http;
    public WangdiantongClient(RestClient.Builder builder,
                              @Value("${wms.wdt.base-url:http://localhost:9001}") String base) {
        this.http = builder.baseUrl(base).build();
    }

    public List<WdtStockDto> queryStock(Instant since) {
        Map<String, Object> form = new LinkedHashMap<>();
        form.put("sid", "demo");
        form.put("appkey", "demo-key");
        if (since != null && !Instant.EPOCH.equals(since)) {
            form.put("start_time", since.toString());
        }
        var body = http.post()
            .uri("/openapi2/stock_query.php")
            .contentType(MediaType.APPLICATION_FORM_URLENCODED)
            .body(toFormBody(form))
            .retrieve()
            .body(new ParameterizedTypeReference<Map<String, Object>>(){});
        // extract "stocks" -> List<WdtStockDto>, omitted for brevity
        return deserializeStocks(body);
    }
}
```

(Similarly add `queryWarehouses()`, `queryLocations()`, `querySkus()`.)

**Step 4: Run — PASS**

**Step 5: Commit**

```bash
git commit -am "feat(adapter): WangdiantongClient with incremental stock query"
```

---

### Task 8: WangDianTong Adapter field mapping (TDD)

**Files:**
- Create: `.../adapter/wangdiantong/WangdiantongAdapter.java`
- Create: `.../test/.../wangdiantong/WangdiantongAdapterTest.java`

**Step 1: Failing mapping test**

```java
class WangdiantongAdapterTest {
    WangdiantongClient client = mock(WangdiantongClient.class);
    WangdiantongAdapter adapter = new WangdiantongAdapter(client);

    @Test
    void maps_wdt_location_to_ontology_with_chinese_zone_normalized() {
        when(client.queryLocations(any())).thenReturn(List.of(
            new WdtLocationDto("A-01-01", "WDT-SZ-001", "揀貨區", "ACTIVE", Instant.now())
        ));
        List<OntologyRecord> records = adapter.pullLocations(Instant.EPOCH);
        assertThat(records).hasSize(1);
        Map<String,Object> p = records.get(0).properties();
        assertThat(p.get("code")).isEqualTo("A-01-01");
        assertThat(p.get("zone")).isEqualTo("PICK");  // 揀貨區 → PICK
        assertThat(p.get("floor")).isEqualTo(1);       // default
    }
}
```

**Step 2: Run — FAIL**

**Step 3: Implement adapter**

```java
@Component
public class WangdiantongAdapter implements WmsAdapter {
    private final WangdiantongClient client;

    private static final Map<String,String> ZONE_MAP = Map.of(
        "揀貨區", "PICK",
        "儲存區", "STORAGE",
        "收貨區", "RECEIVE",
        "發貨區", "DOCK"
    );

    public WangdiantongAdapter(WangdiantongClient client) { this.client = client; }

    public String name() { return "wangdiantong"; }

    public List<OntologyRecord> pullLocations(Instant since) {
        return client.queryLocations(since).stream()
            .map(wdt -> new OntologyRecord("Location", Map.of(
                "code",   wdt.binCode(),
                "zone",   ZONE_MAP.getOrDefault(wdt.zoneName(), "UNKNOWN"),
                "floor",  1,
                "status", wdt.status()
            )))
            .toList();
    }
    // + pullWarehouses, pullSkus, pullInventory
}
```

**Step 4: Run — PASS**

**Step 5: Commit**

```bash
git commit -am "feat(adapter): WangdiantongAdapter maps WDT fields to ontology"
```

---

## Phase 3 — Sync Infrastructure

### Task 9: WmsSyncScheduler with cursor tracking (TDD)

**Files:**
- Create: `.../adapter/sync/WmsSyncScheduler.java`
- Create: `.../adapter/sync/SyncCursorStore.java`
- Create: `.../test/.../adapter/sync/WmsSyncSchedulerTest.java`

**Step 1: Failing test — verifies cursor advances between syncs**

```java
@Test
void second_sync_uses_cursor_from_first() {
    WmsAdapter adapter = mock(WmsAdapter.class);
    when(adapter.name()).thenReturn("test");
    when(adapter.pullInventory(any())).thenReturn(List.of());
    
    GenericRepository repo = mock(GenericRepository.class);
    SyncCursorStore cursor = new InMemorySyncCursorStore();
    WmsSyncScheduler s = new WmsSyncScheduler(List.of(adapter), repo, cursor);

    s.runOnce();
    Instant first = cursor.getLastSync("test");
    Thread.sleep(10);
    s.runOnce();

    verify(adapter).pullInventory(Instant.EPOCH);       // first sync
    verify(adapter).pullInventory(first);               // second sync passes cursor
}
```

**Step 2: Run — FAIL**

**Step 3: Implement**

```java
// SyncCursorStore.java
public interface SyncCursorStore {
    Instant getLastSync(String adapterName);
    void setLastSync(String adapterName, Instant at);
}

// WmsSyncScheduler.java
@Component
public class WmsSyncScheduler {
    private final List<WmsAdapter> adapters;
    private final GenericRepository repo;
    private final SyncCursorStore cursor;

    public WmsSyncScheduler(List<WmsAdapter> adapters, GenericRepository repo,
                            SyncCursorStore cursor) {
        this.adapters = adapters;
        this.repo = repo;
        this.cursor = cursor;
    }

    @Scheduled(fixedRateString = "${wms.sync.interval-ms:30000}")
    public void runOnce() {
        for (WmsAdapter a : adapters) {
            Instant since = cursor.getLastSync(a.name());
            Instant syncStart = Instant.now();
            List.of(a.pullWarehouses(since), a.pullLocations(since),
                    a.pullSkus(since), a.pullInventory(since))
                .stream().flatMap(List::stream)
                .forEach(rec -> repo.upsert(rec.type(), rec.properties()));
            cursor.setLastSync(a.name(), syncStart);
        }
    }
}
```

**Step 4: Run — PASS**

**Step 5: Commit**

```bash
git commit -am "feat(adapter): WmsSyncScheduler with per-adapter cursor tracking"
```

---

### Task 10: Wire WDT adapter end-to-end (integration test)

**Files:**
- Modify: `.../ontology/OntologyDemoApplication.java` (add `@EnableScheduling`)
- Create: `.../test/.../adapter/wangdiantong/WangdiantongIntegrationTest.java`

**Step 1: Failing integration test**

Spins up WDT mock (via `@SpringBootTest(webEnvironment = RANDOM_PORT)`) plus ontology-demo, runs one sync, asserts ontology has records.

```java
@SpringBootTest
@TestPropertySource(properties = { "wms.wdt.base-url=http://localhost:${wdt.port}" })
class WangdiantongIntegrationTest {
    // start WDT mock in @BeforeAll on random port, set wdt.port system property
    @Autowired WmsSyncScheduler scheduler;
    @Autowired GenericRepository repo;

    @Test
    void sync_pulls_wdt_seed_into_ontology() {
        scheduler.runOnce();
        List<Map<String,Object>> locations = repo.findAll("Location");
        assertThat(locations).extracting(m -> m.get("code"))
            .contains("A-01-01", "A-01-02", "B-02-01");
    }
}
```

**Step 2: Run — FAIL (or PASS if Task 9 was complete)**

**Step 3: Fix any wiring issues — `@EnableScheduling` on main app class, ensure WangdiantongAdapter is `@Component`**

**Step 4: Run — PASS**

**Step 5: Commit**

```bash
git commit -am "test(adapter): WDT end-to-end integration; enable scheduling"
```

---

## Phase 4 — JuShuiTan (JST) Mock + Adapter

### Task 11: Scaffold JST Mock Spring Boot app (port 9002)

Mirror Task 3 but `artifactId=jushuitan-mock`, port 9002, package `com.warehouse.mock.jst`.

**Commit:** `feat(jst-mock): scaffold Spring Boot app on port 9002`

---

### Task 12: JST entities + seed data (JST-native field names)

Mirror Task 4 but with JST field names: `wmsCoId`, `slotId`, `skuId`, `qty`, `lockQty`, `modifiedTime`. Zone enum uses English: `"storage"`, `"pick"`, `"dock"`, `"receive"`.

Seed different warehouse to show multi-source: `JST-WH-001` (上海自营仓).

**Commit:** `feat(jst-mock): entities + seed (JST-native field names, English zones)`

---

### Task 13: JST `/open/wms/partner/query` endpoint (TDD)

JSON POST body, RESTful URL, different from WDT's PHP form-style.

```java
@PostMapping(path = "/open/wms/partner/query",
             consumes = MediaType.APPLICATION_JSON_VALUE)
public Map<String,Object> queryPartners(@RequestBody JstQueryRequest req) {
    return Map.of("code", 0, "data", Map.of("partners", warehouseRepo.findAll()));
}
```

**Test:** POST JSON body, expect JSON response with `data.partners[]`.

**Commit:** `feat(jst-mock): /open/wms/partner/query returns warehouses`

---

### Task 14: JST `/open/inventory/query` with pagination + modified range (TDD)

Supports: `page_index`, `page_size` (max 50), `modified_begin`, `modified_end`, `wms_co_id`.

**Test cases:**
- Page size caps at 50
- `modified_begin` filter works
- Returns `has_next` flag

```java
@PostMapping("/open/inventory/query")
public Map<String,Object> query(@RequestBody JstInventoryQueryReq req) {
    int size = Math.min(req.pageSize() != null ? req.pageSize() : 50, 50);
    PageRequest pg = PageRequest.of(req.pageIndex() == null ? 0 : req.pageIndex(), size);
    Instant since = req.modifiedBegin() == null ? Instant.EPOCH : Instant.parse(req.modifiedBegin());
    Page<JstInventory> page = inventoryRepo.findByModifiedTimeAfter(since, pg);
    return Map.of(
        "code", 0,
        "data", Map.of(
            "items", page.getContent(),
            "has_next", page.hasNext(),
            "page_index", page.getNumber()
        )
    );
}
```

**Commit:** `feat(jst-mock): /open/inventory/query with pagination + modified range`

---

### Task 15: JushuitanClient HTTP client (TDD)

JSON-based, handles pagination loop internally — returns all pages as single list.

```java
public List<JstInventoryDto> queryInventory(Instant since) {
    List<JstInventoryDto> all = new ArrayList<>();
    int page = 0;
    while (true) {
        var req = new JstInventoryQueryReq(page, 50, since.toString(), null, "JST-WH-001");
        var resp = http.post().uri("/open/inventory/query").body(req).retrieve()
                       .body(JstInventoryResponse.class);
        all.addAll(resp.data().items());
        if (!resp.data().hasNext()) break;
        page++;
    }
    return all;
}
```

**Commit:** `feat(adapter): JushuitanClient with auto-pagination`

---

### Task 16: JushuitanAdapter field mapping (TDD)

Mirror Task 8 pattern. Note: this task should be noticeably **smaller/faster** than Task 8 because the pattern is already established — that IS the demo's point. Adapter maps `slotId` → `code`, `area` (English) → zone enum.

```java
private static final Map<String,String> ZONE_MAP = Map.of(
    "storage","STORAGE", "pick","PICK", "dock","DOCK", "receive","RECEIVE"
);

public List<OntologyRecord> pullLocations(Instant since) {
    return client.queryLocations(since).stream()
        .map(jst -> new OntologyRecord("Location", Map.of(
            "code",   jst.slotId(),
            "zone",   ZONE_MAP.getOrDefault(jst.area(), "UNKNOWN"),
            "floor",  jst.floor(),
            "status", jst.enabled() ? "ACTIVE" : "DISABLED"
        ))).toList();
}
```

**Commit:** `feat(adapter): JushuitanAdapter — maps JST fields to ontology`

---

### Task 17: JST end-to-end integration test

Mirror Task 10. Verify after scheduler runs, ontology has records from **both** `WDT-SZ-001` and `JST-WH-001`.

**Commit:** `test(adapter): JST end-to-end; verify multi-source ontology`

---

## Phase 5 — Sources UI

### Task 18: `/api/sources` REST endpoint (TDD)

**Files:**
- Create: `.../api/SourcesController.java`
- Create: `.../test/.../api/SourcesControllerTest.java`

Returns per-adapter: name, last sync time, object counts (query `object_type_def` + generic count queries).

```java
@GetMapping("/api/sources")
public List<Map<String,Object>> sources() {
    return adapters.stream().map(a -> Map.of(
        "name", a.name(),
        "lastSyncAt", cursorStore.getLastSync(a.name()),
        "counts", Map.of(
            "Warehouse", repo.count("Warehouse"),
            "Location", repo.count("Location"),
            "SKU", repo.count("SKU"),
            "Inventory", repo.count("Inventory")
        )
    )).toList();
}
```

**Commit:** `feat(api): /api/sources endpoint for WMS sync status`

---

### Task 19: SourcesPage.jsx

**Files:**
- Create: `ontology-demo/frontend/src/pages/SourcesPage.jsx`
- Modify: `ontology-demo/frontend/src/App.jsx` (add route `/sources`)
- Modify: `ontology-demo/frontend/src/components/AppLayout.jsx` (add nav link)

Simple table: WMS name | last sync | counts | "Sync Now" button (POST `/api/sources/{name}/sync`).

Fetch every 5 seconds to show live sync updates. No fancy state management — `useState` + `useEffect` + `fetch`.

**Commit:** `feat(ui): SourcesPage shows WMS sync status + manual trigger`

---

## Phase 6 — Demo Polish

### Task 20: Demo run script + README

**Files:**
- Create: `ontology-demo/scripts/run-demo.sh`
- Modify: `ontology-demo/README.md`

Script boots (in order): WDT mock → JST mock → ontology backend → Python sidecar → Vite frontend. Uses `tmux` or background `&`.

README section "WMS Adapter Demo" with:
- Screenshot / diagram
- Run instructions
- Key files to read (design doc link, adapter code, mock code)
- The "deployment accelerator" narrative

**Commit:** `docs: WMS adapter demo run script + README`

---

### Task 21: End-to-end verification

**Manual checklist (run locally):**

1. `bash ontology-demo/scripts/run-demo.sh`
2. Wait 60s for first sync
3. Open `http://localhost:5173/sources` — expect 2 adapters, both with counts > 0
4. Open `/graph` — search `Location`, expect BOTH `A-01-01` (from WDT) and `SH_R2_S3`-like codes (from JST)
5. Open `/ai` — ask "有幾多個倉？" → AI should answer 2 without knowing the 2 come from different systems
6. Edit a record directly in WDT mock H2 (via `/h2-console`), wait 30s, verify it appears updated in `/graph`
7. Run all tests: `cd ontology-demo && mvn test` — all green

**Commit:** `test: e2e demo verification passes (multi-source WMS → ontology)`

---

## Execution Notes

- **Worktree:** Already in `.claude/worktrees/happy-cohen`. Continue committing here.
- **Branch:** `claude/happy-cohen` — existing branch, keep pushing to it.
- **Pushing to main:** Use `scripts/gh-push-tree.sh` in batches of 15 (see `docs/deployment.md`).
- **Run tests incrementally:** After each task, `mvn test -Dtest=<new test>` before moving on.
- **Don't skip tests:** This plan is TDD. If you feel the urge to skip a test, re-read the design doc.

## YAGNI Reminders (from design doc)

Do NOT add during implementation:
- Real HMAC signature verification
- OAuth / token refresh
- Write-back to WMS (read-only pull only)
- CDC / event sourcing
- Adapter hot-reload
- Schema auto-evolution

---

## Task Summary

| # | Task | Phase |
|---|------|-------|
| 1 | Mock-wms multi-module scaffold | Foundation |
| 2 | WmsAdapter contract | Foundation |
| 3 | WDT mock scaffold | WDT |
| 4 | WDT entities + seed | WDT |
| 5 | WDT `warehouse_query.php` | WDT |
| 6 | WDT `stock_query.php` + modified_after | WDT |
| 7 | WangdiantongClient | WDT |
| 8 | WangdiantongAdapter | WDT |
| 9 | WmsSyncScheduler | Sync |
| 10 | WDT e2e integration test | Sync |
| 11 | JST mock scaffold | JST |
| 12 | JST entities + seed | JST |
| 13 | JST `/open/wms/partner/query` | JST |
| 14 | JST `/open/inventory/query` + pagination | JST |
| 15 | JushuitanClient | JST |
| 16 | JushuitanAdapter | JST |
| 17 | JST e2e integration test | JST |
| 18 | `/api/sources` endpoint | UI |
| 19 | SourcesPage.jsx | UI |
| 20 | Demo script + README | Polish |
| 21 | E2E verification | Polish |

**21 tasks**, average ~30-60 min each → ~10-15 hours total work.
