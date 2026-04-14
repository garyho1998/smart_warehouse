# 可行性分析 — Solo Dev + Factory Partner 模式

> 前提：全職工作 + vibe coding side project，partner 係中國工廠老闆（唔寫 code），有員工做基礎機器人測試。

## 你有嘅

| 資源 | 有 | 備註 |
|------|---|------|
| Java/Spring 經驗 | ✓ | 核心技術棧 match |
| AI coding 工具 | ✓ | 對 CRUD/web app 有效，對 real-time 系統有限（見下方） |
| 真實客戶場景 | ✓ | Partner 嘅工廠 = Day 1 pilot site |
| 機器人測試人手 | ✓ | 唔使自己去現場調機器人 |
| 行業知識 (via partner) | ✓ | Factory owner 知道真正痛點 |

## 你冇嘅

| 缺口 | 影響 | 解決方案 |
|------|------|---------|
| 全職時間 | 每週只有 15-20 小時 | 接受 MVP 會慢 |
| Partner 唔寫 code | 佢唔能 review 技術決定 | 你自己做技術判斷 |
| 機器人硬件經驗 | 你冇碰過 AMR API | Partner 團隊做物理測試 |
| 倉儲行業經驗 | Domain modeling 依賴 partner 溝通 | 溝通成本高，容易有誤解 |
| Real-time systems 經驗 | Workflow engine + robot control 需要 | 呢個係最大技術風險 |

---

## 軟件難度 — 逐層誠實評估

### Layer 1: WMS Adapter — 中高難度

**唔係寫個 REST client 咁簡單。**

- Partner 工廠可能根本冇 WMS — 可能係 Excel / 微信群 / 手寫單
- 中國 WMS API 文檔質量參差，可能冇英文版，可能要直連數據庫
- 唔止 read — 任務完成後要 write back 更新庫存
- 認證機制每個 WMS 唔同（OAuth / API key / session token / 甚至微信掃碼）
- 要處理 API down / timeout / rate limit / 數據格式變更

| 之前評估 | 實際難度 |
|---------|---------|
| 低 | **中-高** — 對接你控制唔到嘅系統 |

### Layer 2: Ontology 數據模型 — 中等難度

**唔止係 JPA entities。**

- Domain modeling 需要深入倉儲行業知識 — 你冇，partner 有但佢唔寫 code
- 溝通成本高：partner 講「上架」，你理解嘅同佢理解嘅可能唔同
- Edge cases：同一個 SKU 唔同倉有唔同存儲規則、批次管理、效期管理
- Schema evolution：model 不斷變，migration 要管理

| 之前評估 | 實際難度 |
|---------|---------|
| 低 | **中** — 需要大量行業知識，但技術上可解 |

### Layer 3: Workflow Engine — 非常高難度 ⚠️

**唔係狀態機。係 concurrent job scheduler + resource allocator。呢個係整個項目最難嘅部分。**

- **並發控制**：10 個機器人同時揀 50 張單，邊個做邊張？
- **資源分配**：最近嘅機器人？電量最多嘅？載重合適嘅？ — optimization problem
- **死鎖預防**：兩個機器人去同一條通道，邊個讓路？
- **異常恢復**：機器人中途冇電 / 卡住 / 撞到嘢，半完成嘅任務點處理？
- **優先級**：急單插隊，已分配嘅任務要唔要重排？
- **一致性**：任務做到一半失敗，庫存數據點 rollback？
- Spring State Machine 搞唔掂呢個。實質係建一個 mini Kubernetes scheduler。

| 之前評估 | 實際難度 |
|---------|---------|
| 低（「狀態機」） | **非常高** — 呢個係整個項目嘅核心同最大風險 |

### Layer 4: Robot API — 非常高難度 ⚠️

**唔係 HTTP call。係 real-time, safety-critical 系統。**

- 中國機器人 API 可能係 proprietary protocol / TCP socket / ROS / gRPC — 唔一定係 REST
- 需要 persistent connection，唔係 request-response
- 要管理倉庫地圖：上傳 layout、定義路線、設定充電站
- 實時狀態 tracking：位置、任務進度、電量、速度
- 命令確認鏈：send → 收到? → 開始? → 完成?（每步可能失敗）
- Multi-robot 交通管理：防碰撞、排隊、讓路
- **安全**：send 錯指令 = 機器人撞人/撞貨 — safety-critical
- 文檔可能只有中文、要簽 NDA、要用 Windows SDK

| 之前評估 | 實際難度 |
|---------|---------|
| 低（「HTTP call」） | **非常高** — safety-critical real-time system |

### Layer 5: 之前完全冇提嘅嘢

| 組件 | 難度 | 問題 |
|------|------|------|
| **Robot Simulator** | 高 | 冇物理機器人嘅時候點測？寫 simulator 本身就係一個項目 |
| **部署** | 中 | 工廠可能冇穩定網絡，需要 on-premise |
| **監控** | 中 | 24/7 運行，半夜出事邊個處理？ |
| **網絡** | 中 | 工廠 WiFi 唔穩定，斷線重連邏輯 |
| **日誌/審計** | 中 | 出事要追蹤邊個指令去咗邊 |

---

## Vibe Coding 嘅真實限制

「AI coding 令 1 人 ≈ 3-5 人產出」— **只適用於 CRUD / web app**。

| 任務類型 | AI 幫助程度 | 原因 |
|---------|-----------|------|
| CRUD API | ★★★★★ | AI 嘅最強項 |
| Web UI | ★★★★☆ | 成熟模式，AI 做得好 |
| WMS Adapter | ★★★☆☆ | 每個 WMS 唔同，AI 冇見過中國 WMS API |
| Domain Modeling | ★★☆☆☆ | 需要行業知識，AI 冇 |
| Concurrent Scheduler | ★★☆☆☆ | AI 容易寫出 race condition |
| Robot API Integration | ★☆☆☆☆ | Proprietary protocol，AI 冇見過 |
| Debug 硬件問題 | ☆☆☆☆☆ | 機器人唔動、延遲高 → AI 幫唔到 |

---

## 倉庫現實 — 物理屬性決定 Workflow

同一個倉入面嘅貨物差異巨大：50g 手機殼到 500kg 棧板。每種貨需要唔同嘅 robot + 唔同嘅 workflow。

| 貨物 | 重量 | Robot | Workflow |
|------|------|-------|----------|
| 小件 | <5kg | AMR goods-to-person | AMR 搬架→人揀→AMR 搬回 |
| 中件 | 5-30kg | AMR transport | AMR 直接搬箱 |
| 大件 | >30kg | AGV 叉車 | 叉車搬 pallet |
| 易碎 | any | 人工揀+robot搬 | Robot 搬到人→人揀→robot 送走 |
| 冷鏈 | any | 冷庫 robot | 限時，唔能久離冷區 |
| 異形 | any | 人工 | 管狀、袋裝，robot 夾唔住 |

### Detection 問題
系統點知件貨幾重幾大？WMS 主數據經常唔準、入庫過磅需要硬件、Camera AI 需要 training data。

### Mixed-Order Splitting
一張單 3 件唔同類型貨 → 拆 3 個 sub-task → 3 種 robot/人處理 → 匯合到打包區 → 同步問題。

### 更多 Edge Cases
退貨（逆向 workflow）、批次 FIFO/FEFO、效期管理、Cross-docking、Wave picking、旺季 10x load、人機交接、QC 抽檢、盤點。

## 客戶配置 — 太複雜冇人用

Rule-based IF/THEN 配置問題：規則衝突、遺漏、膨脹、學習成本高。

AI-Driven 3 層方案：
- **Level 1 智能默認**：根據 SKU 屬性自動建議 workflow，覆蓋 80%
- **Level 2 自然語言**：「大件用叉車，小件用 AMR」→ AI 生成規則
- **Level 3 學習模式**：觀察人工操作 → 自動學習 routing

Micro-MVP：Phase 2 用 Level 0 (hardcoded)，Phase 3 做 Level 1。

---

## 難度總結（最終版）

| 組件 | 初始 | 第一次修正 | 最終 | 備註 |
|------|------|----------|------|------|
| WMS Adapter | 低 | 中-高 | **中-高** | 對接你控制唔到嘅系統 |
| Ontology | 低 | 中 | **中-高** | 加入物理屬性 routing |
| Workflow Engine | 低 | 非常高 | **非常高** | Concurrent scheduler |
| Robot API | 低 | 非常高 | **非常高** | Real-time, safety-critical |
| Item Routing | 冇提 | 冇提 | **高** | weight/size → robot 選擇 |
| Order Splitting | 冇提 | 冇提 | **高** | mixed order → multi-task → 匯合 |
| 客戶配置 | 冇提 | 冇提 | **中-高** | Rule editor 或 AI-driven |
| Edge Cases | 冇提 | 冇提 | **中** | 退貨、批次、效期、wave |
| 測試 | 冇提 | 高 | **高** | 需要 robot simulator |
| 部署/運維 | 冇提 | 中 | **中** | On-premise, 24/7 |
| 監控 | 冇提 | 冇提 | **中** | Dashboard + alerting |

---

## 修正後嘅結論

### 按 Phase 嘅可行性

| Phase | 內容 | Solo dev 可行? |
|-------|------|---------------|
| Phase 0 | 驗證機器人有冇 API | ✓ 唔使寫 code |
| Phase 1 | WMS adapter (read-only) | ✓ 可以做 |
| Phase 2 | 單一機器人 + 簡單任務 | △ 非常挑戰 |
| 完整產品 | Multi-robot orchestration | ✗ Solo dev 幾乎唔可能 |

### Micro-MVP：大幅縮小 scope 先有可能

| 原本 scope | Micro-MVP |
|-----------|-----------|
| Multi-WMS adapter | 只接 partner 嗰個系統 |
| Multi-robot orchestration | **只控制 1 台機器人** |
| Real-time workflow | **Sequential，一次一個任務** |
| 全流程（收貨→出庫） | **只做一個流程（例如搬運）** |
| Concurrent | **唔做，排隊執行** |
| Robot traffic management | **唔做（只有 1 台）** |

呢個 micro-MVP 係 solo dev 做得到嘅。但要誠實認識到：

1. **同 product-overview.html 嘅願景差距好大** — 願景係 multi-WMS + multi-robot + AI，micro-MVP 係 1 WMS + 1 robot + sequential
2. **Micro-MVP 唔能直接 demo 畀投資者** — 佢哋會問「點 scale？」
3. **但 micro-MVP 可以驗證最核心嘅假設** — 你嘅 adapter 能唔能真正接入 WMS 同控制機器人

---

## 分工要非常清晰

| 你（軟件） | Partner（業務 + 現場） |
|-----------|---------------------|
| 寫所有 code | 提供需求 + 業務流程 |
| 設計 API | 攞機器人 API 文檔 |
| 部署 server | 安排人做機器人物理測試 |
| 修 bug | 回報 bug + 錄影現場情況 |
| 寫 robot simulator | 提供真實機器人行為數據 |

如果 partner 唔能做好佢嗰邊，你會卡死喺「等回覆」。

---

## 建議路徑（修正版）

### Phase 0（而家）— Kill or Continue

- 確認 partner 工廠用咩品牌機器人
- 攞到機器人 API 文檔（或確認有 API）
- 確認 partner 現有 WMS / 管理方式
- **如果冇 API 或 partner 唔願意投入時間 → 停止，唔好浪費時間**

### Phase 1（Month 1-3）— 只做數據層

- 寫一個 adapter 讀 partner 工廠 WMS/系統數據
- 建 ontology 模型（同 partner 反覆確認）
- 出一個 read-only dashboard 顯示倉庫狀態
- **唔碰機器人**

### Phase 2（Month 4-6）— 1 台機器人 + 1 個任務

- 只控制 1 台機器人做 1 種任務（例如：A 點搬到 B 點）
- Sequential execution，冇並發
- Partner 團隊現場測試
- **呢個係真正嘅技術驗證點 — 如果做唔到，後面全部唔使諗**

### Phase 3（Month 7+）— 如果 Phase 2 work

- 加第 2 個任務類型
- 考慮要唔要搵第二個開發者
- 先做到穩定再考慮 scale

---

## 底線

**之前講「可行」太樂觀。修正：**

- Phase 1（數據層）= 可以做，信心高
- Phase 2（1 robot + 1 task）= 非常挑戰，信心中等，取決於 robot API 質量
- 完整產品 = solo dev 唔可能，需要團隊

最大風險唔係你嘅 coding 能力，係：
1. **機器人 API 質量**（你控制唔到）
2. **Partner 配合度**（佢有冇時間同意願持續投入）
3. **行業知識溝通**（你唔識倉儲，佢唔寫 code）
