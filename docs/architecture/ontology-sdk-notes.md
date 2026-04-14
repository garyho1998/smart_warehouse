# Ontology SDK — Java 開發者筆記

## 咩係 Ontology Object？

一個強類型嘅域實體，背後連接數據源，帶有額外元數據令佢可以被查詢、連結、操作。

### Java 類比

**Object Type** 就好似 Java class / JPA `@Entity`。每個 **Object**（實例）就好似一行 DB row 映射到呢個 class。

```java
// 大致等同一個「Object Type」
public class Employee {
    private String employeeId;   // 主鍵
    private String name;         // 屬性
    private String role;         // 屬性
    private String facilityId;   // 連結（外鍵 → Facility object type）
}
```

### 核心概念對照 Java

| Ontology 概念 | Java 等價 | Ontology 額外提供 |
|---|---|---|
| **Object Type** | JPA `@Entity` | Schema 喺平台中央註冊，唔止喺 code 入面 |
| **Object**（實例） | 一個 entity / 一行 DB row | 可以用主鍵跨全平台尋址 |
| **Property** | 一個 field（`String name`） | 有類型、命名、索引、可搜索 |
| **Link** | `@ManyToOne` / `@OneToMany` | First-class 可導航關係（圖譜遍歷） |
| **Action** | 一個 service method | 註冊嘅操作，帶驗證 + 權限控制 |

### 底層數據結構

Ontology **唔係**一個單一數據結構。佢係一個**語義層**，坐喺現有存儲之上：

```
                    +-------------------------+
                    |     Ontology Layer       |
                    |  (schema + type system)  |
                    +-----------+-------------+
                                | 映射到
            +-------------------+--------------------+
            v                   v                    v
     PostgreSQL table   Parquet in S3      Kafka stream
     (employees)        (equipment_log)    (live_telemetry)
```

- **Schema** 定義 object types、properties、links 同 actions（好似 JPA metamodel 但全平台通用）
- **Backing store** 係數據實際存放嘅地方（關係型 table、文件存儲、streaming topic）
- **Sync pipeline** 將原始數據轉換成乾淨嘅 ontology objects（ETL 入 entity tables）

### SDK — 佢生成咩

從 ontology schema 自動生成嘅、類型安全嘅客戶端代碼。好似 JPA entities + repository interfaces 從中央 schema registry 自動生成：

```java
// 自動生成 — 永遠唔使手寫
public interface EmployeeRepository {
    Employee get(String primaryKey);
    List<Employee> search(Filter filter);
    Facility getFacility(Employee emp);
    List<Mission> getMissions(Employee emp);
}
```

改 schema → SDK 重新生成 → 編譯器捕捉所有 breaking callsite。

---

## 可以商業使用嗎？

Ontology SDK **唔係**開源庫。佢係**喺 Palantir Foundry 入面生成嘅** — 一個企業授權平台。

| 路徑 | 成本 | 你得到咩 |
|---|---|---|
| **Palantir Foundry 合約** | 企業定價（¥數百萬+/年） | 完整平台 + 自動生成嘅 Ontology SDK |
| **Foundry for Builders** | 較低級別 | 同一平台，唔同定價 |
| **自己建** | 工程時間 | 同樣概念，你自己實現 |

### Palantir 開源咗咩

- **osdk-ts** — Foundry 嘅 TypeScript 客戶端。開源，但需要 Foundry 後端先有用。
- **Conjure** — RPC / 代碼生成框架。底層水喉，唔係 Ontology 層。

**概念**（canonical model、semantic layer、knowledge graph）冇專利 — 只有 Palantir 嘅具體實現係專有嘅。

---

## 免費開源替代方案

冇單一項目可以複製完整嘅 Ontology SDK，但有幾個覆蓋關鍵部分。

### 最接近嘅完整替代

**TypeDB（前身 Grakn）** — 帶有內建類型系統嘅知識圖譜數據庫。最接近 Ontology 概念。

```typeql
define
  employee sub entity,
    owns name, owns role,
    plays assignment:assignee;
  facility sub entity,
    owns location,
    plays assignment:site;
  assignment sub relation,
    relates assignee, relates site;
```

### 用開源部件自建

| Ontology 功能 | 開源方案 | 備註 |
|---|---|---|
| Object Types + Properties | JPA `@Entity` | 標準 canonical model |
| Links / 圖譜遍歷 | Spring Data JPA，或 Apache TinkerPop / JanusGraph | JPA 做簡單關係，graph DB 做深度遍歷 |
| 自動生成 SDK / API | JHipster 或 Spring Data REST + OpenAPI Generator | Schema → API → 客戶端 SDK |
| 中央 schema registry | Apache Avro / JSON Schema | 中央定義類型，生成代碼 |
| Actions（mutations） | Spring service layer + Temporal | 持久化、可重試嘅 actions |
| 搜索 / 索引 | Elasticsearch / OpenSearch | 跨所有 object types 全文搜索 |
| 權限 | Spring Security + OPA (Open Policy Agent) | 行級 / 對象級訪問控制 |

### 最務實嘅路徑（Java 21 / Spring Boot 3 / PostgreSQL）

```
JPA Entities (object types)
  + Spring Data REST (自動生成 CRUD APIs)
  + OpenAPI Generator (自動生成客戶端 SDK)
  + Spring HATEOAS (對象間可導航連結)
```

~80% 嘅 Ontology SDK 價值，零授權成本。

---

## 四層價值模型

Adapter Pattern 只係入場券。Palantir 真正值錢嘅係 Ontology 上面嗰層。

| 層 | 做咩 | 值幾多錢 | 我哋而家有冇 |
|---|---|---|---|
| **Layer 1：數據整合** | Adapter + ETL | 唔值錢（人人識做） | 有（設計咗） |
| **Layer 2：Knowledge Graph** | 關係圖譜 + 跨域發現 | 值錢 | 部分（JPA relations） |
| **Layer 3：Actions + 權限** | 雙向操作 + 審計 + 審批流 | 好值錢 | 未做 |
| **Layer 4：AI on Ontology** | LLM 直接操作真實數據 | **最值錢** | 未做 |

詳見 [ontology-adapter-pattern.md](ontology-adapter-pattern.md)

---

## PLTR 點管理 Ontology 變更（from official docs）

### 三個 Building Block

Palantir ontology 只有 3 種定義，全部喺 Ontology Manager UI 入面配置（唔使寫 code）：

| 概念 | 做乜 | Java 類比 |
|------|------|-----------|
| **Object Type** | 現實世界嘅實體 schema | `@Entity` class |
| **Link Type** | 兩個 Object Type 之間嘅關係 | `@ManyToOne` 但自動雙向、帶語義名 |
| **Action Type** | 對 objects 嘅操作（帶驗證+權限+side effect） | `@Service` method + `@PreAuthorize` |

> Source: [Palantir: Core Concepts](https://www.palantir.com/docs/foundry/ontology/core-concepts)

### Git-style Branching 管理變更

Ontology 變更用 branch 測試，唔會直接改 Main：

```
Main（生產）
  ├── Branch "add-batch" → 加 Batch object type → 測試 → merge
  └── Branch "fix-amount" → 改 property type → 測試 → merge
```

> Source: [Palantir: Test changes in the ontology](https://www.palantir.com/docs/foundry/ontologies/test-changes-in-ontology)

### Shared vs Private Ontology

| 模式 | 做法 | 用途 |
|------|------|------|
| **Private** | 一個 ontology 屬於一個 organization | 獨立客戶 |
| **Shared** | 一個 ontology 跨多個 organization，schema 共用、data 隔離 | 供應鏈生態系統 |

Shared ontology 有版本控制，一個參與者嘅改進可以 benefit 所有人。

> Source: [Palantir: Shared Ontologies](https://www.palantir.com/docs/foundry/ontologies/shared-ontologies)

### Breaking Change 處理

Foundry 定義嘅 breaking changes：
- 改現有 property 嘅 data type（例如 Integer → Decimal）
- 改 object type 嘅 backing datasource
- 改 primary key

> "The Ontology Manager will block the user from saving changes until they define a migration."
> Source: [Palantir: Schema migrations](https://www.palantir.com/docs/foundry/object-edits/schema-migrations)

### Additive 安全，Structural 有 Conflict

| 變更 | Conflict? | 處理 |
|------|-----------|------|
| 加新 Object Type | ✓ 安全 | 直接加 |
| 加新 Link Type | ✓ 安全 | 直接加 |
| 加新 Property | ✓ 安全 | 其他客戶 = null |
| 改 Property type | ⚠️ Breaking | Migration required，Foundry block |
| 唔同客戶要唔同 enum values | ⚠️ Conflict | 合併 enum 或改 String（冇完美解） |
| 結構性分歧（有/冇 OrderLine） | ❌ Hard | FDE 人工判斷 |

### FDE 複用機制

> "When entity resolution appeared at one government agency, then at a pharmaceutical company... it got abstracted into a reusable primitive and pulled upstream into the platform."
> Source: [Everest Group](https://www.everestgrp.com/palantir-inside-the-category-of-one-forward-deployed-software-engineers-blog/)

FDE 用 reusable primitives 而唔係每個客戶從零寫。Pattern 反覆出現 → 抽象成 platform primitive → 所有 FDE 共用。

---

## 同 Warehouse Platform 嘅關係

項目嘅 canonical model（`Warehouse`、`Location`、`SKU`、`Pallet`、`Order`、`Task`）喺 `platform-core/` 入面本質上就係我哋嘅 ontology。從 Palantir 借鑒嘅核心思想：

1. **中央 schema** — 一個 canonical model，唔係每個系統各自嘅 model
2. **Adapters** — integration layer 將外部系統映射到呢個 model
3. **Links** — `Pallet` → `Location` → `Warehouse` 係可導航嘅關係
4. **Actions** — workflows（inbound、pick、transfer）係註冊喺呢啲 objects 上嘅操作

Warehouse platform 用 Spring Boot + JPA 實現同一個概念架構。
