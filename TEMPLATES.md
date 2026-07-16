# Template-Driven Projection — Semantics & Examples

The reference for `n20s.graph.projectTemplate()` (Cypher) and `POST /graph/{name}/projectTemplate` (REST). Every rule is shown as **row + template → triples**, so you can predict exactly what a template will emit before running it.

The idea, in one line: **Cypher finds the pattern, the template shapes the triples, Jena reasons over them.** Templates are deliberately dumb — placeholders, fan-out, filters, datatypes. Anything conditional belongs in Cypher (see [The escape hatch](#the-escape-hatch-computed-rows)). The design follows MarkLogic TDEs and R2RML term maps.

## Anatomy

```json
{
  "subject": "http://example.com#thing_{id}",
  "triples": [
    {
      "predicate": "http://example.com#has_prop",
      "object":    "http://example.com#{prop}",
      "kind":      "iri",
      "datatype":  "http://www.w3.org/2001/XMLSchema#double",
      "include":   ["only", "these"],
      "exclude":   ["not", "these"]
    }
  ]
}
```

| Field | Required | Meaning |
|---|---|---|
| `subject` | yes | IRI template for the row's subject. Placeholders must be scalar and present, or the row is skipped. |
| `triples[]` | yes (≥1) | One entry per triple pattern; each emits 0..n triples per row. |
| `predicate` | yes | IRI template. Placeholders must be scalar and present, or the pattern is skipped. |
| `object` | yes | A template string, or a `{"from": …, "map": …}` spec ([below](#map-rename--filter-in-one-table)). |
| `kind` | no | `"literal"` (default) or `"iri"`. |
| `datatype` | no | XSD datatype IRI; literals only. |
| `include` / `exclude` | no | Filters on fan-out elements, applied before anything else. |

A **row** is a map of values. When you pass a node, it is converted canonically:

```
(:Thing:Ingredient {id: 'x', prop: ['p1','p2']})
        ↓
{ "id": "x", "prop": ["p1","p2"],
  "_labels": ["Thing", "Ingredient"],        ← reserved
  "_elementId": "4:abc:42" }                 ← reserved
```

If a node has an actual property named `_labels` or `_elementId`, the conversion **errors loudly** rather than silently shadowing — rename the property or project a map instead.

## Placeholders

`{name}` is substituted with the row value for `name`. Names match `[A-Za-z_][A-Za-z0-9_]*`, so the reserved keys `{_labels}` and `{_elementId}` work like any other placeholder.

**Row**
```json
{ "id": "retinol", "name": "Retinol" }
```

**Template**
```json
{
  "subject": "http://example.com#ing_{id}",
  "triples": [
    { "predicate": "http://www.w3.org/2000/01/rdf-schema#label", "object": "{name}" }
  ]
}
```

**→ Triples**
```turtle
<http://example.com#ing_retinol> rdfs:label "Retinol" .
```

## List fan-out

A **list-valued** placeholder in the object emits one triple per element. This is the core TDE-style capability: *n* triples from one property.

**Row**
```json
{ "id": "id", "prop": ["p1", "p2", "p3"] }
```

**Template**
```json
{
  "subject": "http://example.com#thing_{id}",
  "triples": [
    { "predicate": "http://example.com#has_prop",
      "object": "http://example.com#{prop}",
      "kind": "iri" }
  ]
}
```

**→ Triples**
```turtle
<http://example.com#thing_id> ex:has_prop <http://example.com#p1> .
<http://example.com#thing_id> ex:has_prop <http://example.com#p2> .
<http://example.com#thing_id> ex:has_prop <http://example.com#p3> .
```

Rules:

- A scalar value in the same position emits exactly one triple — templates don't care whether `prop` is `"p1"` or `["p1"]`.
- An **empty list** emits zero triples (not an error).
- **At most one list per pattern.** Two list placeholders in one object template (`"…{a}_{b}"` with both lists) → error: no silent cartesian products.
- **Lists only fan out in the object position.** A list behind a subject or predicate placeholder → error. If you need per-element subjects, `UNWIND` in Cypher — that's a scoping decision, not a shaping one.

## Labels → `rdf:type`

`{_labels}` is a list like any other, so label typing is just fan-out:

**Row** (from node `(:Thing:Ingredient:_Imported {id: '1'})`)
```json
{ "id": "1", "_labels": ["Thing", "Ingredient", "_Imported"] }
```

**Template**
```json
{
  "subject": "http://example.com#n_{id}",
  "triples": [
    { "predicate": "http://www.w3.org/1999/02/22-rdf-syntax-ns#type",
      "object": "http://example.com#{_labels}",
      "kind": "iri",
      "exclude": ["_Imported"] }
  ]
}
```

**→ Triples**
```turtle
<http://example.com#n_1> a <http://example.com#Thing> .
<http://example.com#n_1> a <http://example.com#Ingredient> .
```

`exclude` drops housekeeping labels (`:_Imported`, SDN base labels, GDS scratch labels) before they become classes. `include` is the allowlist variant — element must be listed to survive. Both compare the element's **string value**, and both work on any fan-out, not just labels.

## `map`: rename + filter in one table

Labels rarely map 1:1 to ontology IRIs. The `{"from": …, "map": …}` object spec replaces each element with its mapped output — and **elements absent from the map are skipped**, so the map is simultaneously a rename table and a filter. It's the reviewable artifact: *here is, exhaustively, how LPG labels become ontology classes.*

**Row**
```json
{ "id": "1", "_labels": ["Thing", "Ingredient", "_Imported"] }
```

**Template**
```json
{
  "subject": "http://example.com#n_{id}",
  "triples": [
    { "predicate": "http://www.w3.org/1999/02/22-rdf-syntax-ns#type",
      "object": { "from": "_labels", "map": {
          "Thing":      "http://example.com#Thing",
          "Ingredient": "http://example.org/cosmo#Ingredient"
      }},
      "kind": "iri" }
  ]
}
```

**→ Triples**
```turtle
<http://example.com#n_1> a <http://example.com#Thing> .
<http://example.com#n_1> a <http://example.org/cosmo#Ingredient> .
```

`_Imported` is silently dropped — it has no entry. Notes:

- `from` names a row key; a scalar value is treated as a one-element list, so `{"from": "state", "map": {"ok": "…#Valid"}}` works on `state: "ok"`.
- Mapped outputs are used **verbatim** (they are full IRIs or literal text you wrote) — no placeholder substitution, no encoding.
- `include`/`exclude` still apply, before the map lookup.

## Skip semantics (TDE-style)

Missing data skips quietly instead of erroring or emitting broken IRIs:

| What's missing | Effect |
|---|---|
| A property behind an **object/predicate** placeholder | That **pattern** is skipped for this row; other patterns still emit |
| A property behind a **subject** placeholder | The whole **row** is skipped (no subject → nothing to say) |
| An element's key in a `map` | That **element** is skipped |

**Row**
```json
{ "id": "1", "name": "Retinol" }
```

**Template**
```json
{
  "subject": "http://ex.org#n_{id}",
  "triples": [
    { "predicate": "http://ex.org#name",  "object": "{name}" },
    { "predicate": "http://ex.org#grade", "object": "{grade}" }
  ]
}
```

**→ Triples** — one, not an error:
```turtle
<http://ex.org#n_1> ex:name "Retinol" .
```

The result's `rows` field counts rows received, `tripleCount` counts triples emitted — a big gap between them is your signal that data is sparser than the template assumes.

## Literals & datatypes

Default `kind` is `"literal"`. Three behaviors:

1. **Whole-template placeholder** (`"object": "{age}"`, no `datatype`) — the value's **native type** is kept: a Neo4j integer becomes `xsd:long`, a float `xsd:double`, a boolean `xsd:boolean`, a string a plain string literal.
2. **Explicit `datatype`** — the substituted text becomes a typed literal: `{"object": "{max}", "datatype": "http://www.w3.org/2001/XMLSchema#double"}` → `"0.05"^^xsd:double`.
3. **Composite template** (`"object": "batch-{id}"`) — always a plain string literal.

**Row**
```json
{ "id": "1", "age": 42, "max": "0.05" }
```

**Template**
```json
{
  "subject": "http://ex.org#n_{id}",
  "triples": [
    { "predicate": "http://ex.org#age", "object": "{age}" },
    { "predicate": "http://ex.org#max", "object": "{max}",
      "datatype": "http://www.w3.org/2001/XMLSchema#double" }
  ]
}
```

**→ Triples**
```turtle
<http://ex.org#n_1> ex:age "42"^^xsd:long .
<http://ex.org#n_1> ex:max "0.05"^^xsd:double .
```

Rule-of-thumb from [BEST_PRACTICES.md](BEST_PRACTICES.md): anything a Jena rule will compare should be `xsd:double` — set `datatype` explicitly rather than relying on native typing when values feed `greaterThan`/`lessThan`.

## IRI safety

Placeholder values substituted into **IRI positions** (subject, predicate, object with `kind: "iri"`) are percent-encoded R2RML-style: RFC 3986 unreserved characters and non-ASCII pass through, everything else is encoded. The template's fixed text is never touched.

**Row**
```json
{ "id": "my id", "prop": ["a/b"] }
```

**→ Triples**
```turtle
<http://example.com#thing_my%20id> ex:has_prop <http://example.com#a%2Fb> .
```

This is why concatenating IRIs in the template beats concatenating them in Cypher: the encoding lives in one place and can't be forgotten. Literal substitution is never encoded.

## Errors (loud, not silent)

| Condition | Error |
|---|---|
| Two list placeholders in one pattern | `at most one list per pattern` |
| List behind a subject/predicate placeholder | `lists may only fan out in the object position` |
| Node property named `_labels` / `_elementId` | reserved-key collision |
| `datatype` with `kind: "iri"` | `'datatype' only applies to kind 'literal'` |
| Malformed JSON, missing `subject`/`predicate`/`object`, unknown `kind` | parse error naming the field |
| Mixed graph names or templates in one aggregation | aggregation error (plugin) |

## The escape hatch: computed rows

Templates have no conditionals, no expressions, no joins — on purpose. When the mapping needs logic ("this class only when concentration > 0.05"), compute the row in Cypher and pass a **map** instead of a node:

```cypher
MATCH (i:Ingredient)
WITH i, CASE WHEN i.concentration > 0.05 THEN 'Regulated' ELSE 'Standard' END AS category
WITH n20s.graph.projectTemplate('g', $template,
     {id: i.id, category: category, props: i.props}) AS g
RETURN g.tripleCount;
```

The template stays a dumb, reviewable artifact; the logic stays in Cypher where it's testable and indexed.

## Full Cypher example: template as cargo

The mapping lives in the graph, next to the ontologies and shapes it serves:

```cypher
CREATE (:Template {name: 'thing_mapping', template: '{
  "subject": "http://example.com#thing_{id}",
  "triples": [
    { "predicate": "http://www.w3.org/1999/02/22-rdf-syntax-ns#type",
      "object": { "from": "_labels", "map": {
          "Thing": "http://example.com#Thing",
          "Ingredient": "http://example.org/cosmo#Ingredient"
      }},
      "kind": "iri" },
    { "predicate": "http://example.com#has_prop",
      "object": "http://example.com#{prop}",
      "kind": "iri" }
  ]
}'});

// Scope with Cypher, shape with the template, reason with Jena
MATCH (t:Thing), (tpl:Template {name: 'thing_mapping'})
WITH n20s.graph.projectTemplate('g', tpl.template, t) AS g

CALL n20s.graph.query('g',
  'SELECT ?x WHERE { ?x a <http://example.com#Thing> }')
YIELD row RETURN row;

CALL n20s.graph.drop('g');
```

## HTTP (n20s-server)

Same semantics over REST — this is the endpoint a middleware calls after fetching nodes over Bolt, converting each node to a row with the reserved keys:

```bash
curl -X POST localhost:7474/graph/g/projectTemplate -H 'Content-Type: application/json' -d '{
  "template": {
    "subject": "http://example.com#thing_{id}",
    "triples": [
      { "predicate": "http://example.com#has_prop",
        "object": "http://example.com#{prop}", "kind": "iri" }
    ]
  },
  "rows": [
    { "id": "id", "_labels": ["Thing"], "prop": ["p1", "p2", "p3"] }
  ],
  "ifExists": "replace"
}'

# → {"graphName": "g", "rows": 1, "tripleCount": 3, "status": "projected"}
```

`template` accepts a JSON object (natural) or a pre-serialized string (when the template comes from a node property). `ifExists` defaults to `"replace"`.
