# PLTR Ontology 內部架構 — 讀寫分離 + Index Pipeline

> 基於 Palantir 官方文檔。Query-time merge 機制係基於通用 CS pattern 嘅推斷，唔係 PLTR 官方公開嘅實現細節。

## 架構總覽

PLTR 嘅 Ontology 有 3 層分離：Schema（OMS）、Raw Data（Backing Dataset）、Index（Object Database）。

```
┌─────────────────────────────────────────────────────────────┐
│  Ontology Metadata Service (OMS)                            │
│  存 schema 定義：Object Types / Link Types / Action Types    │
│  用戶喺 Ontology Manager UI 配置，唔使寫 code                 │
└─────────────────────────────────────────────────────────────┘

┌─────────────────────────────────────────────────────────────┐
│  Backing Dataset（原始數據）                                  │
│  Parquet / CSV / Kafka Stream                               │
│  原始數據永遠住呢度，唔會被 user edit 改動                      │
└──────────────────────┬──────────────────────────────────────┘
                       │
                       │ Funnel Pipeline（4 步）
                       │
          ┌────────────┴────────────┐
          │ ① Changelog Job         │  比較前後 dataset，只記 diff
          │ ② Merge Job             │  合併 diff + user edits（by PK）
          │ ③ Indexing Job          │  轉成 search-optimized index files
          │ ④ Hydration             │  下載 index 到 search nodes
          └────────────┬────────────┘
                       │
                       v
┌─────────────────────────────────────────────────────────────┐
│  Object Database (OSv2)                                     │
│  Inverted index on search nodes                             │
│  所有 query 打呢度，唔係打 Backing Dataset                     │
│  只 index 標記為 Searchable 嘅 properties                    │
└─────────────────────────────────────────────────────────────┘
```

## 讀取流程

App query → Object Database（index） → 返回結果。唔接觸 Backing Dataset。

```
App: GET /objects/Task?status=FAILED

Object Database 查 status index:
  FAILED → [TSK-005]
  → 毫秒級回應，唔使 scan 全部 rows
```

### Searchable Render Hint

唔係每個 property 都會被 index。每個 property 有 `Searchable` 設定：
- ON → 建 inverted index，可以 search / filter
- OFF → 唔建 index，慳 reindexing 時間

> Source: [Property Metadata Reference](https://www.palantir.com/docs/foundry/object-link-types/property-metadata)

## 寫入流程（重點）

User edit **唔會寫回 Backing Dataset**。走獨立路徑：

```
用戶觸發 Action（例如 completeTask）
       │
       v
 Actions Service（驗證權限 + preconditions）
       │
       v
 Funnel Queue（有序隊列，offset tracking）
       │
       ├──→ 立即：offset 套用到 Object Database 嘅 live index
       │    → 下一個 query 已經睇到改動（唔使等 reindex）
       │
       └──→ 定期：Funnel build merged dataset
            → 原始 dataset + queue edits 合併（by PK）
            → 持久化到 Foundry dataset
            → Queue 清空
```

> Source: [How User Edits Are Applied](https://www.palantir.com/docs/foundry/object-edits/how-edits-applied)

### Query-Time Merge 機制（推斷）

**注意：以下係基於通用 CS pattern 嘅推斷。**

Offset 係一個順序號碼。Base Index 記住自己 index 到邊個 offset。Query 時套用後續 edits：

```
Base Index（indexed at offset 0）:
  TSK-005.status = "IN_PROGRESS"

Edit Queue:
  offset 1: TSK-005.status = "FAILED"
  offset 2: TSK-005.status = "RETRY", robotId = "R-HIK-01"

Query "status=IN_PROGRESS":
  Step 1: Base Index → [TSK-002, TSK-005]
  Step 2: Apply offset 1 → TSK-005 改成 FAILED（移除）
          Apply offset 2 → TSK-005 改成 RETRY（仍然唔係 IN_PROGRESS）
  Result: [TSK-002]

Reindex 後（Funnel build merged dataset）:
  Base Index 更新到 offset 2
  Queue 清空
  → 之後 query 唔使 merge，直接讀 index
```

類比：
- Base Index = git last commit
- Edit Queue = uncommitted changes
- Offset = change 順序
- Query = commit + apply uncommitted changes
- Reindex = git commit（changes baked in）

### Reindex 觸發條件

- Backing Dataset 有新 data transaction 時
- 或者每 6 小時（如果有 edits）

> Source: [How User Edits Are Applied](https://www.palantir.com/docs/foundry/object-edits/how-edits-applied)

## 點解要咁設計

| 問題 | PLTR 嘅解法 |
|---|---|
| Backing Dataset 可能係 read-only（唔係你嘅 DB） | User edits 係獨立一層，唔改原始數據 |
| 一個 Object Type 可能接多個 data source | Funnel merge by PK |
| 10 億 rows 每次全部 reindex 太慢 | Incremental：只 index diff + queue edits |
| User edit 要立即可見 | Queue offset 套用到 live index |
| Index 要 search-optimized | Inverted index on search nodes（唔係 row store） |

## 6 個 Microservices

| Service | 職責 |
|---|---|
| **Ontology Metadata Service (OMS)** | Schema 定義（object / link / action types） |
| **Object Data Funnel** | ETL pipeline：Changelog → Merge → Index → Hydrate |
| **Object Databases (OSv2)** | Indexed data storage + query engine |
| **Object Set Service (OSS)** | 讀取 API：search / filter / aggregate |
| **Actions Service** | 寫入 API：structured edits + 權限 + validation |
| **Functions on Objects** | 自定義業務邏輯 |

> Source: [Ontology Backend Overview](https://www.palantir.com/docs/foundry/object-backend/overview)

---

## 我哋嘅 MVP 實現策略

我哋嘅場景：一間工廠、萬級數據、own 個 database。唔需要 PLTR 嘅完整架構。

### 簡化原則

| PLTR 需要 | 我哋唔需要（MVP） | 原因 |
|---|---|---|
| Backing Dataset 同 Index 分離 | ❌ | 我哋 own 個 DB，可以直接 query |
| Funnel incremental indexing | ❌ | 萬級數據，PG B-tree index 夠用 |
| Edit Queue + offset | ❌ | 直接 UPDATE PG table，transaction commit 即可見 |
| Multi-source merge | ❌ | MVP 只有 1 個 WMS source |
| Inverted index on search nodes | ❌ | PG index 夠用 |

### 保留嘅核心概念

| PLTR 概念 | 我哋嘅實現 |
|---|---|
| Schema 係 data，唔係 code | `ontology.yml` config file |
| 通用引擎 | `GenericRepository` + `SchemaManager` |
| Graph traversal via Link Types | `GraphService` 讀 `linkTypes` 定義 |
| Structured Actions | `ActionExecutor` 讀 `actionTypes` 定義 |
| Audit trail | `audit_trail` table |

### 讀寫流程（MVP）

```
寫入：
  User Action → ActionExecutor → UPDATE PG table → Transaction commit → 完成
  （直接寫，直接可見。冇 queue，冇 offset。）

讀取：
  Query → GenericRepository → SELECT from PG table（with B-tree index）→ 返回
  （直接讀，冇 index 層。PG 自帶 index 夠用。）

Graph Traversal：
  Query → GraphService → 讀 linkTypes 定義 → SQL JOIN → 返回 traversal path
```

### 升級路線

```
Phase 0-2（MVP）:
  PG table = 唯一 data store
  B-tree index = 唯一 search index
  直接寫直接讀

Phase 3（第 2 客戶，十萬級）:
  加 Materialized View 做 pre-join（輕量 index）
  REFRESH MATERIALIZED VIEW CONCURRENTLY = 簡化版 Funnel reindex

Phase 4+（多客戶，百萬級）:
  加 Elasticsearch 做 search layer
  加 Debezium CDC 做 change capture（= Funnel）
  PG = backing store，ES = query index
  接近 PLTR 架構
```

---

## Sources

- [Ontology Backend Overview](https://www.palantir.com/docs/foundry/object-backend/overview)
- [How User Edits Are Applied](https://www.palantir.com/docs/foundry/object-edits/how-edits-applied)
- [Object Edits Overview](https://www.palantir.com/docs/foundry/object-edits/overview)
- [Materializations](https://www.palantir.com/docs/foundry/object-edits/materializations)
- [Funnel Batch Pipelines](https://www.palantir.com/docs/foundry/object-indexing/funnel-batch-pipelines)
- [Indexing Overview](https://www.palantir.com/docs/foundry/object-indexing/overview)
- [Property Metadata Reference](https://www.palantir.com/docs/foundry/object-link-types/property-metadata)
- [Ontology Manager Overview](https://www.palantir.com/docs/foundry/ontology-manager/overview)
- [Create an Object Type](https://www.palantir.com/docs/foundry/object-link-types/create-object-type)
