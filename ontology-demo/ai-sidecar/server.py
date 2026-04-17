"""
AI Sidecar for Ontology Demo.
Uses claude_agent_sdk.ClaudeSDKClient — no API key needed, piggybacks on `claude login`.
Defines MCP tools that proxy to the existing Java REST API (port 8080).

Usage:
    pip install -r requirements.txt
    python server.py          # starts on port 8421
"""
from __future__ import annotations

import asyncio
import json
import logging
import uuid
from dataclasses import dataclass, field
from time import time
from typing import Annotated

import aiohttp as aiohttp_lib
import aiohttp_cors
from aiohttp import web
from claude_code_sdk import (
    AssistantMessage,
    ClaudeCodeOptions,
    ClaudeSDKClient,
    ResultMessage,
    SystemMessage,
    TextBlock,
    ToolResultBlock,
    ToolUseBlock,
    UserMessage,
    create_sdk_mcp_server,
    tool,
)

JAVA_API = "http://localhost:8080/api"
log = logging.getLogger("ai-sidecar")

# ── Monkey-patch: SDK raises on unknown event types (e.g. rate_limit_event),
#    which kills the async generator permanently. Patch to return None instead.
import claude_code_sdk._internal.message_parser as _mp

_original_parse = _mp.parse_message


def _safe_parse(data):
    try:
        return _original_parse(data)
    except Exception as e:
        if "Unknown message type" in str(e):
            log.debug("Ignoring unknown SDK event: %s", e)
            return None
        raise


_mp.parse_message = _safe_parse


# ── MCP Tools (proxy to Java REST API) ─────────────────────────────


@tool(
    "search_objects",
    "Search for objects of a given ontology type. "
    "Optionally filter by a single property value. Returns a list of matching objects.",
    {
        "objectType": Annotated[str, "Ontology object type, e.g. Robot, Task, Warehouse, Order, Shelf"],
        "filterProperty": Annotated[str, "Optional property name to filter on, e.g. status, type"],
        "filterValue": Annotated[str, "Optional value to match for the filter property"],
    },
)
async def search_objects(args):
    url = f"{JAVA_API}/objects/{args['objectType']}"
    params = {}
    fp, fv = args.get("filterProperty"), args.get("filterValue")
    if fp and fv:
        params[fp] = fv
    async with aiohttp_lib.ClientSession() as s:
        async with s.get(url, params=params) as r:
            return _text_result(await r.text())


@tool(
    "get_object",
    "Get a single object by type and ID. Returns all properties.",
    {
        "objectType": Annotated[str, "Object type"],
        "objectId": Annotated[str, "Object ID"],
    },
)
async def get_object(args):
    url = f"{JAVA_API}/objects/{args['objectType']}/{args['objectId']}"
    async with aiohttp_lib.ClientSession() as s:
        async with s.get(url) as r:
            return _text_result(await r.text())


@tool(
    "explore_connections",
    "Traverse the ontology graph from a given object. "
    "Follows link types to discover connected objects. "
    "Returns nodes, edges, and insights. Result is displayed as a graph.",
    {
        "objectType": Annotated[str, "Starting object type"],
        "objectId": Annotated[str, "Starting object ID"],
        "depth": Annotated[int, "Traversal depth 1-4, default 3"],
    },
)
async def explore_connections(args):
    depth = args.get("depth") or 3
    url = f"{JAVA_API}/graph/traverse/{args['objectType']}/{args['objectId']}?depth={depth}"
    async with aiohttp_lib.ClientSession() as s:
        async with s.get(url) as r:
            return _text_result(await r.text())


@tool(
    "analyze_anomaly",
    "Analyze ANY object for anomalies and root causes using ontology rules. "
    "Works for Task, Robot, Zone, or any object type. "
    "Traces connections, evaluates declarative rules, returns graph + insights.",
    {
        "objectType": Annotated[str, "Object type to analyze, e.g. Task, Robot, Zone"],
        "objectId": Annotated[str, "Object ID to analyze, e.g. TSK-005, R-GEK-001"],
    },
)
async def analyze_anomaly(args):
    url = f"{JAVA_API}/graph/trace-anomaly/{args['objectType']}/{args['objectId']}"
    async with aiohttp_lib.ClientSession() as s:
        async with s.get(url) as r:
            return _text_result(await r.text())


@tool(
    "execute_action",
    "Execute an ontology action (write operation). "
    "IMPORTANT: Only call this AFTER the user has explicitly confirmed they want to proceed.",
    {
        "actionName": Annotated[str, "Action name, e.g. completeTask"],
        "parameters": Annotated[dict, "Action parameters including the object ID"],
    },
)
async def execute_action(args):
    url = f"{JAVA_API}/actions/{args['actionName']}"
    async with aiohttp_lib.ClientSession() as s:
        async with s.post(url, json=args.get("parameters", {})) as r:
            return _text_result(await r.text())


def _text_result(text: str) -> dict:
    return {"content": [{"type": "text", "text": text}]}


ontology_mcp = create_sdk_mcp_server(
    "ontology",
    tools=[search_objects, get_object, explore_connections, analyze_anomaly, execute_action],
)

# Tool name → frontend display type
TOOL_DISPLAY = {
    "search_objects": "table",
    "get_object": "object",
    "explore_connections": "graph",
    "analyze_anomaly": "graph",
    "execute_action": "action_result",
}


# ── Retrieval Context (mirrors Java RetrievalContextBuilder) ───────


async def build_retrieval_context() -> str:
    """Fetch schema + data summary + alerts from Java backend."""
    try:
        async with aiohttp_lib.ClientSession() as s:
            types = await (await s.get(f"{JAVA_API}/schema/types")).json()
            links = await (await s.get(f"{JAVA_API}/schema/links")).json()
            actions = await (await s.get(f"{JAVA_API}/schema/actions")).json()
    except Exception as e:
        return f"[Error fetching schema: {e}]\n"

    parts: list[str] = ["=== ONTOLOGY SCHEMA ===\n"]

    # Object types
    parts.append("Object Types:")
    for t in types:
        props = t.get("properties", {})
        prop_strs = []
        for pname, pdef in props.items():
            s = f"{pname}({pdef.get('type', '?')}"
            if pdef.get("required"):
                s += ", required"
            if pdef.get("uniqueCol"):
                s += ", unique"
            ev = pdef.get("enumValues")
            if ev:
                s += f", enum={ev}"
            s += ")"
            prop_strs.append(s)
        desc = f" — {t['description']}" if t.get("description") else ""
        parts.append(f"  {t['id']}{desc}")
        parts.append(f"    primaryKey: {t.get('primaryKey', '?')}")
        parts.append(f"    properties: {', '.join(prop_strs)}")
        parts.append("")

    # Link types
    parts.append("Link Types:")
    for lk in links:
        parts.append(
            f"  {lk['id']}: {lk['fromType']} -> {lk['toType']}"
            f" ({lk.get('cardinality', '?')}) via foreignKey={lk.get('foreignKey', '?')}"
        )
    parts.append("")

    # Action types
    if actions:
        parts.append("Action Types:")
        for a in actions:
            desc = f" — {a['description']}" if a.get("description") else ""
            parts.append(f"  {a['id']} on {a.get('objectTypeId', '?')}{desc}")
            params = a.get("parametersJson") or a.get("parameters", {})
            parts.append(f"    parameters: {json.dumps(params)}")
        parts.append("")

    # Data summary
    parts.append("\n=== DATA SUMMARY ===\n")
    try:
        async with aiohttp_lib.ClientSession() as s:
            for t in types:
                try:
                    resp = await s.get(f"{JAVA_API}/objects/{t['id']}")
                    objects = await resp.json()
                    line = f"{t['id']}: {len(objects)} records"
                    if "status" in t.get("properties", {}) and objects:
                        dist: dict[str, int] = {}
                        for obj in objects:
                            st = str(obj.get("status", "UNKNOWN"))
                            dist[st] = dist.get(st, 0) + 1
                        line += " (" + " ".join(f"{k}:{v}" for k, v in dist.items()) + ")"
                    parts.append(line)
                except Exception:
                    parts.append(f"{t['id']}: error reading")
    except Exception:
        parts.append("[Error fetching data summary]")

    # Alerts
    parts.append("\n=== ACTIVE ALERTS ===\n")
    alerts: list[str] = []
    try:
        async with aiohttp_lib.ClientSession() as s:
            try:
                robots = await (await s.get(f"{JAVA_API}/objects/Robot")).json()
                for r in robots:
                    bp = r.get("batteryPct")
                    if bp is not None and int(bp) <= 20:
                        alerts.append(f"Robot {r.get('id')} battery={bp}% (status: {r.get('status')})")
            except Exception:
                pass
            try:
                tasks = await (await s.get(f"{JAVA_API}/objects/Task", params={"status": "FAILED"})).json()
                for tk in tasks:
                    alerts.append(
                        f"Task {tk.get('id')} FAILED (type: {tk.get('type')}, robot: {tk.get('robotId')})"
                    )
            except Exception:
                pass
    except Exception:
        pass

    parts.extend(alerts if alerts else ["No active alerts."])
    return "\n".join(parts)


# ── System Prompt ──────────────────────────────────────────────────

PERSONA = """\
你係一個倉庫管理 AI 助手。你可以查詢同管理倉庫嘅所有資產：機器人、任務、訂單、倉庫、貨架等。

你擁有以下 ontology-native 工具：
- search_objects: 搜尋同列出物件
- get_object: 攞單一物件詳情
- explore_connections: Graph traversal，追蹤物件之間嘅關係
- analyze_anomaly: 分析任何物件嘅異常（Task, Robot, Zone 等），用 ontology rules 自動檢測
- execute_action: 執行操作（需要用戶確認）

回覆規則：
1. 用繁體中文回覆
2. 先理解用戶問題，再決定用邊個工具
3. 如果問題關於「點解」或「原因」，優先用 analyze_anomaly 或 explore_connections
4. 如果要執行操作，必須先同用戶確認
5. 簡潔回覆，唔好重複工具返回嘅 raw data
6. 用 explore_connections 嘅時候，結果會自動顯示喺右側 graph panel

以下係目前嘅 ontology schema 同倉庫狀態：

"""


async def build_system_prompt() -> str:
    context = await build_retrieval_context()
    return PERSONA + context


# ── SSE Helpers ────────────────────────────────────────────────────


async def sse_write(resp: web.StreamResponse, data: dict) -> None:
    line = f"data:{json.dumps(data, ensure_ascii=False)}\n\n"
    await resp.write(line.encode("utf-8"))


def extract_text(content) -> str:
    """Extract plain text from ToolResultBlock.content (str | list)."""
    if isinstance(content, str):
        return content
    if isinstance(content, list):
        texts = []
        for item in content:
            if isinstance(item, dict) and item.get("type") == "text":
                texts.append(item.get("text", ""))
            elif hasattr(item, "text"):
                texts.append(item.text)
            else:
                texts.append(str(item))
        return "\n".join(texts)
    return str(content)


def build_display_data(tool_name: str, content) -> dict:
    """Parse tool result and build frontend-friendly display data."""
    text = extract_text(content)
    try:
        data = json.loads(text)
    except (json.JSONDecodeError, TypeError):
        return {}

    if tool_name == "search_objects" and isinstance(data, list):
        return {"objects": data}
    if tool_name in ("explore_connections", "analyze_anomaly") and isinstance(data, dict) and "nodes" in data:
        return {"graphResult": data}
    if tool_name == "get_object" and isinstance(data, dict):
        return {"object": data}
    if tool_name == "execute_action" and isinstance(data, dict):
        return {"actionResult": data}
    return {}


# ── Session Pool (keeps subprocess alive for multi-turn memory) ───


@dataclass
class ChatSession:
    """A live ClaudeSDKClient subprocess with conversation history."""

    client: ClaudeSDKClient
    created_at: float = field(default_factory=time)
    last_used: float = field(default_factory=time)
    lock: asyncio.Lock = field(default_factory=asyncio.Lock)


class SessionPool:
    """Pool of ChatSession instances keyed by conversationId."""

    MAX_SESSIONS = 5
    SESSION_TTL = 600  # 10 minutes

    def __init__(self):
        self._sessions: dict[str, ChatSession] = {}

    async def get_or_create(
        self, conv_id: str, options: ClaudeCodeOptions
    ) -> tuple[ChatSession, bool]:
        """Return (session, is_new). Reuses existing if alive and not expired."""
        session = self._sessions.get(conv_id)
        if session and (time() - session.last_used < self.SESSION_TTL):
            session.last_used = time()
            return session, False

        # Expired or missing → clean up old
        if session:
            log.info("Session %s expired, removing", conv_id[:8])
            await self._remove(conv_id)

        # Evict oldest if pool is full
        if len(self._sessions) >= self.MAX_SESSIONS:
            oldest = min(self._sessions, key=lambda k: self._sessions[k].last_used)
            log.info("Pool full, evicting session %s", oldest[:8])
            await self._remove(oldest)

        # Create new: connect() spawns a claude subprocess
        client = ClaudeSDKClient(options=options)
        await client.connect()
        session = ChatSession(client=client)
        self._sessions[conv_id] = session
        log.info("Created new session %s (pool size: %d)", conv_id[:8], len(self._sessions))
        return session, True

    async def remove(self, conv_id: str) -> None:
        """Remove and disconnect a session (public, for error recovery)."""
        await self._remove(conv_id)

    async def _remove(self, conv_id: str) -> None:
        session = self._sessions.pop(conv_id, None)
        if session:
            try:
                await session.client.disconnect()
            except Exception:
                log.debug("Error disconnecting session %s", conv_id[:8], exc_info=True)

    async def cleanup_all(self) -> None:
        """Disconnect all sessions. Called on server shutdown."""
        for cid in list(self._sessions):
            await self._remove(cid)
        log.info("All sessions cleaned up")


pool = SessionPool()


# ── Chat Endpoint (SSE streaming) ─────────────────────────────────


async def handle_chat(req: web.Request) -> web.StreamResponse:
    body = await req.json()
    message = body.get("message", "")
    if not message:
        return web.json_response({"error": "message required"}, status=400)

    conversation_id = body.get("conversationId") or str(uuid.uuid4())

    # Build dynamic system prompt (only used when creating a new session)
    system_prompt = await build_system_prompt()
    log.info("System prompt built (%d chars)", len(system_prompt))

    options = ClaudeCodeOptions(
        model="claude-sonnet-4-6",
        system_prompt=system_prompt,
        permission_mode="bypassPermissions",
        mcp_servers={"ontology": ontology_mcp},
        max_turns=20,
        cwd="/tmp",
        allowed_tools=[
            "mcp__ontology__search_objects",
            "mcp__ontology__get_object",
            "mcp__ontology__explore_connections",
            "mcp__ontology__analyze_anomaly",
            "mcp__ontology__execute_action",
        ],
        disallowed_tools=["Skill", "Agent", "Bash", "Read", "Edit", "Write", "Glob", "Grep"],
    )

    # Get or create a persistent session (subprocess stays alive for memory)
    session, is_new = await pool.get_or_create(conversation_id, options)
    log.info("Session %s: %s", conversation_id[:8], "new" if is_new else "reused")

    resp = web.StreamResponse(
        headers={
            "Content-Type": "text/event-stream",
            "Cache-Control": "no-cache",
            "Connection": "keep-alive",
        }
    )
    await resp.prepare(req)

    # Send conversationId first
    await sse_write(resp, {"conversationId": conversation_id})

    # Track tool_use_id -> tool_name for display type mapping
    tool_map: dict[str, str] = {}

    try:
        async with session.lock:
            await session.client.query(message)
            async for ev in session.client.receive_response():
                if ev is None:
                    continue  # monkey-patched unknown event types return None

                if isinstance(ev, AssistantMessage):
                    for block in ev.content:
                        if isinstance(block, TextBlock):
                            await sse_write(resp, {"type": "text", "content": block.text})
                        elif isinstance(block, ToolUseBlock):
                            tool_map[block.id] = block.name
                            friendly = block.name.split("__")[-1] if "__" in block.name else block.name
                            await sse_write(
                                resp,
                                {
                                    "type": "tool_use",
                                    "content": friendly,
                                    "data": {"toolId": block.id, "input": block.input},
                                },
                            )

                elif isinstance(ev, UserMessage) and isinstance(ev.content, list):
                    for block in ev.content:
                        if isinstance(block, ToolResultBlock):
                            raw_name = tool_map.get(block.tool_use_id, "")
                            tool_name = raw_name.split("__")[-1] if "__" in raw_name else raw_name
                            display_type = TOOL_DISPLAY.get(tool_name, "text")
                            display_data = build_display_data(tool_name, block.content)
                            await sse_write(
                                resp,
                                {
                                    "type": "tool_result",
                                    "content": extract_text(block.content)[:200],
                                    "data": {
                                        "displayType": display_type,
                                        "displayData": display_data,
                                    },
                                },
                            )

                elif isinstance(ev, SystemMessage):
                    log.debug("System event: %s", getattr(ev, "subtype", "?"))

                elif isinstance(ev, ResultMessage):
                    cost = getattr(ev, "cost_usd", None) or 0
                    turns = getattr(ev, "num_turns", None) or 0
                    log.info("Chat complete: cost=$%.4f turns=%d", cost, turns)

    except Exception as e:
        log.exception("Chat error (session %s)", conversation_id[:8])
        # Session may be broken — remove it so next request auto-creates
        await pool.remove(conversation_id)
        await sse_write(resp, {"type": "text", "content": f"AI 出錯：{e}"})

    await sse_write(resp, {"type": "done"})
    await resp.write_eof()
    return resp


# ── Status Endpoint ────────────────────────────────────────────────


async def handle_status(_req: web.Request) -> web.Response:
    return web.json_response({"available": True, "mode": "claude-sdk"})


# ── App Factory ────────────────────────────────────────────────────


async def on_shutdown(_app: web.Application) -> None:
    log.info("Shutting down, cleaning %d sessions", len(pool._sessions))
    await pool.cleanup_all()


def create_app() -> web.Application:
    app = web.Application()
    app.on_shutdown.append(on_shutdown)

    cors = aiohttp_cors.setup(
        app,
        defaults={
            "*": aiohttp_cors.ResourceOptions(
                allow_credentials=True,
                expose_headers="*",
                allow_headers="*",
            )
        },
    )

    chat = cors.add(app.router.add_resource("/ai/chat"))
    cors.add(chat.add_route("POST", handle_chat))

    status = cors.add(app.router.add_resource("/ai/chat/status"))
    cors.add(status.add_route("GET", handle_status))

    return app


if __name__ == "__main__":
    logging.basicConfig(level=logging.INFO, format="%(asctime)s [%(name)s] %(message)s")
    log.info("Starting AI sidecar on http://127.0.0.1:8421")
    log.info("Expects Java backend at %s", JAVA_API)
    web.run_app(create_app(), host="127.0.0.1", port=8421)
