# n20s — Neo4j In-Memory Semantics

GDS-style in-memory RDF reasoning for Neo4j. Project scoped triples, run RDFS/OWL inference, validate with SHACL, query with SPARQL — all from Cypher.

## The Idea

Use Cypher to **scope**, n20s to **reason**.

```cypher
// 1. Scope: Cypher finds Alice's drugs
MATCH (:Patient {name: 'Alice'})-[:PRESCRIBED]->(:Drug)-[:HAS_TRIPLE]->(t:Triple)

// 2. Project: scoped triples → in-memory RDF graph
WITH n20s.graph.project('check', t.s, t.p, t.o) AS g
RETURN g.tripleCount;

// 3. Reason: RDFS inference
CALL n20s.graph.infer('check', 'RDFS');

// 4. Query: SPARQL on the reasoned graph
CALL n20s.graph.query('check', '
  SELECT ?drug1 ?drug2 ?risk WHERE {
    ?drug1 a ?c1 . ?drug2 a ?c2 .
    ?c1 <http://ex.org/interactsWith> ?c2 .
    ?c1 <http://ex.org/risk> ?risk .
  }') YIELD row RETURN row;

// 5. Validate: SHACL shapes
CALL n20s.graph.validate('check')
YIELD focusNode, severity, message RETURN focusNode, severity, message;

// 6. Cleanup
CALL n20s.graph.drop('check');
```

## The GDS Analogy

| GDS | n20s |
|-----|------|
| `gds.graph.project()` | `n20s.graph.project()` |
| `gds.pageRank.stream()` | `n20s.graph.query()` (SPARQL) |
| `gds.wcc.stream()` | `n20s.graph.infer()` (reasoning) |
| — | `n20s.graph.validate()` (SHACL) |
| `gds.graph.drop()` | `n20s.graph.drop()` |

## API

### Aggregating Function

| Function | Description |
|---|---|
| `n20s.graph.project(name, s, p, o)` | Collect (s, p, o) rows into a named in-memory RDF graph |

### Procedures

| Procedure | Description |
|---|---|
| `n20s.graph.query(name, sparql)` | Run a SPARQL SELECT query |
| `n20s.graph.construct(name, sparql)` | Run a SPARQL CONSTRUCT query, return triples |
| `n20s.graph.infer(name, profile)` | Run inference. Profiles: `RDFS`, `OWL_MICRO`, `OWL_MINI`, `OWL` |
| `n20s.graph.validate(name)` | Validate against SHACL shapes contained in the graph |
| `n20s.graph.triples(name)` | Stream all triples from a named graph |
| `n20s.graph.list()` | List all in-memory graphs with triple counts |
| `n20s.graph.drop(name)` | Drop a named graph and free memory |

## Demos

Two ready-to-run demo scripts in `demo/`:

### Drug Interaction Safety Check

LPG graph of patients, drugs, prescriptions. RDF triples encode drug classifications, interaction rules, and CYP enzyme metabolism. Detects:

- **Class-level interactions**: Aspirin (NSAID) + Warfarin (Anticoagulant) → bleeding risk
- **CYP enzyme conflicts**: Omeprazole inhibits CYP2C19, which Clopidogrel needs to activate

4 patients, 10 drugs, 3 interaction scenarios (Alice, Carol, Dave).

### Cosmetics Regulation Impact Analysis

Multi-level formulation BOM with `reduce()` for final concentrations. Detects:

- **Regulation blast radius**: EU restricts Retinol above 0.05% → which products are affected?
- **BOM concentration**: `reduce(conc, r IN rels | conc * r.ratio)` computes final % through phases and pre-mixes
- **Substitution validation**: Is Bakuchiol a compliant Retinol alternative?
- **Incompatibility detection**: Retinol + Vitamin C → retinoid degradation at low pH
- **SHACL validation**: SPARQL-based constraint detects Retinoid + Acid co-formulation

5 products, 13 ingredients, 3 brands, 4 markets, SHACL shapes with SPARQL constraints.

### Browser Bookmarks

Import `demo_bookmarks/n20s-all-demos.csv` in Neo4j Browser (Saved Queries → Import) to get all demos as clickable bookmarks organized in folders.

## Install

### Build from source

```bash
mvn clean package -DskipTests
```

### Deploy

Copy these JARs to your Neo4j `plugins/` directory:

```
target/n20s-plugin-1.0.0-SNAPSHOT.jar
target/lib/jena-arq-4.10.0.jar
target/lib/jena-base-4.10.0.jar
target/lib/jena-core-4.10.0.jar
target/lib/jena-iri-4.10.0.jar
target/lib/jena-shacl-4.10.0.jar
target/lib/libthrift-0.19.0.jar
target/lib/collection-0.7.jar
```

### Configure

Add to `neo4j.conf`:

```
dbms.security.procedures.unrestricted=n20s.*
dbms.security.procedures.allowlist=apoc.*,gds.*,n20s.*
```

Restart Neo4j.

## Architecture

```
┌──────────────────────────────────────────────┐
│  Neo4j Instance                               │
│                                                │
│  LPG Graph (persistent)                        │
│  (:Patient)-[:PRESCRIBED]->(:Drug)             │
│  (:Product)-[:CONTAINS]->(:Ingredient)         │
│  (:Drug)-[:HAS_TRIPLE]->(:Triple {s,p,o})      │
│                                                │
│  n20s In-Memory Graphs (ephemeral)             │
│  ┌──────────────────────────────┐              │
│  │  Jena Model (heap-resident)  │              │
│  │  SPARQL · RDFS · SHACL       │              │
│  └──────────────────────────────┘              │
└──────────────────────────────────────────────┘
```

The LPG graph is the **structure** — you navigate it with Cypher.
The triples are **knowledge** — you reason over them with n20s.
The triples ride along as cargo via `HAS_TRIPLE`, invisible to normal Cypher queries.

## Design Principles

- **Scope first, reason second.** Cypher narrows to what matters (hundreds of triples). n20s reasons over the subset (instant).
- **Triples are cargo, not structure.** `HAS_TRIPLE` attaches knowledge to LPG nodes without polluting the graph with edges you'd never traverse.
- **Ephemeral by design.** In-memory graphs are projected, used, and dropped — like GDS projections.
- **No data model change required.** Your LPG stays as-is. RDF reasoning is a lens you project when needed.

## Requirements

- Neo4j 5.x / 2025.x / 2026.x
- Java 17+
- Apache Jena 4.10 (bundled)

## License

Apache 2.0
