# n20s — In-Memory RDF Reasoning for Neo4j

**Scope with Cypher. Reason with ontologies. Verify everything.**

n20s adds a reasoning engine to Neo4j. Project a scoped slice of your graph into an ephemeral RDF model, apply ontologies, rules, and SHACL shapes, get back inferences and violations — from Cypher, or over HTTP from any application or agent.

```cypher
// An agent proposed a formulation. Can it ship?
MATCH (i:Ingredient) WHERE i.name IN $proposed
WITH collect(i.turtle) AS cargo
MATCH (ont:Ontology {name:'cosmo'}), (sh:SHACLRules {name:'cosmo_validation'})
UNWIND cargo + [ont.turtle, sh.turtle] AS t
WITH n20s.graph.addTurtle('check_' + $sessionId, t) AS g

CALL n20s.graph.validate('check_' + $sessionId)
YIELD focusNode, severity, message
RETURN focusNode, severity, message;
// → [Violation] Retinol — exceeds EU maximum concentration (0.05%)
```

The ontology said no. The agent can't ship what the rules forbid — and every reasoning step is a Cypher statement you can replay.

## Why n20s

**LLMs guess. Ontologies know.** GraphRAG grounds agents in your *data*; n20s grounds them in your *rules*. An agent that proposes a drug combination, a product formulation, or a compliance decision can have its output validated against formal domain knowledge — class hierarchies, inference rules, SHACL constraints — before anything ships. Deterministic, explainable, reproducible.

That's the flagship use case. The engine underneath is general:

- **Graph developers** get RDFS/OWL inference, custom rules, and SHACL validation without leaving Cypher — and without changing their data model.
- **Semantics practitioners** get Apache Jena embedded in Neo4j: real SPARQL, real entailment, real shapes, applied to precisely scoped subgraphs instead of monolithic triple stores.
- **Agent builders** get a validation endpoint (Neo4j procedure or REST API) whose answers come with an audit trail.

## How It Works

n20s follows the GDS mental model: **project → compute → drop**. Reasoning over millions of triples is slow; reasoning over the few hundred that matter is instant. Cypher decides *what* gets reasoned about; n20s decides *how*.

```
1. SCOPE     Cypher narrows to the relevant subgraph      (fast, indexed)
2. PROJECT   scoped triples → in-memory Jena model        (ephemeral, named)
3. REASON    RDFS/OWL inference, custom rules, SPARQL
4. VALIDATE  SHACL shapes
5. DROP      free the memory
```

| GDS | n20s |
|-----|------|
| `gds.graph.project()` | `n20s.graph.project()` / `n20s.graph.addTurtle()` |
| `gds.pageRank.stream()` — results out, projection untouched | `n20s.graph.query()` (SPARQL) |
| *stream-like* | `n20s.graph.query(..., 'RDFS')` (backward chaining) |
| *stream-like* | `n20s.graph.validate()` (SHACL) |
| `gds.wcc.mutate()` — results written back into the projection | `n20s.graph.infer()` (forward chaining, materializes entailments) |
| `gds.graph.export()` | `n20s.graph.toTurtle()` |
| `gds.graph.drop()` | `n20s.graph.drop()` |

Your LPG graph is the **structure** — you navigate it with Cypher and run GDS on it. RDF triples are **knowledge** — they ride along as cargo on your nodes (a `turtle` string property, or `(:Triple)` nodes), invisible to normal queries, activated only when projected into n20s.

## Quick Start

```cypher
// 1. Store RDF knowledge as cargo on a node
CREATE (:Ingredient {name: 'Retinol', turtle: '
  @prefix cosmo: <http://example.org/cosmo#> .
  @prefix rdfs: <http://www.w3.org/2000/01/rdf-schema#> .
  cosmo:Retinol a cosmo:RetinoidAgent ;
      rdfs:label "Retinol" ;
      cosmo:maxConcentrationEU "0.05"^^<http://www.w3.org/2001/XMLSchema#double> .
'});
CREATE (:Ontology {name: 'cosmo', turtle: '
  @prefix cosmo: <http://example.org/cosmo#> .
  @prefix rdfs: <http://www.w3.org/2000/01/rdf-schema#> .
  cosmo:RetinoidAgent rdfs:subClassOf cosmo:PhotosensitiveAgent .
'});

// 2. Scope + project
MATCH (n) WHERE n.turtle IS NOT NULL
WITH n20s.graph.addTurtle('demo', n.turtle) AS g
RETURN g.tripleCount;

// 3. Reason — RDFS infers Retinol is photosensitive (a class that exists nowhere in your LPG)
CALL n20s.graph.query('demo',
  'SELECT ?x WHERE { ?x a <http://example.org/cosmo#PhotosensitiveAgent> }',
  'RDFS')
YIELD row RETURN row;
// → {x: "http://example.org/cosmo#Retinol"}

// 4. Drop
CALL n20s.graph.drop('demo');
```

## API

### Aggregating Functions

| Function | Description |
|---|---|
| `n20s.graph.project(name, s, p, o, [ifExists])` | Collect (s, p, o) rows into a named in-memory RDF graph |
| `n20s.graph.addTurtle(name, turtle, [ifExists])` | Collect Turtle strings into a named in-memory RDF graph |
| `n20s.graph.projectTemplate(name, template, row, [ifExists])` | Project nodes, relationships, paths, entity lists, or maps into a named graph via a JSON template ([see below](#template-driven-projection)) |

All accept an optional `ifExists` parameter:

| Value | Behavior | Default for |
|---|---|---|
| `'replace'` | Drop existing graph and create new | `project()`, `projectTemplate()` |
| `'append'` | Merge into existing graph (create if needed) | `addTurtle()` |
| `'fail'` | Error if graph already exists (GDS-style) | — |

```cypher
// Collect Turtle from many nodes in one pass — single aggregation, no per-row CALL
MATCH (i:Ingredient) WHERE i.turtle IS NOT NULL
WITH n20s.graph.addTurtle('check', i.turtle) AS g
RETURN g.graphName, g.tripleCount, g.added;
```

### Template-Driven Projection

`projectTemplate()` maps LPG nodes to triples declaratively — in the spirit of MarkLogic TDEs and R2RML term maps. Cypher finds the pattern, the template shapes the triples, Jena reasons over them. The mapping is data, not code: store it as cargo on a `(:Template)` node, version it, audit it.

```cypher
CREATE (:Template {name: 'thing_mapping', template: '{
  "subject": "http://example.com#thing_{id}",
  "triples": [
    { "predicate": "http://www.w3.org/1999/02/22-rdf-syntax-ns#type",
      "object":    { "from": "_labels", "map": {
          "Thing": "http://example.com#Thing",
          "Ingredient": "http://example.org/cosmo#Ingredient"
      }},
      "kind": "iri" },
    { "predicate": "http://example.com#has_prop",
      "object":    "http://example.com#{prop}",
      "kind":      "iri" }
  ]
}'});

// One node (:Thing:Ingredient {id: 'x', prop: ['p1','p2','p3']}) → 2 rdf:type + 3 has_prop triples
MATCH (t:Thing), (tpl:Template {name: 'thing_mapping'})
WITH n20s.graph.projectTemplate('g', tpl.template, t) AS g
RETURN g.graphName, g.rows, g.tripleCount;
```

Semantics in brief:

- **Placeholders** `{name}` are substituted with row values; dotted paths (`{_start.id}`) reach into nested maps. Rows are **nodes** (labels exposed as `{_labels}`, element id as `{_elementId}`), **relationships** (`{_type}`, `{_start}`, `{_end}`), **paths / entity lists** (`[s, r, t]` → positional `{_0}`, `{_1}`, `{_2}`), or **maps** — pass `properties(t)` or a computed map when the mapping needs Cypher logic.
- **List fan-out** — a list-valued placeholder in the object emits one triple per element (object position only, max one list per pattern); dotted access fans out over lists of maps.
- **Filters** — `include` / `exclude` on fan-out elements; the **`map` spec** renames *and* filters in one reviewable table, in object position (label → class) or predicate position (relationship type → predicate).
- **Skip semantics (TDE-style)** — missing property skips the pattern, missing subject placeholder skips the row. No errors, no partial IRIs.
- **IRI safety & datatypes** — placeholder values in IRI positions are percent-encoded; literals keep native XSD types or take an explicit `datatype`.

Relationships project as triples the same way — prefer **named entity maps** (positional `[s, r, t]` → `{_0}`, `{_1}`, `{_2}` also works, but names can't silently reverse an edge):

```cypher
MATCH (s:Thing)-[r:RELATES_TO]->(t:OtherThing)
WITH n20s.graph.projectTemplate('g', tpl.template, {s: s, r: r, t: t}) AS g
RETURN g.rows, g.tripleCount;
// template: subject "…{s.id}", predicate {"from": "r._type", "map": {…}}, object "…{t.id}"
```

Anything conditional ("this label only when...") belongs in Cypher — compute a map and pass it as the row. The template stays deliberately dumb.

The full reference — every rule as row + template → triples, error catalog, HTTP examples — lives in **[TEMPLATES.md](TEMPLATES.md)**.

### Procedures

| Procedure | Description |
|---|---|
| `n20s.graph.addTurtle(name, turtle, [ifExists])` | Parse a Turtle string into a named graph (also available as aggregating function) |
| `n20s.graph.query(name, sparql, [profile])` | SPARQL SELECT; optional profile enables backward-chaining inference during the query |
| `n20s.graph.queryWithRules(name, sparql, rules, [profile])` | SPARQL SELECT with custom Jena rules (backward chaining); optional profile layers RDFS/OWL underneath |
| `n20s.graph.construct(name, sparql)` | SPARQL CONSTRUCT, returns triples |
| `n20s.graph.infer(name, profile)` | Forward-chaining inference — materializes entailed triples (axiomatic noise filtered out) |
| `n20s.graph.inferWithRules(name, rules, [profile])` | Custom rule inference (forward chaining, materializes); optional profile underneath |
| `n20s.graph.validate(name)` | SHACL validation — shapes are projected into the same graph as the data |
| `n20s.graph.validateWithRules(name, [rules], [profile])` | SHACL validation over ephemeral inference (profile first, rules on top) — the graph is never modified |
| `n20s.graph.toTurtle(name)` | Serialize a named graph as a Turtle string |
| `n20s.graph.triples(name)` | Stream all triples from a named graph |
| `n20s.graph.list()` | List all in-memory graphs with triple counts |
| `n20s.graph.drop(name)` | Drop a named graph and free memory |
| `n20s.version()` | n20s and Apache Jena versions |

### Reasoning Profiles

| Profile | Adds |
|---|---|
| `RDFS` | subClassOf, subPropertyOf, domain, range, type propagation |
| `OWL_MICRO` | RDFS + TransitiveProperty, SymmetricProperty, inverseOf |
| `OWL_MINI` | OWL_MICRO + intersectionOf, unionOf, hasValue |
| `OWL` | Jena's rule-based OWL reasoner (broadest coverage) |

These are Jena's rule-based reasoners — deliberately not full OWL DL (no cardinality reasoning, no negation). With the scope-first pattern you rarely need DL: class hierarchies, transitivity, and property inheritance over a scoped subgraph cover the overwhelming majority of production reasoning needs. If you need tableaux-grade DL, use a dedicated reasoner.

### Forward vs Backward Chaining

```cypher
// FORWARD: materialize once, query many times
CALL n20s.graph.infer('scope', 'RDFS');
CALL n20s.graph.query('scope', 'SELECT ...');          // fast — already materialized
CALL n20s.graph.query('scope', 'SELECT ... more');     // fast

// BACKWARD: reason on-the-fly, one step, no materialization
CALL n20s.graph.query('scope', 'SELECT ...', 'RDFS');
```

Rule of thumb: backward for one-shot checks — including SHACL via `validateWithRules()`, so a complete agent validation never mutates the projection. Forward when you'll run several queries on the same projection or want to export the entailed graph via `toTurtle()`.

### Custom Rules

Domain-specific inference in [Jena rule syntax](https://jena.apache.org/documentation/inference/#rules), with [built-in predicates](https://jena.apache.org/documentation/inference/#builtin-primitives) (`greaterThan`, `lessThan`, `notEqual`, `regex`, `sum`, ...). Rules use **full IRIs** (only `rdf:type` has a shorthand):

```cypher
CALL n20s.graph.queryWithRules('check',
  'SELECT ?name WHERE { ?x a <http://example.org/cosmo#Violation> ;
                           <http://www.w3.org/2000/01/rdf-schema#label> ?name }',
  '[euLimit: (?x http://example.org/cosmo#actualConcentration ?actual)
             (?x http://example.org/cosmo#maxConcentrationEU ?limit)
             greaterThan(?actual, ?limit)
         -> (?x rdf:type http://example.org/cosmo#Violation)]',
  'RDFS')                          // RDFS runs first, rules fire on the enriched model
YIELD row RETURN row;
```

## Agent Integration

n20s was built with agentic workloads in mind. The pattern proven by the [cosmetics R&D demo](https://github.com/halftermeyer/n20s-cosmo-rd) (MCP server + React app + Gemini function calling):

1. **The agent's tools scope with Cypher** — the LLM never writes SPARQL or touches the ontology directly; each tool encapsulates a scope-project-reason-drop cycle.
2. **Computed values become typed triples** — e.g., BOM concentrations computed by Cypher are injected as `cosmo:actualConcentration "0.081"^^xsd:double`, then rules compare them against ontology limits.
3. **Every tool response carries a `cypher_audit_trail`** — the exact Cypher/SPARQL/rule statements executed, copy-paste runnable in Neo4j Browser. The reasoning is never a black box; a technical audience can replay every step.
4. **Validation verdicts are structured** — `{status: PASS|FAIL, violations: [...], shacl: [...]}` so the agent can act on them, not parse prose.

See [BEST_PRACTICES.md](BEST_PRACTICES.md) for the full integration guide, including the audit-trail implementation and the pitfalls to avoid.

## SHACL Validation

Shapes are projected as regular triples alongside the data — typically from a Turtle property on a dedicated `(:SHACLRules)` node. Supports property constraints and SPARQL-based constraints:

```cypher
// Project data + ontology + shapes, then validate
MATCH (:Product {name: 'Retinol Booster'})-[:CONTAINS*]->(i:Ingredient)
WHERE i.turtle IS NOT NULL
WITH collect(i.turtle) AS turtles
MATCH (ont:Ontology), (sh:SHACLRules)
UNWIND turtles + [ont.turtle, sh.turtle] AS t
WITH n20s.graph.addTurtle('check', t) AS g

CALL n20s.graph.validate('check')
YIELD focusNode, severity, message
RETURN focusNode, severity, message;
```

A `focusNode` of `null` with severity `INFO` means the graph conforms. SPARQL constraints inside `sh:select` must be single-line and declare their own `PREFIX`es — see [BEST_PRACTICES.md](BEST_PRACTICES.md#shacl).

## Install

### Option 1: Neo4j Plugin (self-managed Neo4j)

Download the plugin fat JAR from [Releases](https://github.com/halftermeyer/neo4j-n20s/releases), copy to `plugins/`, allowlist, restart:

```bash
cp n20s-plugin-*.jar $NEO4J_HOME/plugins/
```

```
# neo4j.conf — append to an existing allowlist if you have one
dbms.security.procedures.allowlist=apoc.*,gds.*,n20s.*
```

All dependencies (Jena, SHACL) are bundled and relocated — no classpath conflicts.

### Option 2: Standalone Server (Aura / managed Neo4j)

Where plugins can't be installed, run **n20s-server** — the same Jena engine as an HTTP sidecar. Your application scopes with Cypher over bolt, collects Turtle cargo, and sends it to the server for reasoning.

```bash
java -jar n20s-server-*.jar                       # port 7474; PORT=… to change

# or Docker
docker build -f n20s-server/Dockerfile -t n20s-server .
docker run -p 7474:7474 n20s-server
```

Two environment variables:

| Var | Default | Effect |
|---|---|---|
| `PORT` | `7474` | HTTP port. Note `7474` is also Neo4j's default HTTP port — set another (e.g. `PORT=7475`) if you run Neo4j on the same host. |
| `CORS` | `false` | When `true`, sends `Access-Control-Allow-Origin: *` so browsers accept responses from any origin. |

#### Calling from a browser (CORS)

The server-to-server case (Python, a backend, `curl`) needs **nothing** — CORS is a browser rule, not a server one. It only matters when JavaScript running on one origin (`http://localhost:5173`) calls the n20s-server on another (`http://localhost:7474`); the browser blocks the response unless the server opts in. You have two ways to handle it:

**A. Dev proxy (recommended — no CORS needed).** Let your dev server forward a path prefix to n20s-server. Same-origin from the browser's point of view, so the CORS rule never triggers. This is what the [cosmo-rd app](https://github.com/halftermeyer/n20s-cosmo-rd) does — `app/vite.config.ts`:

```ts
server: {
  proxy: {
    '/n20s': {                                   // browser calls fetch('/n20s/version')
      target: process.env.VITE_N20S_URL || 'http://localhost:7475',
      changeOrigin: true,
      rewrite: (p) => p.replace(/^\/n20s/, ''),  // → http://localhost:7475/version
    },
  },
}
```

Leave the server started plainly (no `CORS`). If the proxy target and the server's `PORT` disagree, you get a **502** from the dev server — they must match.

**B. Enable CORS on the server (direct browser calls).** Skip the proxy and call the server's URL straight from the browser:

```bash
CORS=true java -jar n20s-server-*.jar            # allow any origin
```

`CORS=true` allows **every** origin (`*`). That's fine for local dev, but for anything exposed, front the server with a reverse proxy that restricts origins instead.

```bash
# Batch-add turtle, then reason
curl -X POST localhost:7474/graph/check/turtle -H 'Content-Type: application/json' \
  -d '{"turtles": ["@prefix ex: <http://ex.org/> . ex:Zeus a ex:God .",
                   "@prefix ex: <http://ex.org/> . @prefix rdfs: <http://www.w3.org/2000/01/rdf-schema#> . ex:God rdfs:subClassOf ex:Being ."],
       "ifExists": "replace"}'

curl -X POST localhost:7474/graph/check/query -H 'Content-Type: application/json' \
  -d '{"sparql": "SELECT ?x WHERE { ?x a <http://ex.org/Being> }", "profile": "RDFS"}'
```

| Plugin procedure | Method | Endpoint |
|---|---|---|
| `n20s.graph.addTurtle` | POST | `/graph/{name}/turtle` (accepts `turtle`, `turtles[]`, `ifExists`) |
| `n20s.graph.project` | POST | `/graph/{name}/triples` (`?ifExists=` query param) |
| `n20s.graph.projectTemplate` | POST | `/graph/{name}/projectTemplate` (accepts `template`, `rows[]`, `ifExists`) |
| `n20s.graph.query` | POST | `/graph/{name}/query` |
| `n20s.graph.queryWithRules` | POST | `/graph/{name}/queryWithRules` |
| `n20s.graph.construct` | POST | `/graph/{name}/construct` |
| `n20s.graph.infer` | POST | `/graph/{name}/infer` |
| `n20s.graph.inferWithRules` | POST | `/graph/{name}/inferWithRules` |
| `n20s.graph.validate` | POST | `/graph/{name}/validate` |
| `n20s.graph.validateWithRules` | POST | `/graph/{name}/validateWithRules` (accepts `rules`, `profile`, both optional) |
| `n20s.graph.toTurtle` | GET | `/graph/{name}/turtle` |
| `n20s.graph.triples` | GET | `/graph/{name}/triples` |
| `n20s.graph.list` | GET | `/graph` |
| `n20s.graph.drop` | DELETE | `/graph/{name}` |
| `n20s.version` | GET | `/version` |

Full request/response schemas for every endpoint: [CONTEXT.md](CONTEXT.md#rest-api-n20s-server).

**Same engine, same results.** An application can switch between plugin mode (bolt) and server mode (REST) behind one abstraction — the cosmo-rd demo runs both from a single flag.

**Python client.** [`n20s-python`](n20s-python/) mirrors the Cypher API over the server, GDS-Python-client style — including the Bolt fetch: `n20s.graph.projectTemplate("g", tpl, cypher="MATCH … RETURN n")` runs your scoping Cypher via the Neo4j driver, converts entities to the canonical row shapes, and POSTs them. The client is the middleware.

### Build from source

```bash
mvn clean package
cp n20s-plugin/target/n20s-plugin-*.jar $NEO4J_HOME/plugins/   # plugin
java -jar n20s-server/target/n20s-server-*.jar                 # server
```

## Best Practices

The condensed version — the full guide with rationale and code lives in [BEST_PRACTICES.md](BEST_PRACTICES.md):

1. **Unique graph names per operation** (`'check_' + sessionId`) — the catalog is shared; fixed names collide under concurrency.
2. **Always drop, ideally in a `finally`** — graphs survive transactions and are only freed on drop.
3. **Self-contained Turtle cargo** — every `turtle` property repeats its `@prefix` declarations so any subset composes.
4. **`xsd:double` for anything a rule compares** — `xsd:decimal` rejects the scientific notation Cypher's `toString()` emits.
5. **One URI-minting function** shared by the data generator and every consumer — drift here silently detaches triples.
6. **`rdfs:label` on every resource** — rules and SPARQL need human-readable names for output.
7. **Ontology and SHACL shapes on dedicated nodes** (`(:Ontology)`, `(:SHACLRules)`) — versionable, updatable without touching data.
8. **Inject Cypher-computed values as typed triples** — bridge structural computation into the reasoning layer.
9. **Audit trail on every reasoning call** — log the exact statements, return them with the result.
10. **Never split `.cypher` files on bare `;`** — Turtle and SPARQL cargo contain semicolons.

## Demos

Ready-to-run scripts in `n20s-plugin/demo/` (import `demo_bookmarks/n20s-all-demos.csv` in Neo4j Browser for clickable bookmarks):

- **Drug interaction safety check** — class-level interactions (NSAID + Anticoagulant → bleeding risk) and CYP enzyme conflicts, RDF as `(:Triple)` nodes.
- **Cosmetics regulation impact** — multi-level BOM concentrations via `reduce()`, EU regulation blast radius, substitution validation, SHACL co-formulation constraints, RDF as Turtle cargo.

The full reference application is **[n20s-cosmo-rd](https://github.com/halftermeyer/n20s-cosmo-rd)**: 153 ingredients with real INCI/CAS data, 4 regulatory markets, an MCP server for LLM agents, and a React app that runs in plugin or server mode.

## Architecture

```
┌──────────────────────────────────────────────┐
│  Neo4j (self-managed or Aura)                │
│                                              │
│  LPG graph (persistent)                      │
│  (:Product)-[:CONTAINS {ratio}]->(:Ingredient)│
│                                              │
│  RDF cargo (invisible to normal Cypher)      │
│  (:Ingredient {turtle: '…'})                 │
│  (:Drug)-[:HAS_TRIPLE]->(:Triple {s,p,o})    │
│  (:Ontology {turtle}) (:SHACLRules {turtle}) │
└──────────────┬───────────────────────────────┘
               │  scope with Cypher, then…
               ▼
┌──────────────────────────────────────────────┐
│  n20s reasoning engine (Apache Jena)         │
│  ephemeral named graphs · SPARQL ·           │
│  RDFS/OWL · custom rules · SHACL             │
│                                              │
│  delivered as:                               │
│  • Neo4j plugin  (in-process, from Cypher)   │
│  • HTTP server   (sidecar, from any client)  │
└──────────────────────────────────────────────┘
```

```
neo4j-n20s/
├── n20s-core/      shared Jena reasoning engine (no Neo4j dependency)
├── n20s-plugin/    Neo4j procedure wrappers + demos
├── n20s-server/    Javalin HTTP server + Dockerfile
└── pom.xml         parent POM
```

## n20s and n10s

[n10s (neosemantics)](https://neo4j.com/labs/neosemantics/) *maps* RDF into LPG — classes become labels, properties become attributes. It's the right tool for importing static RDF you want to query with Cypher.

n20s *keeps RDF alive* for reasoning: no mapping, no model change, ontology semantics preserved. Use n10s to bring RDF data in; use n20s when you need live inference and validation over scoped subsets. They coexist happily.

## Memory & Limitations

n20s graphs are heap-resident Jena models (~200–300 bytes/triple; 100k triples ≈ 20–30 MB). In plugin mode they share Neo4j's heap.

- **Scope first** — it's the speed story as much as the memory story. RDFS over 1k triples is instant; forward chaining can 5–10× the triple count.
- **Prefer backward chaining for one-shot queries** — no materialization.
- **Drop when done; monitor with `list()`.**
- **Global catalog (plugin)**: graphs live in a JVM-wide singleton shared across databases and users. Anyone with procedure access can list/query/drop any graph. Use unique per-session names; don't treat projected graphs as access-controlled.
- **Single-writer semantics**: Jena models aren't thread-safe. One writer per graph name at a time; concurrent access to *different* names is safe.

## Requirements

- Neo4j 5.x / 2025.x / 2026.x, Java 17+ (plugin)
- Java 17+ only (server)
- Apache Jena 6.1 (bundled in both)

## License

Apache 2.0
