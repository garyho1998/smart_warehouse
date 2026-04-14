# Warehouse Platform MVP (倉庫版 PLTR-lite)

Smart warehouse integration platform for 3 warehouses — unified visibility, task orchestration, and exception handling on top of existing WMS/ERP systems.

## Problem

3 warehouses each run their own WMS / ERP / Excel / manual processes. No unified view of inventory, tasks, status, or exceptions across sites.

## What This Is

An **integration + orchestration layer** that sits on top of existing systems. It does **not** replace WMS/ERP — it unifies them.

Key capabilities:
- **Data integration** — adapters for each source system (REST, CSV, SFTP)
- **Canonical data model** — unified Warehouse, Location, SKU, Pallet, Order, Task, Device, Robot
- **Workflow engine** — inbound, transfer, pick, outbound, stocktake, exception handling
- **Operations dashboard** — real-time status, alerts, audit trail, reconciliation

## Architecture

```
┌─────────────────────────────────────────────────┐
│              Operations / Visibility             │
│        Dashboard · Alerts · Audit · Recon        │
├─────────────────────────────────────────────────┤
│                 Workflow Layer                    │
│    Inbound · Transfer · Pick · Outbound · Count  │
├─────────────────────────────────────────────────┤
│            Canonical Data Model                  │
│  Warehouse · Location · SKU · Pallet · Order     │
│  Task · Device · Robot                           │
├─────────────────────────────────────────────────┤
│              Integration Layer                   │
│   REST Adapter · CSV Adapter · SFTP Adapter      │
├──────────┬──────────┬──────────┬────────────────┤
│  WMS-A   │  WMS-B   │  ERP     │  Excel/Manual  │
└──────────┴──────────┴──────────┴────────────────┘
```

## Rollout Strategy

1. **Pilot** — 1 warehouse: master data sync, order/task exchange, status feedback
2. **Replicate** — copy to remaining 2 warehouses
3. **Configure** — each warehouse gets its own zones, flow rules, SLA, device types, exception policies

## Tech Stack

- **Backend**: Java 21 / Spring Boot 3
- **Database**: PostgreSQL
- **Messaging**: (TBD — Kafka or RabbitMQ based on scale needs)
- **Frontend**: (TBD — lightweight dashboard, React or server-rendered)

## Future Expansion

- WCS / RCS interfaces
- Robot task dispatch
- Conveyor / gate / elevator integration
- Rule engine
- AI-based scheduling

## Non-Goals (MVP)

- Full Palantir-grade platform
- Replace existing WMS entirely
- Digital twin / complex AI
- Full automation of all processes

## Project Structure

```
warehouse-platform/
├── docs/                # Research, architecture, feasibility, deployment notes
├── pages/               # GitHub Pages HTML files (published under /pages/*)
├── gh-push-tree.sh      # GitHub API deploy helper
├── ontology-sdk-notes.md
├── CLAUDE.md
└── README.md
```

## Getting Started

Open the published pages directly from the `pages/` path, for example:

- `https://garyho1998.github.io/smart_warehouse/pages/industry-landscape.html`
- `https://garyho1998.github.io/smart_warehouse/pages/product-overview.html`
