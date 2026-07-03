# n20s Best Practices

Field-tested patterns for building applications and agents on n20s. Distilled from the [n20s-cosmo-rd](https://github.com/halftermeyer/n20s-cosmo-rd) reference application — including the mistakes it made so you don't have to.

Each practice states the rule, the reason, and where relevant the code. Anti-patterns are collected at the end.

---

## 1. Graph Lifecycle

### Use unique graph names per operation

The n20s catalog is a JVM-wide singleton. Two concurrent operations using the graph name `'validation'` will silently corrupt each other — one drops the graph while the other is mid-query.

```python
graph_name = f"validation_{uuid4().hex[:8]}"     # unique per call
```

Fixed names are acceptable only in strictly single-user contexts (a live demo you drive yourself). If you must reuse a name, drop-before-use makes the operation idempotent but **does not** make it concurrency-safe.

### Always drop, ideally in a `finally`

In-memory graphs survive transactions and sessions; they are freed only by `drop()` or a restart. A leaked 100k-triple graph is 20–30 MB of heap that never comes back.

```python
try:
    # addTurtle → reason → collect results
finally:
    session.run("CALL n20s.graph.drop($g)", g=graph_name)
```

Monitor with `CALL n20s.graph.list()` — a graph you don't recognize is a leak.

### Know your `ifExists` semantics

`project()` defaults to `'replace'`; `addTurtle()` defaults to `'append'`. If you `project()` into a graph you built up with `addTurtle()`, the default wipes it. Pass `ifExists` explicitly whenever you mix the two:

```cypher
WITH n20s.graph.project('g', t.s, t.p, t.o, 'append') AS g   -- keep what addTurtle loaded
```

Use `'fail'` when a name collision indicates a bug you want surfaced, not papered over.

---

## 2. Modeling RDF Cargo

### Every Turtle string is self-contained

Repeat all `@prefix` declarations in every `turtle` property. Any subset of cargo must compose into a valid graph — you never know which nodes a Cypher scope will select.

```turtle
@prefix cosmo: <http://example.org/cosmo#> .
@prefix rdfs: <http://www.w3.org/2000/01/rdf-schema#> .
@prefix xsd: <http://www.w3.org/2001/XMLSchema#> .

cosmo:Retinol a cosmo:RetinoidAgent ;
    rdfs:label "Retinol" ;
    cosmo:maxConcentrationEU "0.05"^^xsd:double .
```

### Shared knowledge lives on dedicated nodes

Ontology (class hierarchy, axioms) and SHACL shapes go on their own nodes, not duplicated per entity:

```cypher
(:Ontology   {name: 'cosmo',            turtle: '…class hierarchy…'})
(:SHACLRules {name: 'cosmo_validation', turtle: '…shapes…'})
```

They're loaded alongside scoped data at reasoning time, versionable independently, and updatable without touching entity nodes.

### One URI-minting function, everywhere

Turtle cargo references resources by URI; anything that *constructs* URIs at runtime (e.g., injecting computed values) must mint them **identically** to the data generator. The cosmo demo mints `"Hyaluronic Acid" → cosmo:HyaluronicAcid` by stripping non-alphanumerics — and implements that rule three times in three languages. If one drifts, computed triples silently stop attaching to their entities and **every validation passes vacuously** — the worst failure mode for a compliance system.

Extract the convention into one shared function per codebase, and consider a sanity check: after injecting triples, assert that each minted URI actually exists in the projected graph.

### `rdfs:label` on every resource

Rules and SPARQL queries need human-readable output. A violation report of raw URIs is useless to an agent or a user. Bind labels in your result queries:

```sparql
SELECT ?name ?actual ?limit WHERE {
  ?x a cosmo:Violation ; rdfs:label ?name ;
     cosmo:actualConcentration ?actual ; cosmo:maxConcentrationEU ?limit }
```

---

## 3. Datatypes and Literals

### Use `xsd:double` for anything a rule compares

Jena's `greaterThan`/`lessThan` builtins need both operands to be comparable numbers. Prefer `xsd:double`:

- `xsd:decimal` **rejects scientific notation** — and Cypher's `toString(0.000803)` produces `"8.03E-4"`, which fails to parse as a decimal. This bug only appears with small numbers, i.e., in production, at night.
- Keep both sides of a comparison the same XSD type. Comparing an untyped literal to a typed one silently never matches.

If your ontology declares `rdfs:range xsd:decimal` on a property whose data uses `^^xsd:double`, nothing enforces the mismatch today — but align them anyway; stricter validation will eventually meet that inconsistency.

### Turtle-in-Cypher escaping

- Outer Cypher string in **single quotes**, Turtle literals inside in **double quotes**.
- Use **real newlines** in the Turtle, not `\n` escape sequences.
- Escape `\` and `'` when generating (`s.replace("\\", "\\\\").replace("'", "\\'")`).

```cypher
CREATE (:Ingredient {name: 'Retinol', turtle: '
  @prefix cosmo: <http://example.org/cosmo#> .
  cosmo:Retinol a cosmo:RetinoidAgent ;
      cosmo:note "shouldn\'t exceed EU limits" .
'});
```

---

## 4. Scoping and Projection

### Cypher decides WHAT, n20s decides HOW

The canonical projection shape — traversal selects the cargo, one aggregation projects it:

```cypher
MATCH (i:Ingredient)-[:BELONGS_TO]->(:Category {name: $cat})
WHERE i.turtle IS NOT NULL
WITH n20s.graph.addTurtle($graph, i.turtle) AS g
RETURN g.tripleCount;
```

Never project the whole database "to be safe." The scope *is* the optimization: RDFS over 300 triples is instant; over 300k it's a coffee break, and forward chaining multiplies the count.

### Bulk-load with the aggregating function

One aggregation call per scope, not one procedure `CALL` per row:

```cypher
// plugin — single pass
MATCH (i:Ingredient) WHERE i.turtle IS NOT NULL
WITH n20s.graph.addTurtle('check', i.turtle) AS g RETURN g.added;
```

```bash
# server — single request with the batch field
curl -X POST localhost:7474/graph/check/turtle -H 'Content-Type: application/json' \
  -d '{"turtles": ["…", "…", "…"], "ifExists": "replace"}'
```

### Inject computed values as typed triples

The bridge between structural computation and logical reasoning: compute with Cypher, assert as RDF, reason over the result. The cosmo demo computes final BOM concentrations with `reduce()` over `CONTAINS*` ratios, then injects them:

```python
conc_turtle = PREFIXES + "\n".join(
    f'cosmo:{safe_name(i["name"])} cosmo:actualConcentration "{i["conc"]}"^^xsd:double .'
    for i in ingredients
)
# addTurtle(graph, conc_turtle) — now rules can compare actual vs. ontology limits
```

This is the pattern that makes n20s more than a SPARQL engine: LPG math becomes reasoning input.

---

## 5. Rules

### Full IRIs, RDFS underneath, one query for all verdicts

- Jena rules use **full IRIs** — no prefixes. Only `rdf:type` has a shorthand.
- Layer the `'RDFS'` profile under custom rules (`queryWithRules(…, rules, 'RDFS')`): RDFS enriches the model first (subclass closure), then your rules fire on the enriched model. This is how a rule targeting `cosmo:RegulatedIngredient` catches every subclass.
- When checking multiple rule sets (e.g., four markets), define one rule per market producing distinct conclusion types, then collect everything with a single `UNION` SPARQL query — one reasoning pass, one result set.

```
[euLimit: (?x http://example.org/cosmo#actualConcentration ?actual)
          (?x http://example.org/cosmo#maxConcentrationEU ?limit)
          greaterThan(?actual, ?limit)
      -> (?x rdf:type http://example.org/cosmo#EUViolation)]
```

Available builtins include `greaterThan`, `lessThan`, `equal`, `notEqual`, `regex`, `sum`, `product` — [full list](https://jena.apache.org/documentation/inference/#builtin-primitives).

---

## 6. SHACL

- **`sh:select` SPARQL must be a single-line string with its own `PREFIX` declarations** — it does not inherit prefixes from the surrounding Turtle.
- **`focusNode: null` with severity `INFO` means the graph conforms** — filter it before reporting violations.
- **SHACL + full shape sets + RDFS is the slowest combination.** For a targeted check (e.g., "does adding `a cosmo:Allergen` to this one ingredient break anything?"), build a minimal inline shape at runtime instead of projecting your entire shapes library:

```python
inline_shape = PREFIXES + '''
cosmo:AllergenShape a sh:NodeShape ;
    sh:targetClass cosmo:Allergen ;
    sh:sparql [ a sh:SPARQLConstraint ;
        sh:message "Allergen present above declaration threshold" ;
        sh:select "PREFIX cosmo: <http://example.org/cosmo#> SELECT $this WHERE { $this cosmo:actualConcentration ?c . FILTER(xsd:double(?c) > 0.001) }" ] .
'''
```

---

## 7. Agent Integration

### Tools encapsulate reasoning; the LLM never writes SPARQL

Each agent tool (MCP or function-calling) wraps a complete scope-project-reason-drop cycle behind a domain-level signature (`validate_formulation(ingredients)`, not `run_sparql(query)`). The LLM supplies domain parameters; the tool supplies the reasoning machinery. This keeps the agent honest — it cannot skip validation steps or query around the ontology.

### Return structured verdicts

```json
{
  "status": "FAIL",
  "violations": [{"ingredient": "Retinol", "market": "EU", "actual": 0.06, "limit": 0.05}],
  "shacl": [{"focusNode": "Retinol", "severity": "Violation", "message": "…"}]
}
```

An agent acts on `status: FAIL`; it cannot reliably act on prose. Strip URIs to local names (`focusNode.split("#")[-1]`) for readability.

### The audit trail pattern

Every tool response includes the exact statements executed, copy-paste runnable. This converts an agentic black box into something a technical audience can replay in Neo4j Browser — the single most effective trust-building device the reference app found.

```python
_thread_local = local()

def run_cypher(query, params=None):
    _get_trail().append(_fmt(query, params))          # log THEN run
    with driver.session() as s:
        return [dict(r) for r in s.run(query, params or {})]

def _build_response(results, label="results"):
    audit = "\n\n".join(f"// Step {i+1}\n{q}" for i, q in enumerate(_get_trail()))
    return json.dumps({label: results, "cypher_audit_trail": audit}, indent=2)
```

Implementation notes:
- Reset the trail at tool entry; log **then** run so failed statements appear too.
- Prepend parameters as a `// Parameters: {...}` comment so each step is runnable standalone.
- In the MCP `instructions`, tell the LLM to always render the trail in a fenced `cypher` block.
- In a UI, funnel plugin (bolt) and server (HTTP) calls into the same log, grouped per operation.

---

## 8. Deployment

| Context | Mode | Notes |
|---|---|---|
| Self-managed Neo4j | **Plugin** | In-process; call `n20s.*` from Cypher |
| Neo4j Aura / managed | **Server** | HTTP sidecar; app scopes over bolt, reasons over REST |
| Browser front-end (dev) | Server + proxy | Vite/dev-server proxy to dodge CORS, or `CORS=true` |
| Browser front-end (prod) | Backend in front | Never ship database credentials in a client bundle |

Build an abstraction with one API surface and two backends selected by config — the cosmo-rd app switches with a single env var and gets identical reasoning results, because both modes run the same Jena engine.

---

## 9. Anti-Patterns

Real mistakes from the reference implementation. Each one "worked in the demo."

| Anti-pattern | Why it bites | Instead |
|---|---|---|
| **Splitting `.cypher` files on bare `;`** | Turtle cargo uses `;` as its predicate separator; every ingredient statement gets shredded mid-string and fails as a syntax error | Split on `;\n` (statement-per-line files), use a real statement parser, or run via cypher-shell |
| **Fixed graph names** (`'validation'`) | Concurrent calls drop each other's graphs mid-flight | `f"validation_{uuid4().hex[:8]}"` + `finally: drop` |
| **`except Exception: pass`** around reasoning calls | Parse failures, OOM, and missing graphs vanish silently; a broken load looks like an empty dataset | Catch narrowly; count and report failures |
| **Credentials in the client bundle** (`VITE_NEO4J_PASSWORD`) | Anyone with the URL has your database | A thin backend owns the driver; the browser talks to the backend |
| **Duplicated conventions** (URI minting, rule strings, in N places) | They drift; triples detach; validations pass vacuously | One source of truth per convention; share or generate |
| **Claiming reasoning that doesn't run** | A tool marketed as "RDFS catches all subclasses" that actually does a label match will eventually be caught by exactly the audience you built the audit trail for | The audit trail keeps you honest — read your own |
| **Projecting everything, always** | Reasoning time and materialization blow-up scale with input size | Scope is the product: project the slice the question needs |

---

## Quick Checklist

Before shipping an n20s integration:

- [ ] Graph names unique per operation, dropped in `finally`
- [ ] All Turtle cargo self-contained (prefixes) with `rdfs:label`s
- [ ] Comparable literals typed `xsd:double`, both sides of every builtin
- [ ] One URI-minting function; injected URIs verified to attach
- [ ] Rules use full IRIs; RDFS layered underneath where subclass closure matters
- [ ] SHACL `sh:select` single-line with inline PREFIXes; `null` focusNode filtered
- [ ] Structured verdicts + audit trail on every reasoning tool
- [ ] No credentials in client bundles; no bare-`;` file splitting
