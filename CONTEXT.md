# n20s — Context for LLM Agents

This file explains the philosophy, architecture, and intended usage patterns of the n20s Neo4j plugin so that an LLM working on this codebase (or building demos on top of it) can make informed decisions.

## What n20s Is

n20s ("neo4j to semantics") is a Neo4j plugin that brings **RDF reasoning into Cypher workflows**. It follows the GDS (Graph Data Science) mental model: project data into an ephemeral in-memory structure, run computations, get results back, drop the projection.

The in-memory structure is an **Apache Jena Model** — a standards-compliant RDF triple store that supports SPARQL queries, RDFS/OWL inference, SHACL validation, and custom rule-based reasoning.

## The Core Insight

**LPG and RDF are complementary, not competing.**

- **LPG (Cypher)** excels at structural computation: path traversal, pattern matching, aggregation, graph algorithms. It's fast, intuitive, and scales to billions of nodes.
- **RDF (SPARQL + reasoning)** excels at logical computation: type inference via class hierarchies, property inheritance, domain/range propagation, ontology-driven validation. It leverages decades of formal semantics work.

Most projects force a choice. n20s lets you use both in the same query — Cypher for structure, Jena for logic.

## The "Scope First, Reason Second" Pattern

This is the central design pattern. It solves a real problem: reasoning over millions of triples is slow, but reasoning over hundreds is instant.

```
1. SCOPE with Cypher    → narrow to the relevant subgraph (fast, indexed)
2. PROJECT into n20s    → collect scoped triples into an in-memory Jena Model
3. REASON with n20s     → RDFS inference, custom rules, SPARQL queries
4. VALIDATE with n20s   → SHACL shapes check
5. DROP                 → free memory
```

The LPG graph acts as an index into the RDF knowledge. You never reason over the whole dataset — you reason over precisely the subset that matters for the question at hand.

## Triples as Cargo

RDF knowledge is stored as **cargo** on LPG nodes — invisible to normal Cypher queries, activated only when projected into n20s.

Two strategies:

### Turtle Properties
A `turtle` string property on a node containing valid Turtle RDF. Compact, self-contained, no extra nodes. Use `n20s.graph.addTurtle()` to project.

```cypher
(:Drug {name: 'Aspirin', turtle: '
    @prefix pharma: <http://example.org/pharma#> .
    pharma:Aspirin a pharma:NSAID, pharma:PlateletInhibitor .
'})
```

Best for: knowledge that travels with the entity. Ontologies, SHACL shapes, and classification data stored on dedicated nodes.

### Triple Nodes
Individual `(:Triple {s, p, o})` nodes connected via `[:HAS_TRIPLE]`. Each triple is a separate node, individually addressable by Cypher. Use `n20s.graph.project()` to collect.

```cypher
(:Drug)-[:HAS_TRIPLE]->(:Triple {s: 'pharma:Aspirin', p: 'rdf:type', o: 'pharma:NSAID'})
```

Best for: fine-grained Cypher scoping (e.g., select only triples related to a specific patient's drugs).

### When to Choose Which

- If the RDF knowledge is self-contained per entity → Turtle property
- If you need to select individual triples via Cypher patterns → Triple nodes
- If you have shared knowledge (ontology, rules) → Turtle property on a dedicated `(:Ontology)` or `(:SHACLRules)` node
- You can mix both strategies in the same graph

## Reasoning Capabilities

### Built-in Profiles (RDFS, OWL)
Standard entailment rules. RDFS handles subClassOf, subPropertyOf, domain, range, type propagation. OWL profiles add transitivity, symmetry, inverseOf, intersectionOf, etc. These are Jena's rule-based reasoners — not tableaux-based, so no full OWL DL (no cardinality, no negation).

### Custom Rules (Jena Rule Syntax)
Domain-specific rules using Jena's native format. Support built-in predicates (`greaterThan`, `lessThan`, `sum`, `notEqual`, `regex`, etc.).

```
[ruleName: (pattern1) (pattern2) builtinTest(args) -> (conclusion)]
```

Example:
```
[adult: (?x rdf:type http://ex.org/Person)
        (?x http://ex.org/age ?age)
        greaterThan(?age, 17)
    -> (?x rdf:type http://ex.org/Adult)]
```

Rules use full URIs except for `rdf:type` which Jena recognizes as a shorthand.

### Forward vs Backward Chaining
- **Forward (materialize)**: `infer()` / `inferWithRules()` — compute all entailed triples, store them, then query. Best for multiple queries on the same projection.
- **Backward (on-the-fly)**: `query(..., profile)` / `queryWithRules()` — reason during query execution, no materialization. Best for one-shot queries.

### Layered Reasoning
Custom rules can be combined with built-in profiles. The profile runs first (e.g., RDFS infers type hierarchy), then custom rules fire on the inferred model:

```cypher
CALL n20s.graph.queryWithRules('g', 'SELECT ...', '[custom rules]', 'RDFS')
```

## SHACL Validation
SHACL shapes are projected as regular triples (typically from a Turtle property on a `(:SHACLRules)` node). The `validate()` procedure parses shapes from the graph and validates data against them. Supports property constraints and SPARQL-based constraints.

**Important**: SPARQL queries inside `sh:select` need their own `PREFIX` declarations — they don't inherit from the surrounding Turtle.

## API Quick Reference

| Procedure | Purpose |
|---|---|
| `n20s.graph.project(name, s, p, o)` | Aggregating function: collect (s,p,o) rows into a named graph |
| `n20s.graph.addTurtle(name, turtle)` | Parse Turtle string into a named graph (creates if needed) |
| `n20s.graph.query(name, sparql, [profile])` | SPARQL SELECT, optional backward chaining |
| `n20s.graph.queryWithRules(name, sparql, rules, [profile])` | SPARQL SELECT with custom rules, optional profile underneath |
| `n20s.graph.construct(name, sparql)` | SPARQL CONSTRUCT, returns triples |
| `n20s.graph.infer(name, profile)` | Forward chaining with built-in profile |
| `n20s.graph.inferWithRules(name, rules, [profile])` | Forward chaining with custom rules, optional profile underneath |
| `n20s.graph.validate(name)` | SHACL validation |
| `n20s.graph.toTurtle(name)` | Serialize graph as Turtle string |
| `n20s.graph.triples(name)` | Stream all triples |
| `n20s.graph.list()` | List all in-memory graphs |
| `n20s.graph.drop(name)` | Drop a graph and free memory |
| `n20s.version()` | Plugin and Jena versions |

## What n20s Is NOT

- **Not a replacement for n10s (neosemantics).** n10s maps RDF to LPG — a mature, battle-tested ETL tool. n20s keeps RDF alive for reasoning without mapping. They're complementary.
- **Not a triple store.** In-memory graphs are ephemeral. They exist for the duration of a reasoning task, then get dropped.
- **Not a SPARQL endpoint.** n20s is invoked from Cypher, not from external SPARQL clients.
- **Not full OWL DL.** Jena's rule-based reasoners handle RDFS and common OWL patterns. For full OWL DL (cardinality, negation, disjointness), use a dedicated reasoner like Pellet.

## Relationship to n10s

n10s (neosemantics) is the established Neo4j RDF tool. It imports RDF, maps classes to labels, properties to node attributes. It works well for static RDF data that you want to query with Cypher.

n20s starts from a different premise: some workloads need RDF semantics kept alive — for inference, validation, provenance — alongside LPG structure. The two tools can coexist. If your RDF data is static and you just need Cypher queries, use n10s. If you need live reasoning on scoped subsets of your graph, use n20s.

## Building Demos

When building a demo on n20s, the most compelling pattern is:

1. **Start with an LPG problem** that users recognize (patients + drugs, products + ingredients, services + dependencies)
2. **Add RDF knowledge as cargo** where classification, rules, or ontological structure matters
3. **Show a question that Cypher alone can't answer** (requires type inference, class hierarchy traversal, or rule application)
4. **Show the "scope first" workflow** — Cypher narrows, n20s reasons
5. **Use real ontologies when possible** — life sciences (ChEBI, ATC, MeSH), SKOS taxonomies, EU regulatory vocabularies. RDF's value proposition is strongest when leveraging existing semantic web resources.

### Demo Conventions
- Use `;` to separate statements in `.cypher` files
- Step 0 should clean both Neo4j data AND n20s in-memory graphs
- Turtle string properties use single-quote Cypher delimiters with double-quote Turtle literals inside
- SPARQL inside `sh:select` must be a single-line double-quoted string with its own PREFIX declarations
- Browser bookmarks go in `demo_bookmarks/` as CSV (double quotes escaped as `""`)

## Project Structure (Multi-Module Maven)

The repo is a three-module Maven project:

```
neo4j-n20s/
├── n20s-core/      Shared Jena reasoning engine — GraphCatalog, GraphEngine, TripleParser, model POJOs
├── n20s-plugin/    Neo4j @Procedure wrappers — thin delegates to GraphEngine, includes shade plugin
├── n20s-server/    Javalin HTTP server — REST endpoints mirroring plugin procedures, Dockerfile
└── pom.xml         Parent POM with shared dependency versions
```

- **n20s-core** has zero Neo4j dependency. It contains `GraphCatalog` (named graph registry), `GraphEngine` (all Jena reasoning logic), `TripleParser` (literal/blank-node parsing), and result POJOs.
- **n20s-plugin** depends on n20s-core + Neo4j (provided). Each `@Procedure` method is a one-liner: `return GraphEngine.query(...).stream()`. The shade plugin bundles and relocates Jena for classpath safety.
- **n20s-server** depends on n20s-core + Javalin + Jackson. Exposes 13 REST endpoints that delegate to `GraphEngine`. Ships as a runnable fat JAR (22MB) with a multi-stage Dockerfile.

### When to use which

- **Plugin**: self-managed Neo4j where you can install JARs. Procedures are called directly from Cypher.
- **Server**: managed services (Aura) or environments where plugins can't be installed. The application scopes data with Cypher via bolt, then sends Turtle to the server for reasoning over HTTP.

Both use the exact same Jena reasoning engine underneath.

### REST API (n20s-server)

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

Request bodies are JSON. Example:
```bash
curl -X POST http://localhost:7474/graph/test/turtle \
  -H 'Content-Type: application/json' \
  -d '{"turtle":"@prefix ex: <http://ex.org/> . ex:Zeus a ex:God ."}'
```

## Technical Details

- **Runtime**: Apache Jena 6.1. Plugin bundles it in a fat JAR via maven-shade-plugin. Server bundles it via a separate shade config.
- **Package relocation** (plugin only): all Jena dependencies relocated under `n20s.shaded.*` to avoid classpath conflicts with Neo4j. The server has no relocation (no Neo4j on the classpath).
- **Memory**: Jena models are heap-resident (~200-300 bytes per triple). In the plugin, shared with Neo4j's JVM heap. In the server, its own JVM.
- **Thread safety**: `GraphCatalog` uses `ConcurrentHashMap`. Individual Jena Models are not thread-safe — concurrent access to the same named graph from different transactions/requests is not supported.
- **Neo4j compatibility** (plugin): 5.x, 2025.x, 2026.x. Java 17+.
- **Server deployment**: runnable fat JAR (22MB), port via `PORT` env var (default 7474), multi-stage Dockerfile provided.
