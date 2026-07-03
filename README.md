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

### Aggregating Functions

| Function | Description |
|---|---|
| `n20s.graph.project(name, s, p, o, [ifExists])` | Collect (s, p, o) rows into a named in-memory RDF graph |
| `n20s.graph.addTurtle(name, turtle, [ifExists])` | Collect Turtle strings into a named in-memory RDF graph |

Both accept an optional `ifExists` parameter:

| Value | Behavior | Default for |
|---|---|---|
| `'replace'` | Drop existing graph and create new | `project()` |
| `'append'` | Merge into existing graph (create if needed) | `addTurtle()` |
| `'fail'` | Error if graph already exists (GDS-style) | — |

```cypher
// Collect Turtle from multiple nodes in one pass
MATCH (i:Ingredient) WHERE i.turtle IS NOT NULL
WITH n20s.graph.addTurtle('check', i.turtle) AS g
RETURN g.graphName, g.tripleCount, g.added;

// project() with append mode
WITH n20s.graph.project('check', t.s, t.p, t.o, 'append') AS g
RETURN g.tripleCount;
```

### Procedures

| Procedure | Description |
|---|---|
| `n20s.graph.addTurtle(name, turtle, [ifExists])` | Parse a Turtle string and add triples to a named graph (creates if needed). Also available as aggregating function above. |
| `n20s.graph.query(name, sparql)` | Run a SPARQL SELECT query |
| `n20s.graph.query(name, sparql, profile)` | Run a SPARQL SELECT with backward-chaining inference (no `infer()` step needed) |
| `n20s.graph.construct(name, sparql)` | Run a SPARQL CONSTRUCT query, return triples |
| `n20s.graph.infer(name, profile)` | Forward-chaining inference — materializes entailed triples (axiomatic triples filtered out) |
| `n20s.graph.inferWithRules(name, rules, [profile])` | Custom rule-based inference (forward chaining, materializes). Optional profile layers RDFS/OWL underneath. |
| `n20s.graph.queryWithRules(name, sparql, rules, [profile])` | SPARQL query with custom rules (backward chaining, no materialization). Optional profile layers RDFS/OWL underneath. |
| `n20s.graph.validate(name)` | SHACL validation — shapes must be projected in the same graph |
| `n20s.graph.toTurtle(name)` | Serialize a named graph as a Turtle string |
| `n20s.graph.triples(name)` | Stream all triples from a named graph |
| `n20s.graph.list()` | List all in-memory graphs with triple counts |
| `n20s.graph.drop(name)` | Drop a named graph and free memory |
| `n20s.version()` | Return n20s and Apache Jena versions |

### Reasoning Profiles

Used by `infer()` and `query(..., profile)`:

| Profile | Description |
|---|---|
| `RDFS` | RDFS entailment (subClassOf, subPropertyOf, domain, range, type propagation) |
| `OWL_MICRO` | RDFS + TransitiveProperty, SymmetricProperty, inverseOf |
| `OWL_MINI` | OWL_MICRO + intersectionOf, unionOf, hasValue |
| `OWL` | Jena's rule-based OWL reasoner (broader OWL coverage, not full OWL DL) |

All profiles use Apache Jena's built-in rule-based reasoners (forward/backward chaining). These are not tableaux reasoners — they don't support full OWL DL (e.g., no cardinality reasoning, no negation). For most knowledge graph use cases (class hierarchies, transitivity, property inheritance), they are more than sufficient.

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

## Custom Rules

Beyond the built-in RDFS/OWL profiles, `inferWithRules()` and `queryWithRules()` let you define domain-specific inference rules using [Jena's rule syntax](https://jena.apache.org/documentation/inference/#rules). Rules use full URIs (or the built-in `rdf:type` shorthand) and support [Jena built-in predicates](https://jena.apache.org/documentation/inference/#builtin-primitives):

```cypher
CALL n20s.graph.inferWithRules('check', '
    [retinoidConflict:
        (?x rdf:type http://ex.org/cosmo#RetinoidAgent)
        (?y rdf:type http://ex.org/cosmo#AcidActiveAgent)
        notEqual(?x, ?y)
        -> (?x http://ex.org/cosmo#conflictsWith ?y)]
    [adultRule:
        (?p rdf:type http://ex.org/Person)
        (?p http://ex.org/age ?age)
        greaterThan(?age, 17)
        -> (?p rdf:type http://ex.org/Adult)]
')
YIELD newTriples RETURN newTriples;
```

Like the built-in profiles, custom rules support both forward and backward chaining. An optional reasoning profile (RDFS, OWL_MICRO, etc.) can be layered underneath — RDFS runs first, then custom rules fire on top:

```cypher
// FORWARD: materialize, then query
CALL n20s.graph.inferWithRules('g', '[rule: ...]');
CALL n20s.graph.query('g', 'SELECT ...');

// BACKWARD: reason on-the-fly, no materialization
CALL n20s.graph.queryWithRules('g', 'SELECT ...', '[rule: ...]');

// COMBINED: RDFS + custom rules (both forward and backward)
CALL n20s.graph.inferWithRules('g', '[rule: ...]', 'RDFS');
CALL n20s.graph.queryWithRules('g', 'SELECT ...', '[rule: ...]', 'RDFS');
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

### Option 1: Neo4j Plugin (recommended for self-managed Neo4j)

Download the latest plugin fat JAR from [GitHub Releases](https://github.com/halftermeyer/neo4j-n20s/releases) and copy it to your Neo4j `plugins/` directory:

```bash
cp n20s-plugin-*.jar $NEO4J_HOME/plugins/
```

Add n20s to the procedure allowlist in `neo4j.conf`:

```
dbms.security.procedures.allowlist=n20s.*
```

If you already have an allowlist (e.g., for APOC or GDS), append n20s to it:

```
dbms.security.procedures.allowlist=apoc.*,gds.*,n20s.*
```

Restart Neo4j. All dependencies (Apache Jena, SHACL, etc.) are bundled and relocated to avoid classpath conflicts.

### Option 2: Standalone Server (for Aura / managed Neo4j)

When the plugin can't be installed (e.g., Neo4j Aura), use the **n20s-server** — a standalone HTTP server exposing the same Jena reasoning engine as REST endpoints.

```bash
# Run the fat JAR
java -jar n20s-server-*.jar

# Or with Docker
docker build -f n20s-server/Dockerfile -t n20s-server .
docker run -p 7474:7474 n20s-server
```

The server starts on port 7474 (configurable via `PORT` env var). Set `CORS=true` for browser-based clients. Your application scopes data with Cypher on Aura, collects Turtle strings, and sends them to the server for reasoning.

```bash
# Add triples (single or batch)
curl -X POST http://localhost:7474/graph/check/turtle \
  -H 'Content-Type: application/json' \
  -d '{"turtles":["@prefix ex: <http://ex.org/> . ex:Zeus a ex:God .",
                   "@prefix ex: <http://ex.org/> . ex:God rdfs:subClassOf ex:Being ."],
       "ifExists":"replace"}'

# Query with RDFS backward chaining
curl -X POST http://localhost:7474/graph/check/query \
  -H 'Content-Type: application/json' \
  -d '{"sparql":"SELECT ?x WHERE { ?x a <http://ex.org/Being> }","profile":"RDFS"}'
```

#### REST API (n20s-server)

| Plugin procedure | Method | Endpoint |
|---|---|---|
| `n20s.graph.addTurtle(name, turtle)` | POST | `/graph/{name}/turtle` |
| `n20s.graph.project(name, s, p, o)` | POST | `/graph/{name}/triples` |
| `n20s.graph.query(name, sparql, profile)` | POST | `/graph/{name}/query` |
| `n20s.graph.queryWithRules(name, sparql, rules, profile)` | POST | `/graph/{name}/queryWithRules` |
| `n20s.graph.construct(name, sparql)` | POST | `/graph/{name}/construct` |
| `n20s.graph.infer(name, profile)` | POST | `/graph/{name}/infer` |
| `n20s.graph.inferWithRules(name, rules, profile)` | POST | `/graph/{name}/inferWithRules` |
| `n20s.graph.validate(name)` | POST | `/graph/{name}/validate` |
| `n20s.graph.toTurtle(name)` | GET | `/graph/{name}/turtle` |
| `n20s.graph.triples(name)` | GET | `/graph/{name}/triples` |
| `n20s.graph.list()` | GET | `/graph` |
| `n20s.graph.drop(name)` | DELETE | `/graph/{name}` |
| `n20s.version()` | GET | `/version` |

### Build from source

```bash
# Build all modules
mvn clean package

# Plugin JAR (for Neo4j plugins/ directory)
cp n20s-plugin/target/n20s-plugin-*.jar $NEO4J_HOME/plugins/

# Server JAR (standalone, runnable)
java -jar n20s-server/target/n20s-server-*.jar
```

## Architecture

```
┌──────────────────────────────────────────────┐
│  Neo4j (self-managed or Aura)                │
│                                              │
│  LPG Graph (persistent)                      │
│  (:Patient)-[:PRESCRIBED]->(:Drug)           │
│  (:Product)-[:CONTAINS]->(:Ingredient)       │
│                                              │
│  RDF cargo (two strategies)                  │
│  (:Drug)-[:HAS_TRIPLE]->(:Triple {s,p,o})    │
│  (:Ingredient {turtle: '...'})               │
└──────────────┬───────────────────────────────┘
               │ scope with Cypher, then...
               ▼
┌──────────────────────────────────────────────┐
│  n20s Reasoning Engine (Apache Jena)         │
│  ┌────────────────────────────────────────┐  │
│  │  In-Memory RDF Graphs (ephemeral)      │  │
│  │  SPARQL · RDFS/OWL · SHACL · Rules     │  │
│  └────────────────────────────────────────┘  │
│                                              │
│  Delivered as:                               │
│  • Neo4j plugin (in-process, from Cypher)    │
│  • HTTP server  (sidecar, from any client)   │
└──────────────────────────────────────────────┘
```

The LPG graph is the **structure** — you navigate it with Cypher.
The triples are **knowledge** — you reason over them with n20s.
Triples ride along as cargo (via `HAS_TRIPLE` or `turtle` properties), invisible to normal Cypher queries.

## Project Structure

```
neo4j-n20s/
├── n20s-core/      Shared Jena reasoning engine (no Neo4j dependency)
├── n20s-plugin/    Neo4j procedure wrappers (thin delegates to core)
├── n20s-server/    Javalin HTTP server + Dockerfile
└── pom.xml         Parent POM (multi-module Maven project)
```

## Design Principles

- **Scope first, reason second.** Cypher narrows to what matters (hundreds of triples). n20s reasons over the subset (instant).
- **Triples are cargo, not structure.** RDF knowledge attaches to LPG nodes without polluting the graph with edges you'd never traverse.
- **Ephemeral by design.** In-memory graphs are projected, used, and dropped — like GDS projections.
- **No data model change required.** Your LPG stays as-is. RDF reasoning is a lens you project when needed.
- **Forward or backward — your choice.** Materialize inferences for repeated queries, or reason lazily for one-shot queries.

## Memory Usage

n20s graphs are **heap-resident** Apache Jena models. Keep these guidelines in mind:

- **Jena scales well.** Apache Jena handles millions of triples in memory. 10k triples ≈ 2-3 MB of heap, 100k ≈ 20-30 MB. The practical limit depends on your Neo4j heap (`-Xmx`), since Jena models share it.
- **Scope first for speed, not just memory.** The "scope first, reason second" pattern keeps reasoning fast — RDFS inference on 1k triples is instant, on 100k it's still fast but forward chaining may 5-10x the triple count.
- **Forward chaining materializes triples.** `infer()` replaces the graph with an expanded copy. OWL profiles on large graphs can produce significantly more triples. Prefer backward chaining (`query(..., 'RDFS')`) for one-shot queries to avoid materialization.
- **Drop when done.** Always call `n20s.graph.drop()` after use. In-memory graphs survive across transactions and are only freed on drop or Neo4j restart.
- **Monitor with `n20s.graph.list()`.** Check triple counts to spot graphs that weren't cleaned up.

## Limitations

- **Global catalog (plugin).** In-memory graphs are stored in a JVM-wide singleton shared across all databases and all users. Any user with procedure access can list, query, or drop graphs created by another user — including graphs projected from a database they have no access to. This is fine for single-user or demo environments; for shared instances, use unique graph names per session (e.g., `"check_" + username`) and drop promptly.
- **Single-writer semantics.** Jena's in-memory Models are not thread-safe. Two concurrent `addTurtle()` calls on the same graph name, or a `drop()` while another transaction is mid-query, is undefined behavior. Stick to one writer per graph name at a time. Concurrent access to *different* graph names is safe (the catalog itself is a `ConcurrentHashMap`).

## Requirements

- Neo4j 5.x / 2025.x / 2026.x
- Java 17+
- Apache Jena 6.1 (bundled)

## License

Apache 2.0
