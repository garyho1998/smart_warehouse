#!/bin/bash
# gh-push-tree.sh — Push multiple files to GitHub in 1 commit via Git Trees API
# Preserves directory structure. Builds on remote HEAD (not local).
#
# Usage:
#   ./gh-push-tree.sh -m "commit message" file1 file2 dir/file3 ...
#   ./gh-push-tree.sh file1 file2              (default message)

set -e

REPO="garyho1998/smart_warehouse"
BRANCH="main"
TOKEN="${GH_TOKEN:?Set GH_TOKEN env var first}"
API="https://api.github.com/repos/$REPO"

# --- Parse args ---
MSG=""
FILES=()
while [[ $# -gt 0 ]]; do
  case "$1" in
    -m) MSG="$2"; shift 2 ;;
    *)  FILES+=("$1"); shift ;;
  esac
done

if [ ${#FILES[@]} -eq 0 ]; then
  echo "Usage: ./gh-push-tree.sh -m \"message\" file1 [file2 ...]"
  exit 1
fi

MSG="${MSG:-Update ${FILES[*]}}"

api_get() {
  curl -sf -H "Authorization: token $TOKEN" "$API$1"
}

api_post() {
  curl -sf -X POST -H "Authorization: token $TOKEN" \
    -H "Content-Type: application/json" "$API$1" -d @-
}

jq_py() {
  python3 -c "import sys,json; print(json.load(sys.stdin)$1)"
}

# --- Step 1: Get remote HEAD ---
echo "1/5 Getting remote HEAD..."
HEAD_SHA=$(api_get "/git/refs/heads/$BRANCH" | jq_py "['object']['sha']")
BASE_TREE=$(api_get "/git/commits/$HEAD_SHA" | jq_py "['tree']['sha']")
echo "     HEAD: ${HEAD_SHA:0:7}  Tree: ${BASE_TREE:0:7}"

# --- Step 2: Create blobs ---
echo "2/5 Creating blobs (${#FILES[@]} files)..."
BLOB_SHAS=()
for FILE in "${FILES[@]}"; do
  if [ ! -f "$FILE" ]; then
    echo "  SKIP (not found): $FILE"
    continue
  fi

  # Use python3 to build JSON safely (handles any file size)
  BLOB_SHA=$(python3 -c "
import base64, json, sys
with open('$FILE', 'rb') as f:
    content = base64.b64encode(f.read()).decode()
sys.stdout.write(json.dumps({'content': content, 'encoding': 'base64'}))
" | api_post "/git/blobs" | jq_py "['sha']")

  echo "  $FILE → ${BLOB_SHA:0:7}"
  BLOB_SHAS+=("$BLOB_SHA:$FILE")
done

# --- Step 3: Create tree ---
echo "3/5 Creating tree..."
TREE_JSON=$(python3 -c "
import json, sys
entries = []
for item in sys.argv[1:]:
    sha, path = item.split(':', 1)
    entries.append({'path': path, 'mode': '100644', 'type': 'blob', 'sha': sha})
sys.stdout.write(json.dumps({'base_tree': '$BASE_TREE', 'tree': entries}))
" "${BLOB_SHAS[@]}")

NEW_TREE=$(echo "$TREE_JSON" | api_post "/git/trees" | jq_py "['sha']")
echo "     New tree: ${NEW_TREE:0:7}"

# --- Step 4: Create commit (python3 handles message escaping) ---
echo "4/5 Creating commit..."
COMMIT_SHA=$(python3 -c "
import json, sys
msg = sys.stdin.read()
sys.stdout.write(json.dumps({'message': msg, 'tree': '$NEW_TREE', 'parents': ['$HEAD_SHA']}))
" <<< "$MSG" | api_post "/git/commits" | jq_py "['sha']")
echo "     Commit: ${COMMIT_SHA:0:7}"

# --- Step 5: Update branch ---
echo "5/5 Updating $BRANCH..."
echo "{\"sha\":\"$COMMIT_SHA\"}" \
  | curl -sf -X PATCH -H "Authorization: token $TOKEN" \
    -H "Content-Type: application/json" "$API/git/refs/heads/$BRANCH" -d @- > /dev/null

echo ""
echo "Done! Pushed ${#FILES[@]} files in 1 commit."
echo "https://github.com/$REPO/commit/$COMMIT_SHA"
