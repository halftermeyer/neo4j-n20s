# n20s — Context for LLM Agents

This file is the operating manual for an LLM working on this codebase or building applications on top of n20s. It covers philosophy, API, conventions, and the gotchas that cost integrators real debugging time. For narrative documentation see [README.md](README.md); for the full integration guide see [BEST_PRACTICES.md](BEST_PRACTICES.md).

## What n20s Is

n20s brings **RDF reasoning into Cypher workflows** (and, via its server, into any HTTP client). It follows the GDS mental model: project data into an ephemeral in-memory structure, run computations, get results back, drop the projection.

The in-memory structure is an **Apache Jena Model** — a standards-compliant RDF store supporting SPARQL, RDFS/OWL inference, SHACL validation, and custom rule-based reasoning.

**The core insight: LPG and RDF are complementary computational lenses.**
- **LPG (Cypher)** — structural computation: traversal, pattern matching, aggregation, graph algorithms. Scales to billions of nodes.
- **RDF (SPARQL + reasoning)** — logical computation: type inference, class hierarchies, property inheritance, ontology-driven validation.

Cypher decides **what** gets reasoned about. n20s decides **how**.

## The Central Pattern: Scope First, Reason Second

```
1. SCOPE with Cypher    → narrow to the relevant subgraph (fast, indexed)
2. PROJECT into n20s    → collect scoped triples into a named in-memory model
3. REASON with n20s     → RDFS/OWL inference, custom rules, SPARQL
4. VALIDATE with n20s   → SHACL shapes
5. DROP                 → free memory
```

Reasoning over millions of triples is slow; reasoning over the few hundred that matter is instant. Never reason over the whole dataset — the scope *is* the optimization.

## Triples as Cargo

RDF knowledge is stored as **cargo** on LPG nodes — invisible to normal Cypher, activated only when projected.

**Turtle properties** (primary strategy) — a `turtle` string property containing self-contained Turtle. Project with `addTurtle()`:

```cypher
(:Ingredient {name: 'Retinol', turtle: '
    @prefix cosmo: <http://example.org/cosmo#> .
    @prefix rdfs: <http://www.w3.org/2000/01/rdf-schema#> .
    cosmo:Retinol a cosmo:RetinoidAgent ;
        rdfs:label "Retinol" .
'})
```

**Triple nodes** — individual `(:Triple {s, p, o})` nodes via `[:HAS_TRIPLE]`, each addressable by Cypher. Project with `project()`.

Choosing: self-contained per-entity knowledge → Turtle property. Fine-grained triple-level scoping → Triple nodes. Shared knowledge (ontology, shapes) → Turtle property on dedicated `(:Ontology)` / `(:SHACLRules)` nodes. Strategies mix freely in one projection.

## API Quick Reference

| Procedure / Function | Purpose |
|---|---|
| `n20s.graph.project(name, s, p, o, [ifExists])` | Aggregating function: collect (s,p,o) rows into a named graph. Default ifExists: `'replace'` |
| `n20s.graph.addTurtle(name, turtle, [ifExists])` | Aggregating function OR procedure: collect/parse Turtle. Default ifExists: `'append'` |
| `n20s.graph.projectTemplate(name, template, row, [ifExists])` | Aggregating function: project nodes or maps into a named graph via a JSON template (TDE/R2RML-style). Default ifExists: `'replace'` |
| `n20s.graph.query(name, sparql, [profile])` | SPARQL SELECT; optional backward-chaining profile |
| `n20s.graph.queryWithRules(name, sparql, rules, [profile])` | SPARQL SELECT with custom rules; optional profile layered underneath |
| `n20s.graph.construct(name, sparql)` | SPARQL CONSTRUCT, returns triples |
| `n20s.graph.infer(name, profile)` | Forward chaining, materializes (axiomatic triples filtered) |
| `n20s.graph.inferWithRules(name, rules, [profile])` | Forward chaining with custom rules (axiomatic triples filtered) |
| `n20s.graph.validate(name)` | SHACL validation (shapes projected in the same graph) |
| `n20s.graph.toTurtle(name)` | Serialize graph as Turtle string |
| `n20s.graph.triples(name)` | Stream all triples |
| `n20s.graph.list()` | List in-memory graphs |
| `n20s.graph.drop(name)` | Drop a graph, free memory |
| `n20s.version()` | n20s + Jena versions |

**Profiles**: `RDFS`, `OWL_MICRO`, `OWL_MINI`, `OWL` — Jena rule-based reasoners, not full OWL DL (no cardinality, no negation).

**ifExists** (`project`, `projectTemplate`, `addTurtle`): `'replace'` (drop + recreate), `'append'` (merge, create if needed), `'fail'` (error if exists).

**projectTemplate** — the `row` argument is a node (labels exposed to the template as `{_labels}`, element id as `{_elementId}`) or a map (`properties(t)` / computed row). Template JSON: `{"subject": "…{id}…", "triples": [{"predicate": "…", "object": "…{prop}…" | {"from": "_labels", "map": {…}}, "kind": "iri"|"literal", "datatype": "…", "include": […], "exclude": […]}]}`. A list-valued placeholder in the object fans out one triple per element (max one list per pattern, object position only). Missing property → pattern skipped; missing subject placeholder → row skipped (TDE semantics). Placeholder values in IRI positions are percent-encoded. `map` renames and filters in one table — elements absent from the map are dropped. Conditional mapping belongs in Cypher: compute a map and pass it as the row.

**Chaining**: forward (`infer*` — materialize once, query many) vs backward (`query(..., profile)` / `queryWithRules` — reason during the query, no materialization). Backward for one-shot checks; forward for repeated queries or Turtle export of entailments.

**Layering**: in `*WithRules`, the optional profile runs first (e.g., RDFS computes subclass closure), then custom rules fire on the enriched model. This is how a rule targeting a superclass catches all subclasses.

## Critical Gotchas

Hard-won knowledge — violating these produces silent wrong answers or hours of debugging. Full rationale in [BEST_PRACTICES.md](BEST_PRACTICES.md).

1. **Graph names are global and shared.** Use unique names per operation (`'check_' + uuid`); always drop in a `finally`. Fixed names are not concurrency-safe.
2. **`project()` replaces, `addTurtle()` appends** by default. Mixing them without explicit `ifExists` wipes data.
3. **Use `xsd:double`, not `xsd:decimal`, for rule-compared numbers.** Cypher's `toString()` emits scientific notation (`8.03E-4`) which `xsd:decimal` rejects. Both operands of a builtin must be the same XSD type.
4. **Jena rules use full IRIs** — no prefixes; only `rdf:type` has a shorthand.
5. **SPARQL inside `sh:select` must be single-line with its own `PREFIX` declarations** — it inherits nothing from the surrounding Turtle.
6. **`focusNode: null` + severity `INFO` from `validate()` means the graph conforms** — filter it before reporting violations.
7. **Every Turtle cargo string must be self-contained** (own `@prefix` lines) — any Cypher scope may select any subset.
8. **URI minting must be one shared convention.** Runtime-constructed URIs (e.g., injected computed values) that don't exactly match the cargo's URIs silently detach — validations then pass vacuously.
9. **Never split `.cypher` files on bare `;`** — Turtle cargo uses `;` as its predicate separator. Split on `;\n` or use a real parser.
10. **Turtle-in-Cypher**: single-quoted Cypher string, double-quoted Turtle literals, real newlines, escape `\` and `'`.
11. **`rdfs:label` everything** — rules and SPARQL results need human-readable names.
12. **SHACL + big shape sets + RDFS is slow** — for targeted checks, build a minimal inline shape at runtime.

## Agent Integration Pattern

When building LLM-agent tools (MCP or function calling) on n20s:

- **Tools encapsulate the full scope-project-reason-drop cycle** behind domain-level signatures (`validate_formulation(ingredients)`). The LLM never writes SPARQL and cannot skip validation steps.
- **Inject Cypher-computed values as typed triples** (`cosmo:X cosmo:actualConcentration "0.06"^^xsd:double`) so rules can compare structural computation against ontology limits.
- **Return structured verdicts**: `{status: "PASS"|"FAIL", violations: [...], shacl: [...]}` — agents act on fields, not prose.
- **Attach a `cypher_audit_trail`** to every tool response: the exact statements executed (log-then-run, parameters as comments), copy-paste runnable in Neo4j Browser. Instruct the LLM to render it in a fenced `cypher` block. This makes agent reasoning reproducible — the core trust device.

Reference implementation: [n20s-cosmo-rd](https://github.com/halftermeyer/n20s-cosmo-rd) (MCP server + dual-mode React app).

## What n20s Is NOT

- **Not a replacement for n10s (neosemantics).** n10s maps RDF into LPG (ETL). n20s keeps RDF alive for reasoning, no mapping. Complementary: n10s to import, n20s to reason.
- **Not a triple store.** In-memory graphs are ephemeral by design.
- **Not a SPARQL endpoint.** Invoked from Cypher or the REST API, not by external SPARQL clients.
- **Not full OWL DL.** No cardinality/negation reasoning. For tableaux-grade DL, use a dedicated reasoner — though with scope-first, RDFS + custom rules covers most production needs.

## Building Demos

The compelling arc:
1. Start with an **LPG problem people recognize** (patients+drugs, products+ingredients, services+dependencies).
2. Add **RDF cargo** where classification, rules, or ontological structure matters.
3. Show a **question Cypher alone can't answer** (type inference, hierarchy traversal, rule application).
4. Show **scope first**: Cypher narrows, n20s reasons.
5. Prefer **real ontologies/vocabularies** (ChEBI, ATC, MeSH, SKOS, EU regulatory).

Conventions:
- One statement per line in `.cypher` files; split on `;\n`, never bare `;`.
- Step 0 cleans both Neo4j data AND n20s in-memory graphs.
- Browser bookmarks as CSV in `demo_bookmarks/` (double quotes escaped as `""`).
- In cypher-shell, single-quote passwords containing `!` (zsh history expansion).

## Project Structure (Multi-Module Maven)

```
neo4j-n20s/
├── n20s-core/      GraphCatalog, GraphEngine, TripleParser, model POJOs — zero Neo4j dependency
├── n20s-plugin/    @Procedure wrappers (one-line delegates to GraphEngine) + shade plugin + demos
├── n20s-server/    Javalin HTTP server (13 REST endpoints) + Dockerfile
└── pom.xml         Parent POM, shared dependency versions
```

- **n20s-core**: all Jena logic. `GraphCatalog` (named-graph registry, `ConcurrentHashMap`), `GraphEngine` (reasoning operations), `TripleParser` (literal/blank-node parsing, null guards).
- **n20s-plugin**: depends on core + Neo4j (provided). Shade plugin relocates Jena under `n20s.shaded.*` to avoid classpath conflicts.
- **n20s-server**: depends on core + Javalin + Jackson. Fat JAR (~22 MB), starts in <200 ms.

**Deployment**: plugin for self-managed Neo4j (in-process, Cypher); server for Aura/managed (HTTP sidecar — app scopes over bolt, sends Turtle over REST). Same engine, identical reasoning results; build a one-flag dual-mode abstraction in clients.

## REST API (n20s-server)

JSON in, JSON out. `profile` optional (omit or `""` for no reasoning). Errors: `{"error": "message"}` with 400 (bad input), 404 (graph not found), 500 (unexpected).

**Env vars**: `PORT` (default 7474), `CORS=true` (browser clients).

### `POST /graph/{name}/turtle` — add Turtle

```json
// single, batch, or both — ifExists: "append" (default) | "replace" | "fail"
{"turtle": "@prefix ex: <http://ex.org/> . ex:Zeus a ex:God ."}
{"turtles": ["…", "…"], "ifExists": "replace"}

// → {"graphName": "g", "triplesBefore": 0, "triplesAfter": 2, "added": 2}
```

### `POST /graph/{name}/triples` — project (s,p,o) triples

```json
// body: array of triples; ?ifExists= query param (default: replace)
[{"s": "http://ex.org/Zeus", "p": "http://www.w3.org/1999/02/22-rdf-syntax-ns#type", "o": "http://ex.org/God"},
 {"s": "http://ex.org/Zeus", "p": "http://ex.org/name", "o": "\"Zeus\"@en"}]

// → {"graphName": "g", "tripleCount": 2, "status": "projected"}
```

Object encoding: URIs bare, blank nodes `_:id`, literals `"value"`, `"value"@lang`, `"value"^^<datatypeURI>`.

### `POST /graph/{name}/projectTemplate` — project rows via template

```json
// template: JSON object (or pre-serialized string); rows: array of maps;
// ifExists: "replace" (default) | "append" | "fail"
{"template": {
   "subject": "http://example.com#thing_{id}",
   "triples": [
     {"predicate": "http://www.w3.org/1999/02/22-rdf-syntax-ns#type",
      "object": {"from": "_labels", "map": {"Thing": "http://example.com#Thing"}}, "kind": "iri"},
     {"predicate": "http://example.com#has_prop",
      "object": "http://example.com#{prop}", "kind": "iri"}
   ]},
 "rows": [{"id": "id", "_labels": ["Thing"], "prop": ["p1", "p2", "p3"]}]}

// → {"graphName": "g", "rows": 1, "tripleCount": 4, "status": "projected"}
```

Same template semantics as the plugin function (see API Quick Reference). This is the endpoint a middleware calls after fetching nodes over Bolt: convert each node to `{…props, "_labels": […], "_elementId": "…"}` and POST.

### `POST /graph/{name}/query` — SPARQL SELECT

```json
{"sparql": "SELECT ?x WHERE { ?x a <http://ex.org/God> }", "profile": "RDFS"}

// → [{"row": {"x": "http://ex.org/Zeus"}}, {"row": {"x": "http://ex.org/Athena"}}]
```

### `POST /graph/{name}/queryWithRules`

```json
{"sparql": "SELECT ?x WHERE { ?x a <http://ex.org/Being> }",
 "rules": "[godBeing: (?x rdf:type http://ex.org/God) -> (?x rdf:type http://ex.org/Being)]",
 "profile": "RDFS"}

// → same shape as /query
```

### `POST /graph/{name}/construct`

```json
{"sparql": "CONSTRUCT { ?x <http://ex.org/is> <http://ex.org/divine> } WHERE { ?x a <http://ex.org/God> }"}

// → [{"subject": "…", "predicate": "…", "object": "…"}]
```

### `POST /graph/{name}/infer` and `/inferWithRules`

```json
{"profile": "RDFS"}                                    // infer
{"rules": "[…]", "profile": "RDFS"}                    // inferWithRules

// → {"graphName": "g", "triplesBefore": 3, "triplesAfter": 9, "newTriples": 6, "profile": "RDFS"}
```

### `POST /graph/{name}/validate` — SHACL (no body needed)

```json
// conforms:
[{"focusNode": null, "path": null, "severity": "INFO", "message": "Validation passed — graph conforms to all shapes.", "value": null, "sourceShape": null}]
// violations:
[{"focusNode": "http://ex.org/Alice", "path": "http://ex.org/name", "severity": "Violation", "message": "…", "value": null, "sourceShape": "…"}]
```

### Reads and management

```json
GET    /graph/{name}/turtle   → {"graphName": "g", "tripleCount": 2, "turtle": "…"}
GET    /graph/{name}/triples  → [{"subject": "…", "predicate": "…", "object": "…"}]
GET    /graph                 → [{"graphName": "g", "tripleCount": 3}]
DELETE /graph/{name}          → {"graphName": "g", "status": "dropped"}
GET    /version               → {"version": "…", "jenaVersion": "6.1.0"}
```

## Technical Details

- **Runtime**: Apache Jena 6.1, bundled in both plugin (shaded/relocated) and server fat JARs.
- **Memory**: heap-resident models, ~200–300 bytes/triple. Plugin shares Neo4j's heap; server has its own JVM.
- **Thread safety**: catalog is a `ConcurrentHashMap`; individual Jena models are **not** thread-safe — single writer per graph name.
- **Isolation**: catalog is JVM-global — shared across users and databases in plugin mode. Use unique names; do not treat projected graphs as access-controlled.
- **Compatibility**: Neo4j 5.x / 2025.x / 2026.x, Java 17+.
- **Testing**: 31 core (pure Jena, fast) + 31 plugin (Neo4j harness) + 18 server (HTTP) tests; CI runs all on every push.
