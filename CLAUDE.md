# CLAUDE.md

## 語言及格式

- 所有回覆用**繁體中文**
- 所有價格用 **RMB (¥)**
- 永遠唔好估數字 — 必須用 WebSearch 搵真實來源，附連結
- Git 操作唔好上傳圖片檔案（.png / .jpg / .jpeg）

## 項目文檔結構

研究及設計文檔喺以下位置，有新資訊時更新對應文件：

| 文件 | 內容 |
|---|---|
| `ontology-sdk-notes.md` | Ontology SDK 概念、Java 類比、開源替代、四層價值 |
| `docs/market-research/wms-pricing-global.md` | 全球 WMS + 機器人定價（RMB） |
| `docs/market-research/china-competitors.md` | 中國競品四大分類 + 強弱分析 |
| `docs/market-research/competitor-financials.md` | 競品真實財報數據（極智嘉、海康、聚水潭）+ 來源連結 |
| `docs/architecture/ontology-adapter-pattern.md` | Ontology 四層價值模型 |
| `docs/architecture/integration-design.md` | 多 WMS 整合設計 + Java 代碼示例 |

## GitHub 推送

公司網絡封鎖 git push，用 GitHub REST API 上傳。本地有 `gh-push-tree.sh` helper script，用 `$GH_TOKEN` 環境變數。HTML 頁面正式放喺 `pages/`，對外 URL 亦會係 `/pages/*.html`。

## Memory Palace

At the end of each conversation, update the memory palace with any new conversation-derived insights (user preferences, decisions, feedback, non-obvious context, external references). Do not save things that can be learned by reading the repo.
