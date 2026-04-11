# Ontology 四層價值分析

> Adapter Pattern 係 90 年代嘅 Java 概念，唔值錢。Palantir 值 $2,500 億唔係因為佢識做 adapter。真正值錢嘅係 Ontology 上面嗰層。

## 四層價值模型

| 層 | 做咩 | 值幾多錢 | 我哋而家有冇 |
|---|---|---|---|
| **Layer 1：數據整合** | Adapter + ETL | 唔值錢（人人識做） | 有（設計咗） |
| **Layer 2：Knowledge Graph** | 關係圖譜 + 跨域發現 | 值錢 | 部分（JPA relations） |
| **Layer 3：Actions + 權限** | 雙向操作 + 審計 + 審批流 | 好值錢 | 未做 |
| **Layer 4：AI on Ontology** | LLM 直接操作真實數據 | **最值錢** | 未做 |

---

## Layer 1：數據整合（入場券）

普通嘅 Adapter Pattern — 將外部系統嘅格式翻譯成統一格式。

```
外部系統 → Adapter → 統一模型 → 存入 DB → Dashboard 顯示
```

任何 Java 中級開發者都識做。唔係競爭優勢。

---

## Layer 2：Knowledge Graph

普通 DB 係 table + foreign key。Ontology 係**活嘅關係網**，可以跨域發現。

```
普通 DB：
  SELECT * FROM location WHERE warehouse_id = 'WH-A'
  → 返回一堆 row，完。

Knowledge Graph：
  由一個異常出發 →
    呢個 Pallet 喺邊個 Location？
      → 呢個 Location 屬於邊個 Zone？
        → 呢個 Zone 今日有邊啲 Task？
          → 呢啲 Task 係邊個機器人做？
            → 呢個機器人最近故障率係幾多？
              → 同一批機器人其他嘅有冇類似問題？
```

你可以沿住關係一路行落去，發現你一開始唔知道要問嘅嘢。SQL 做到但好痛苦（一大堆 JOIN），Ontology 入面呢啲關係係 first-class。

### Java 實現方向

JPA `@ManyToOne` / `@OneToMany` 做基本關係。如果需要深度圖譜遍歷，考慮 Apache TinkerPop / JanusGraph。

---

## Layer 3：Actions + 權限

普通系統係單向讀取。Ontology 係**雙向** — 你喺平台落指令，佢寫回去對應嘅系統。

```
普通做法：
  富勒 WMS ──讀──→ 你嘅平台 ──→ Dashboard 顯示（只能睇）

Ontology 做法：
  操作員喺 Dashboard 撳「移貨」
       │
       ▼
  Ontology 知道呢個 Location 屬於富勒
       │
       ▼
  自動寫回富勒 API 執行移貨
       │
       ▼
  同時通知海康機器人去搬
```

每個操作有**審批流、權限、審計、回滾**：

```java
// 普通做法
taskService.updateStatus(taskId, "COMPLETED");  // 任何人都可以 call

// Ontology 做法
@Action(
    requires = Permission.WAREHOUSE_OPERATOR,
    audit = true,
    approval = ApprovalFlow.AUTO
)
public void completeTask(Task task) {
    // 1. 檢查權限
    // 2. 記錄邊個喺幾點做咗呢個操作
    // 3. 觸發下游（通知 WMS、更新庫存、通知機器人）
    // 4. 如果出錯可以回滾
}
```

---

## Layer 4：AI on Ontology（最值錢）

呢個係 Palantir 而家最大嘅賣點（AIP 產品）。

```
普通 AI：
  用戶問「上海倉今日有咩異常？」
  → AI 讀 Dashboard 嘅 text → 生成總結
  → 只能講，唔能做

Ontology + AI（AIP）：
  用戶問「上海倉今日有咩異常？」
  → AI 直接 query Ontology objects
  → 發現 3 個 Task 超時 + 2 個機器人離線
  → AI 建議：「調配深圳倉 2 台機器人支援，需要你確認」
  → 用戶確認 → AI 直接觸發 Action → 機器人調配執行
```

AI 唔係讀 text，係讀**結構化、有權限、有關係嘅真實數據**，然後可以**觸發真實操作**。

---

## 我哋嘅路線圖

Palantir 值錢主要係 Layer 3 + 4。

- **MVP（而家）**：Layer 1（Adapter 整合） + Layer 2 基礎（JPA 關係）
- **Phase 2**：Layer 2 完整（圖譜遍歷）+ Layer 3（雙向操作 + 權限）
- **Phase 3**：Layer 4（AI 操作真實數據）

如果只做 Layer 1，就係普通整合工具。做到 Layer 2 + 3，就有真正嘅競爭力。Layer 4 係未來方向。
