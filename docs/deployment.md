# 部署指南

## GitHub Pages 部署流程

### 前置條件

1. **GitHub Personal Access Token (PAT)**
   - 位置：`~/.zshrc` 中嘅 `export GH_TOKEN=ghp_...`
   - 生成方法：GitHub → Settings → Developer settings → Personal access tokens → Tokens (classic) → Generate new token
   - 需要嘅權限：`repo`（完整 repo 權限）
   - Token 格式：`ghp_` 開頭

2. **Repo**：`garyho1998/smart_warehouse`（public）
3. **GitHub Pages**：已啟用，source = `main` branch `/` root

### 部署步驟

公司網絡封鎖 `git push`，所以用 GitHub API 推送檔案。

#### 1. 推送檔案

```bash
# 推送一個或多個檔案
bash scripts/gh-push-tree.sh -m "commit message" <file_path> [more_files...]

# 例子
bash scripts/gh-push-tree.sh -m "Update industry landscape" pages/industry-landscape.html
bash scripts/gh-push-tree.sh -m "Update product + explainer" pages/product-overview.html pages/pltr-explainer.html

# 刪除舊檔 / redirect 檔案
bash scripts/gh-push-tree.sh --delete industry-landscape.html --delete product-overview.html -m "Remove root redirects"
```

`scripts/gh-push-tree.sh` 做嘅事：
- 讀 remote `main` HEAD
- 為新檔 / 更新檔建立 blobs
- 用 Git Trees API 一次過提交多個新增、更新、刪除、搬位操作
- 最後更新 remote branch 指向新 commit

#### 1a. 推送大量檔案（>20 個）

公司網絡或 GitHub API 會 reject 過大 payload。超過 ~20 個檔案要分 batch 推送，每 batch 約 15 個。

**重要：shell 展開要用 bash array，唔好用 `$FILES`**

`bash script.sh -m "msg" $FILES` 喺某啲 shell（zsh、特定 IFS 設定）**唔會 word-split**，所有 filename 會被當成**一個 arg**，導致 "SKIP (not found)"。要用 `while read` 入 array：

```bash
# 取所有 diff 檔案
git diff --name-only origin/main...<branch> > /tmp/push-files.txt

# 分 batch（每 15 個一批）
split -l 15 /tmp/push-files.txt /tmp/push-batch-

# 逐個 batch 推送
for batch in /tmp/push-batch-*; do
  FILES=()
  while IFS= read -r line; do
    FILES+=("$line")
  done < "$batch"
  bash scripts/gh-push-tree.sh \
    -m "Batch push: ${#FILES[@]} files" \
    "${FILES[@]}"
done
```

**注意事項：**
- Script 必須喺**主 repo** 目錄跑（唔好喺 worktree 跑），因為佢 reads files relative to cwd
- 每 batch 會產生一個 commit，N 個檔案 = ceil(N/15) 個 commits
- 如果檔案喺 worktree 存在但主 repo 冇（例如 `.gitignore` 有 diff），script 會 "SKIP (not found)" 並跳過

#### 2. 等 GitHub Pages 部署（~1-2 分鐘）

```bash
# 查部署狀態
curl -s -H "Authorization: token $GH_TOKEN" \
  https://api.github.com/repos/garyho1998/smart_warehouse/pages \
  | grep '"status"'
# "status": "built" = 部署完成
```

#### 3. 驗證

```bash
# 檢查 HTTP status
curl -s -o /dev/null -w "%{http_code}" \
  https://garyho1998.github.io/smart_warehouse/pages/industry-landscape.html
# 200 = OK
```

### 已部署頁面

| 頁面 | URL |
|------|-----|
| 行業象限圖 | https://garyho1998.github.io/smart_warehouse/pages/industry-landscape.html |
| 產品定位 | https://garyho1998.github.io/smart_warehouse/pages/product-overview.html |
| 系統架構 | https://garyho1998.github.io/smart_warehouse/pages/system-backbone.html |
| Palantir 解說 | https://garyho1998.github.io/smart_warehouse/pages/pltr-explainer.html |
| 數據流 | https://garyho1998.github.io/smart_warehouse/pages/pltr-data-flow.html |

> 注意：如果 root redirect 檔已刪除，舊網址例如 `/industry-landscape.html` 會唔再存在，請直接分享 `/pages/industry-landscape.html`。

### 首次設定 GitHub Pages

如果 Pages 未啟用：

```bash
# 1. Repo 必須係 public（免費版唔支援 private Pages）
curl -s -X PATCH \
  -H "Authorization: token $GH_TOKEN" \
  https://api.github.com/repos/garyho1998/smart_warehouse \
  -d '{"private":false}'

# 2. 啟用 Pages
curl -s -X POST \
  -H "Authorization: token $GH_TOKEN" \
  https://api.github.com/repos/garyho1998/smart_warehouse/pages \
  -d '{"build_type":"legacy","source":{"branch":"main","path":"/"}}'
```

### Claude Code session 搵唔到 GH_TOKEN

Claude Code 啟動時會 load shell profile，但如果 token 係後來加入 `~/.zshrc`，當前 session 可能冇 load 到。

`! source ~/.zshrc` **唔 work** — `!` 命令跑喺 subshell，env var 唔會傳返去。

**解決方法：**喺 `scripts/gh-push-tree.sh` 命令前面 inline source：

```bash
source ~/.zshrc && bash scripts/gh-push-tree.sh -m "Update industry landscape" pages/industry-landscape.html
```

或者直接開新 Claude Code session（會自動 load `~/.zshrc`）。

### Token 過期處理

如果推送失敗（401/403），token 可能已過期：

1. 去 https://github.com/settings/tokens
2. Generate new token (classic)
3. 勾選 `repo` 權限
4. 更新 `~/.zshrc`：
   ```bash
   export GH_TOKEN=ghp_新token
   ```
5. 開新 Claude Code session（或 `source ~/.zshrc && bash scripts/gh-push-tree.sh ...`）
