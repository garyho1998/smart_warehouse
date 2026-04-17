# Ontology Demo

## Current Status

This module now implements the core vertical slice of the DB-driven ontology plan:

- Spring Boot scaffold
- H2 meta-table initialization
- `ontology.yml` bootstrap into DB meta-tables
- Cached `SchemaMetaRepository`
- Schema-driven data-table creation with FK and index setup
- `GenericRepository` for schema-driven CRUD
- demo seed data for warehouse graph scenarios
- `GraphService` with generic traversal and anomaly tracing
- `ActionExecutor` + `SideEffectExecutor` + `AuditService`
- additive runtime schema writes via `SchemaService`
- REST controllers for schema, objects, graph, and actions
- test coverage for bootstrap, schema loading, table sync, CRUD, graph traversal, actions, schema mutation, and controllers

## Commands

Use the project-local Maven settings and repo-local cache in this environment:

```bash
mvn -s .mvn/central-settings.xml -Dmaven.repo.local=.m2/repository test
mvn -s .mvn/central-settings.xml -Dmaven.repo.local=.m2/repository spring-boot:run
```

## API Highlights

Schema read:

```bash
curl http://localhost:8080/api/schema/types
curl "http://localhost:8080/api/schema/history?table=object_type_def"
```

Generic object CRUD:

```bash
curl "http://localhost:8080/api/objects/Task?status=FAILED"
curl http://localhost:8080/api/objects/Task/TSK-005
```

Graph and action:

```bash
curl "http://localhost:8080/api/graph/traverse/Task/TSK-005?depth=3"
curl http://localhost:8080/api/graph/trace-anomaly/Task/TSK-005
curl -X POST http://localhost:8080/api/actions/completeTask \
  -H 'Content-Type: application/json' \
  -d '{"taskId":"TSK-001","actor":"ops-user"}'
```

Runtime schema writes are additive only:

- `POST /api/schema/types`
- `POST /api/schema/types/{name}/properties`
- `POST /api/schema/links`
- `POST /api/schema/actions`

Example `POST /api/schema/types` payload:

```json
{
  "id": "Carrier",
  "description": "Runtime carrier type",
  "primaryKey": "id",
  "properties": [
    { "name": "id", "type": "string", "required": true },
    { "name": "warehouseId", "type": "string", "required": true },
    { "name": "code", "type": "string", "required": true, "uniqueCol": true },
    { "name": "name", "type": "string", "required": true }
  ]
}
```

Example `POST /api/schema/actions` payload:

```json
{
  "id": "renameCarrier",
  "description": "Rename a carrier",
  "objectTypeId": "Carrier",
  "parameters": {
    "actor": { "type": "string", "required": true }
  },
  "preconditions": [],
  "mutations": [
    { "set": { "name": "Renamed Carrier" } }
  ],
  "sideEffects": [],
  "audit": true
}
```

## WMS Adapter Demo

Shows Ontology as a deployment accelerator: adding a new WMS only needs one
Adapter class — Graph / AI / Rules / UI keep working with zero changes.

### Architecture

```
旺店通 Mock (9001, PHP form-encoded)  ─┐                     ┌─→ Graph / AI /
                                      ├─→ Ontology (8080) ───┤   Rules / UI
聚水潭 Mock (9002, REST JSON)         ─┘                     └─→ (zero changes)
```

Adapters in `src/main/java/com/warehouse/ontology/adapter/` pull every 30s
via `modified_after` and upsert into the ontology through `GenericRepository`.

### Run the demo

```bash
bash ontology-demo/scripts/run-demo.sh         # starts 5 processes in background
bash ontology-demo/scripts/run-demo.sh stop    # stops everything
```

Then open:
- `http://localhost:5173/sources` — see both WMS sync status + counts
- `http://localhost:5173/graph` — search `Location`, see records from BOTH WMS
- `http://localhost:5173/ai` — ask "有幾多個倉?" — AI doesn't know there are 2 WMS

### Key files

- `docs/plans/2026-04-17-wms-adapter-demo-design.md` — design rationale
- `docs/plans/2026-04-17-wms-adapter-demo.md` — task-by-task plan
- `src/main/java/.../adapter/WmsAdapter.java` — contract
- `src/main/java/.../adapter/wangdiantong/WangdiantongAdapter.java` — WDT mapping
- `src/main/java/.../adapter/jushuitan/JushuitanAdapter.java` — JST mapping
- `src/main/java/.../adapter/sync/WmsSyncScheduler.java` — poll + cursor
- `mock-wms/wangdiantong-mock/` — fake PHP-style 旺店通 server
- `mock-wms/jushuitan-mock/` — fake REST/JSON 聚水潭 server

### What the demo shows

| | 旺店通 (WDT) | 聚水潭 (JST) |
|---|---|---|
| URL style | `warehouse_query.php` | `/open/wms/partner/query` |
| Wire format | form-encoded | JSON |
| Field naming | `bin_code`, `goods_no` | `slot_id`, `sku_id` |
| Zone terms | 中文 (`揀貨區`) | 英文 enum (`pick`) |
| Pagination | — | `page_index` + `page_size` |

Despite the radical difference, **both flow into the same ontology** and every
downstream consumer (Graph view, AI chat, rules engine) just works.

## Current Limits

- Schema mutation is create-only. No rename, drop, destructive update, or backfill path yet.
- Action DSL is intentionally minimal: precondition `property in [...]`, mutation `set`, special value `NOW`, and single-hop side effects via `via`.
- Deferred endpoints:
  - `PUT /api/schema/types/{name}`
  - `GET /api/audit`
  - `GET /api/graph/impact/Robot/{id}`
