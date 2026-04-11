# Ontology SDK — Notes for Java Developers

## What is an Ontology Object?

A strongly-typed domain entity backed by a data source, with extra metadata that makes it queryable, linkable, and actionable across the entire platform.

### Java Analogy

An **Object Type** is like a Java class / JPA `@Entity`. Each **Object** (instance) is like one row mapped to that class.

```java
// Roughly what an "Object Type" represents
public class Employee {
    private String employeeId;   // primary key
    private String name;         // property
    private String role;         // property
    private String facilityId;   // link (foreign key -> Facility object type)
}
```

### Key Concepts Mapped to Java

| Ontology Concept | Java Equivalent | What the Ontology Adds |
|---|---|---|
| **Object Type** | JPA `@Entity` class | Schema registered centrally, not just in code |
| **Object** (instance) | One entity / one DB row | Addressable by primary key across the whole platform |
| **Property** | A field (`String name`) | Typed, named, indexed, searchable |
| **Link** | `@ManyToOne` / `@OneToMany` | First-class navigable relationship (graph traversal) |
| **Action** | A service method | Registered operation with validation + permission checks |

### Underlying Data Structure

The Ontology is **not** a single data structure. It's a **semantic layer** on top of existing storage:

```
                    +-------------------------+
                    |     Ontology Layer       |
                    |  (schema + type system)  |
                    +-----------+-------------+
                                | maps to
            +-------------------+--------------------+
            v                   v                    v
     PostgreSQL table   Parquet in S3      Kafka stream
     (employees)        (equipment_log)    (live_telemetry)
```

- **Schema** defines object types, properties, links, and actions (like a JPA metamodel but platform-wide)
- **Backing store** is whatever the data lives in (relational table, file storage, streaming topic)
- **Sync pipeline** transforms raw data into clean ontology objects (ETL into entity tables)

### The SDK — What It Generates

Auto-generated, type-safe client code from the ontology schema. Like if JPA entities + repository interfaces were auto-generated from a central schema registry:

```java
// Auto-generated — never written by hand
public interface EmployeeRepository {
    Employee get(String primaryKey);
    List<Employee> search(Filter filter);
    Facility getFacility(Employee emp);
    List<Mission> getMissions(Employee emp);
}
```

Change the schema -> SDK regenerates -> compiler catches every breaking callsite.

---

## Can You Use the Ontology SDK Commercially?

The Ontology SDK is **not an open-source library**. It is **generated inside Palantir Foundry** — a licensed enterprise platform.

| Path | Cost | What You Get |
|---|---|---|
| **Palantir Foundry contract** | Enterprise pricing ($M+/year) | Full platform + auto-generated Ontology SDK |
| **Foundry for Builders** | Lower tier for smaller orgs | Same platform, different pricing |
| **Build your own** | Engineering time | Same concepts, your implementation |

### What IS Open Source from Palantir

- **osdk-ts** — TypeScript client for Foundry. Open source, but requires a Foundry backend to function.
- **Conjure** — RPC / code-generation framework. The plumbing underneath, not the Ontology layer.

The **concepts** (canonical model, semantic layer, knowledge graph) are not patented — only Palantir's specific implementation is proprietary.

---

## Free Open-Source Alternatives

No single project replicates the full Ontology SDK, but several cover key pieces.

### Closest Full Alternatives

**TypeDB (formerly Grakn)** — Knowledge graph database with a built-in type system. Closest to the Ontology concept.

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

- Typed entities + relationships = Object Types + Links
- Own query language (TypeQL) for graph traversal
- Java client available
- Missing: no auto-generated SDK, no action framework

**LinkedIn DataHub / OpenMetadata** — Metadata platforms with entity models and relationships. More focused on data cataloging than application development.

### Build Your Own from Open-Source Parts

| Ontology Feature | Open-Source Solution | Notes |
|---|---|---|
| Object Types + Properties | JPA `@Entity` | Standard canonical model |
| Links / Graph traversal | Spring Data JPA, or Apache TinkerPop / JanusGraph | JPA for simple relations, graph DB for deep traversal |
| Auto-generated SDK / API | JHipster or Spring Data REST + OpenAPI Generator | Schema -> API -> client SDK |
| Central schema registry | Apache Avro / JSON Schema | Define types centrally, generate code |
| Actions (mutations) | Spring service layer + Temporal | Durable, retryable actions |
| Search / indexing | Elasticsearch / OpenSearch | Full-text search across all object types |
| Permissions | Spring Security + OPA (Open Policy Agent) | Row-level / object-level access control |

### Most Pragmatic Path (Java 21 / Spring Boot 3 / PostgreSQL)

```
JPA Entities (object types)
  + Spring Data REST (auto-generated CRUD APIs)
  + OpenAPI Generator (auto-generated client SDK)
  + Spring HATEOAS (navigable links between objects)
```

This gives you:
1. Define a `Pallet` entity -> REST API auto-exposed -> client SDK auto-generated
2. Change a field -> regenerate -> compiler catches breaks
3. Links between `Pallet` -> `Location` -> `Warehouse` navigable via HATEOAS

~80% of the Ontology SDK value, zero licensing cost.

### Comparison

| | Palantir Ontology | DIY (Spring) | TypeDB |
|---|---|---|---|
| Cost | $$$$$ | Free | Free |
| Schema -> SDK codegen | Built-in | OpenAPI Generator | Manual |
| Graph traversal | Built-in | HATEOAS / JanusGraph | Built-in |
| Actions framework | Built-in | Temporal / service layer | Manual |
| Search | Built-in | Elasticsearch | TypeQL |
| Permissions | Built-in | Spring Security + OPA | Manual |
| Setup effort | Vendor does it | You build it | Moderate |

---

## Relevance to Warehouse Platform

The project's canonical model (`Warehouse`, `Location`, `SKU`, `Pallet`, `Order`, `Task`) in `platform-core/` is essentially the ontology. Key ideas borrowed from Palantir:

1. **Central schema** — one canonical model, not per-system models
2. **Adapters** — integration layer maps external systems into that model
3. **Links** — `Pallet` -> `Location` -> `Warehouse` are navigable relationships
4. **Actions** — workflows (inbound, pick, transfer) are registered operations on those objects

The warehouse platform achieves the same conceptual architecture with Spring Boot + JPA.
