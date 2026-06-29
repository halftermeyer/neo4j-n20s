# n20s — Neo4j In-Memory Semantics

GDS-style in-memory RDF reasoning for Neo4j. Project scoped triples, run RDFS/OWL inference (forward or backward chaining), validate with SHACL, query with SPARQL — all from Cypher.

## The Idea

Use Cypher to **scope**, n20s to **reason**.

```cypher
// 1. Scope: Cypher finds Alice's drugs
MATCH (:Patient {name: 'Alice'})-[:PRESCRIBED]->(:Drug)-[:HAS_TRIPLE]->(t:Triple)

// 2. Project: scoped triples → in-memory RDF graph
WITH n20s.graph.project('check', t.s, t.p, t.o) AS g
RETURN g.tripleCount;

// 3. Query with backward chaining (RDFS reasoning on-the-fly)
CALL n20s.graph.query('check', '
  SELECT ?drug1 ?drug2 ?risk WHERE {
    ?drug1 a ?c1 . ?drug2 a ?c2 .
    ?c1 <http://ex.org/interactsWith> ?c2 .
    ?c1 <http://ex.org/risk> ?risk .
  }', 'RDFS') YIELD row RETURN row;

// 4. Validate: SHACL shapes (also projected in the graph)
CALL n20s.graph.validate('check')
YIELD focusNode, severity, message RETURN focusNode, severity, message;

// 5. Cleanup
CALL n20s.graph.drop('check');
```

## The GDS Analogy

| GDS | n20s |
|-----|------|
| `gds.graph.project()` | `n20s.graph.project()` |
| `gds.pageRank.stream()` | `n20s.graph.query()` (SPARQL) |
| `gds.wcc.stream()` | `n20s.graph.infer()` (forward chaining) |
| — | `n20s.graph.query(..., 'RDFS')` (backward chaining) |
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
| `n20s.graph.addTurtle(name, turtle)` | Parse a Turtle string and add triples to a named graph (creates if needed) |
| `n20s.graph.query(name, sparql)` | Run a SPARQL SELECT query |
| `n20s.graph.query(name, sparql, profile)` | Run a SPARQL SELECT with backward-chaining inference (no `infer()` step needed) |
| `n20s.graph.construct(name, sparql)` | Run a SPARQL CONSTRUCT query, return triples |
| `n20s.graph.infer(name, profile)` | Forward-chaining inference — materializes all entailed triples |
| `n20s.graph.validate(name)` | SHACL validation — shapes must be projected in the same graph |
| `n20s.graph.triples(name)` | Stream all triples from a named graph |
| `n20s.graph.list()` | List all in-memory graphs with triple counts |
| `n20s.graph.drop(name)` | Drop a named graph and free memory |

### Reasoning Profiles

Used by `infer()` and `query(..., profile)`:

| Profile | Description |
|---|---|
| `RDFS` | RDFS entailment (subClassOf, domain, range, type propagation) |
| `OWL_MICRO` | Minimal OWL (transitivity, symmetry, inverseOf) |
| `OWL_MINI` | OWL with intersectionOf, unionOf |
| `OWL` | Full OWL reasoning |

### Forward vs Backward Chaining

```cypher
// FORWARD CHAINING: materialize all inferences, then query
// Best for: multiple queries on the same projected graph
CALL n20s.graph.infer('scope', 'RDFS');                    // step 1: materialize
CALL n20s.graph.query('scope', 'SELECT ...');              // step 2: query
CALL n20s.graph.query('scope', 'SELECT ... (another)');    // step 3: fast (already materialized)

// BACKWARD CHAINING: reason on-the-fly during query
// Best for: one-shot queries, simpler workflow
CALL n20s.graph.query('scope', 'SELECT ...', 'RDFS');      // one step: reason + query
```

## Triples as Cargo

RDF knowledge rides along on LPG nodes, invisible to normal Cypher queries. Two strategies:

### Turtle Properties (`addTurtle`)

Serialize RDF as a Turtle string property on nodes. Clean, compact, no extra nodes. Use `n20s.graph.addTurtle()` to project.

```cypher
// Ingredient carries its RDF classification as a Turtle property
CREATE (:Ingredient {name: 'Retinol', turtle: '
  @prefix cosmo: <http://example.org/cosmo#> .
  cosmo:Retinol a cosmo:RetinoidAgent ;
                cosmo:maxConcentrationEU "0.05" .
'})

// Project into in-memory graph
MATCH (i:Ingredient) WHERE i.turtle IS NOT NULL
CALL n20s.graph.addTurtle('check', i.turtle)
YIELD graphName, added RETURN graphName, sum(added);
```

Best when: knowledge travels with the entity. Supports multiple calls to build up a graph incrementally.

### Triple Nodes (`project`)

Store each triple as a `(:Triple {s, p, o})` node connected via `[:HAS_TRIPLE]`. Granular, individually addressable. Use `n20s.graph.project()` to collect.

```cypher
// Drug carries individual triple nodes
(:Drug)-[:HAS_TRIPLE]->(:Triple {s: 'pharma:Aspirin', p: 'rdf:type', o: 'pharma:NSAID'})

// Project via aggregating function
MATCH (:Drug)-[:HAS_TRIPLE]->(t:Triple)
WITH n20s.graph.project('check', t.s, t.p, t.o) AS g
RETURN g.tripleCount;
```

Best when: you need fine-grained Cypher scoping of individual triples.

## SHACL Validation

SHACL shapes are projected as regular triples alongside the data. The `validate()` procedure parses shapes from the graph and validates the data against them.

Supports both property constraints and SPARQL-based constraints:

```cypher
// Project data + ontology + SHACL shapes from Turtle properties
MATCH (:Product {name: 'Retinol Booster'})-[:CONTAINS*]->(i:Ingredient)
WHERE i.turtle IS NOT NULL
WITH collect(i.turtle) AS turtles
MATCH (ont:Ontology), (sh:SHACLRules), (p:Product {name: 'Retinol Booster'})
WITH turtles + [p.turtle, ont.turtle, sh.turtle] AS allTurtles
UNWIND allTurtles AS t
CALL n20s.graph.addTurtle('check', t) YIELD added
RETURN sum(added);

// Validate — returns violations and warnings
CALL n20s.graph.validate('check')
YIELD focusNode, severity, message
RETURN focusNode, severity, message;

// → [Violation] Retinol — Retinoid incompatible with acid active agent
// → [Warning] AscorbicAcid — Missing EU max concentration limit
```

## Demos

Two ready-to-run demo scripts in `demo/`:

### Drug Interaction Safety Check

LPG graph of patients, drugs, prescriptions. RDF triples (as Triple nodes via `HAS_TRIPLE`) encode drug classifications, interaction rules, and CYP enzyme metabolism. Detects:

- **Class-level interactions**: Aspirin (NSAID) + Warfarin (Anticoagulant) → bleeding risk
- **CYP enzyme conflicts**: Omeprazole inhibits CYP2C19, which Clopidogrel needs to activate

4 patients, 10 drugs, 3 interaction scenarios (Alice, Carol, Dave).

### Cosmetics Regulation Impact Analysis

Multi-level formulation BOM with `reduce()` for final concentrations. RDF knowledge stored as Turtle properties on nodes — zero Triple nodes. Demonstrates:

- **BOM concentration**: `reduce(conc, r IN rels | conc * r.ratio)` computes final % through phases and pre-mixes
- **Regulation blast radius**: EU restricts Retinol above 0.05% → which products are affected?
- **Substitution validation**: Is Bakuchiol a compliant Retinol alternative?
- **Forward chaining**: project → `infer('RDFS')` → `query()` (3 steps)
- **Backward chaining**: project → `query(..., 'RDFS')` (2 steps, same result)
- **SHACL validation**: SPARQL-based constraint detects Retinoid + Acid co-formulation

5 products, 13 ingredients, 3 brands, 4 markets.

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
target/lib/commons-collections4-4.4.jar
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
│                                                │
│  RDF cargo (two strategies)                    │
│  (:Drug)-[:HAS_TRIPLE]->(:Triple {s,p,o})      │
│  (:Ingredient {turtle: '...'})                 │
│                                                │
│  n20s In-Memory Graphs (ephemeral)             │
│  ┌──────────────────────────────┐              │
│  │  Jena Model (heap-resident)  │              │
│  │  SPARQL · RDFS/OWL · SHACL   │              │
│  └──────────────────────────────┘              │
└──────────────────────────────────────────────┘
```

The LPG graph is the **structure** — you navigate it with Cypher.
The triples are **knowledge** — you reason over them with n20s.
Triples ride along as cargo (via `HAS_TRIPLE` or `turtle` properties), invisible to normal Cypher queries.

## Design Principles

- **Scope first, reason second.** Cypher narrows to what matters (hundreds of triples). n20s reasons over the subset (instant).
- **Triples are cargo, not structure.** RDF knowledge attaches to LPG nodes without polluting the graph with edges you'd never traverse.
- **Ephemeral by design.** In-memory graphs are projected, used, and dropped — like GDS projections.
- **No data model change required.** Your LPG stays as-is. RDF reasoning is a lens you project when needed.
- **Forward or backward — your choice.** Materialize inferences for repeated queries, or reason lazily for one-shot queries.

## Requirements

- Neo4j 5.x / 2025.x / 2026.x
- Java 17+
- Apache Jena 4.10 (bundled)

## License

Apache 2.0
