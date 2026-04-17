# WMS Adapter Demo — Design Doc

**Date:** 2026-04-17
**Branch:** `claude/happy-cohen`
**Status:** Design approved, ready for implementation plan

---

## Context

Ontology Explorer MVP 已完成核心功能（DB meta-tables、Generic graph traversal、Rules、Functions、AI Chat）。下一步要證明產品嘅核心 value proposition：

> **Ontology = 部署加速器** — 新客戶用咩 WMS 都好，寫一個 Adapter 就接入，Dashboard / AI / Graph / Workflow 全部即刻 work。

呢個 demo 會透過 **2 個真實中國 WMS（旺店通 + 聚水潭）嘅 Adapter**，具體展示：

1. 同一個 ontology 對接 2 個 API 結構完全唔同嘅 WMS
2. 加第 2 個 adapter 嘅速度明顯快過第 1 個（pattern 已建立）
3. 加完 adapter 之後，existing Graph / AI Chat / Rules **零改動** 都即刻 work

**Real pain point addressed（來自 memory）:** 真正痛點係客戶採用速度慢 — 呢個 demo 就係要答「我哋嘅產品點解令部署快」。

---

## Architecture

### 新增 3 個模組

```
ontology-demo/
├── mock-wms/                          ← 新增：模擬 2 個 WMS
│   ├── wangdiantong-mock/             Spring Boot (port 9001)
│   └── jushuitan-mock/                Spring Boot (port 9002)
│
├── src/main/java/.../adapter/         ← 新增：Adapter 層
│   ├── WmsAdapter.java                Contract
│   ├── WmsSyncScheduler.java          定時 pull
│   ├── wangdiantong/
│   │   ├── WangdiantongClient.java    HTTP client
│   │   └── WangdiantongAdapter.java   Field mapping
│   └── jushuitan/
│       ├── JushuitanClient.java       HTTP client
│       └── JushuitanAdapter.java      Field mapping
│
└── frontend/src/pages/
    └── SourcesPage.jsx                ← 新增：WMS 源管理 UI
```

### Data Flow

```
┌────────────────┐  HTTP   ┌───────────────┐
│ 旺店通 Mock    │ pull    │ WangDian      │
│ (9001, PHP API)│────────→│ Adapter       │────┐
└────────────────┘         └───────────────┘    │
                                                 ↓
┌────────────────┐  HTTP   ┌───────────────┐   Ontology
│ 聚水潭 Mock    │ pull    │ JuShuiTan     │───→ (existing
│ (9002, REST)   │────────→│ Adapter       │    meta + data)
└────────────────┘         └───────────────┘       │
                                                   ↓
                        ┌──────────────────────────┴─────────┐
                        │ 零改動即刻 work：                    │
                        │ Graph / AI Chat / Rules / UI       │
                        └────────────────────────────────────┘
```

---

## Real API Structure（重要差異）

### 旺店通 API

- **URL style:** PHP 風格 `.php` endpoint（legacy design）
- **Base URL:** `http://api.wangdian.cn/openapi2/`
- **Auth:** `sid` + `appkey` + `appsecret`（signature required）
- **Format:** Form-encoded params，返回 JSON
- **Key endpoints:**
  - `warehouse_query.php` — 查倉庫
  - `stock_query.php` — 增量庫存查詢（有 `start_time`, `end_time`）
  - `vip_stock_query_all.php` — 全量庫存查詢
  - `goods_push.php` — 貨品推送
- **Field naming:** `warehouse_no`, `bin_code`, `goods_no`, `spec_no`
- **Zone terms:** 中文（「揀貨區」「儲存區」）

### 聚水潭 API

- **URL style:** RESTful JSON paths
- **Base URL:** `https://openapi.jushuitan.com` (prod) / `https://dev-api.jushuitan.com` (dev)
- **Auth:** `appKey` + `appSecret` + `accessToken`（HMAC-SHA256 signature）
- **Format:** JSON body，返回 JSON
- **Key endpoints:**
  - `/open/wms/partner/query` — 倉庫查詢
  - `/open/inventory/query` — 庫存查詢
- **Parameters:**
  - `wms_co_id` — 倉庫編號
  - `page_index`, `page_size` (max 50)
  - `modified_begin`, `modified_end`
  - `has_lock_qty`
- **Rate limit:** 5 req/s, 100 req/min
- **Field naming:** `wms_co_id`, `slot_id`, `sku_id`, `qty`
- **Zone terms:** 英文 enum（"storage", "pick", "dock"）

### 點解呢兩個 WMS 好適合 demo

| Aspect | 旺店通 | 聚水潭 | Demo value |
|--------|--------|--------|------------|
| URL 風格 | PHP `.php` | REST `/path` | 展示 adapter 抽象唔同 transport |
| Data format | Form-encoded | JSON | 展示格式轉換 |
| Field names | `bin_code`, `goods_no` | `slot_id`, `sku_id` | 展示命名映射 |
| Zone value | 中文 | 英文 enum | 展示 value normalization |
| Rate limit | 寬鬆 | 5/s, 100/min | 展示 rate limiting handling |
| Pagination | 簡單 | page_index + size | 展示 pagination 抽象 |

---

## Sync Strategy：Incremental Pull

**決定：** 用 `modified_after` timestamp-based incremental pull，每 30 秒 poll 一次。

**Flow:**

```java
// Pseudocode
private Instant lastSyncAt = Instant.EPOCH;  // 第一次係 full sync

@Scheduled(fixedRate = 30_000)
public void sync() {
    List<WmsRecord> changed = wmsClient.queryChanged(since = lastSyncAt);
    for (WmsRecord r : changed) {
        OntologyObject obj = adapter.toOntology(r);
        ontology.upsert(obj.type(), obj.properties());
    }
    lastSyncAt = now();
}
```

**Mock server 要支援 `modified_after`** — 呢個係重點，先做到真實 delta sync。

**點解唔用 webhook：**
- 旺店通、聚水潭 都冇公開 webhook support — 唔真實
- Pull 更符合中國 WMS 現實
- Demo 視覺上易睇「data 流入 ontology」

---

## Adapter Interface

```java
public interface WmsAdapter {
    String name();  // "wangdiantong" / "jushuitan"
    
    // 每個 adapter 實作：將 WMS 原始 response 轉成 ontology 物件
    List<OntologyObject> pullWarehouses(Instant since);
    List<OntologyObject> pullLocations(Instant since);
    List<OntologyObject> pullSkus(Instant since);
    List<OntologyObject> pullInventory(Instant since);
}
```

**Adapter 做嘅事（只有 field mapping 同 value normalization）：**

```java
// WangdiantongAdapter
public OntologyObject toLocation(WdtLocation wdt) {
    return new OntologyObject("Location", Map.of(
        "code",   wdt.getBinCode(),         // "A-01-03"
        "zone",   mapZone(wdt.getZoneName()),  // "揀貨區" → "PICK"
        "floor",  1,                         // 旺店通冇 floor，default
        "status", wdt.getStatus()
    ));
}

// JushuitanAdapter  
public OntologyObject toLocation(JstLocation jst) {
    return new OntologyObject("Location", Map.of(
        "code",   jst.getSlotId(),          // "SH_R2_S3"
        "zone",   mapZone(jst.getArea()),   // "storage" → "STORAGE"
        "floor",  jst.getFloor(),           // 2
        "status", jst.isEnabled() ? "ACTIVE" : "DISABLED"
    ));
}
```

---

## Mock WMS Design

### Key Principle

Mock 要 **盡量貼近真實 API**：同樣 endpoint 名、同樣 field names、同樣 auth 機制（可以簡化）、同樣 pagination + modified_after 支援。

### Seed Data

兩個 mock 各有一套 sample data（模擬同一個現實倉庫嘅兩種視角）：
- 1 個倉庫（但唔同 ID format）
- 10 個 location（唔同 naming convention）
- 20 個 SKU
- 50 條 inventory

### Simplifications（demo 為目的）

- ❌ 唔做真實 HMAC signature — 簡單 API key header
- ❌ 唔做 OAuth / token refresh
- ❌ 唔做 rate limit（但 adapter 要有 retry 邏輯 demo）
- ✅ 做 `modified_after` filter
- ✅ 做 pagination
- ✅ 做真實 field names / value formats

---

## Demo Scenarios

1. **啟動順序：** 先起 ontology + AI sidecar → 再起 mock WMS → 最後起 adapter sync
2. **第一次 sync：** Full pull，ontology 從空到有數據
3. **Incremental demo：** 喺 mock WMS UI 改一條 SKU 庫存，30 秒內 ontology 自動更新
4. **Multi-source demo：** 兩個 WMS 嘅 data 同時流入，喺 Graph UI 睇到同一個 ontology 中有兩邊嘅 Location / SKU
5. **AI query：** 問「邊個倉有低庫存？」AI 查 ontology 返回結果 — **AI 唔知底層有 2 個 WMS**
6. **Speed comparison：** 展示 `git log --follow adapter/` — 第 1 個 adapter commit 數 vs 第 2 個 adapter commit 數（第 2 個明顯少）

---

## UI Changes（最小）

加一個 `/sources` 頁：

- 列出已連接嘅 WMS（旺店通 / 聚水潭）
- 每個顯示：
  - 最後 sync 時間
  - Sync 狀態（running / error）
  - 已 sync 數量（locations, SKUs, inventory）
- 「Trigger Sync Now」按鈕
- 基本 error log

**唔做：** 連線設定 UI（用 `application.yml` config 就得 — YAGNI）。

---

## What NOT to Do (YAGNI)

- ❌ 唔做真實 API 簽名演算法（mock 用簡單 header）
- ❌ 唔做寫回 WMS（只做 pull read-only，唔做 Layer 3 write-back）
- ❌ 唔做 CDC / event sourcing（poll 就夠）
- ❌ 唔做多 instance / 分散式 sync（一個 JVM 搞掂）
- ❌ 唔做 adapter hot-reload（restart app 就得）
- ❌ 唔做 schema 自動演化（ontology schema 已經 fixed）

---

## Success Criteria

- ✅ 兩個 mock WMS 可以獨立啟動，符合真實 API 結構
- ✅ 兩個 adapter 都 pass incremental pull test
- ✅ 啟動 demo 後 30 秒內，ontology 有從兩個 WMS 同步過嚟嘅數據
- ✅ AI Chat 能查到從任一 WMS 同步嘅物件（唔需要提 WMS 名）
- ✅ Existing graph traversal、rules、functions 全部 work，零改動
- ✅ 第 2 個 adapter 嘅 LoC 明顯少過第 1 個（抽象成功）

---

## Files to Modify / Create

**Modify:**
- `ontology-demo/pom.xml` — add mock WMS module refs

**Create:**
- `ontology-demo/mock-wms/wangdiantong-mock/` — full Spring Boot app
- `ontology-demo/mock-wms/jushuitan-mock/` — full Spring Boot app
- `ontology-demo/src/main/java/.../adapter/` — adapter package
- `ontology-demo/frontend/src/pages/SourcesPage.jsx` — UI
- `ontology-demo/src/test/java/.../adapter/` — adapter tests

---

## Next Step

Invoke `writing-plans` skill 寫 detailed bite-sized implementation plan。
