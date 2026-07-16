# n20s Python client

The Cypher API of [n20s](https://github.com/halftermeyer/neo4j-n20s), in Python, over the HTTP sidecar — in the spirit of the [GDS Python client](https://neo4j.com/docs/graph-data-science/current/python-client/). Method names mirror the Cypher procedures one-to-one:

| Cypher | Python |
|---|---|
| `n20s.graph.projectTemplate('g', tpl, n)` | `n20s.graph.projectTemplate("g", tpl, cypher="MATCH … RETURN n")` |
| `CALL n20s.graph.query('g', sparql, 'RDFS')` | `n20s.graph.query("g", sparql, profile="RDFS")` |
| `CALL n20s.graph.queryWithRules(…)` | `n20s.graph.queryWithRules(…)` |
| `CALL n20s.graph.inferWithRules(…)` | `n20s.graph.inferWithRules(…)` |
| `CALL n20s.graph.validate('g')` | `n20s.graph.validate("g")` |
| `CALL n20s.graph.drop('g')` | `n20s.graph.drop("g")` |

The client **is the middleware**: it runs your scoping Cypher over Bolt (optional `neo4j` driver), converts nodes / relationships / paths to the canonical row shapes (`_labels`, `_type`, `_start`/`_end`, positional `_0…`) — identical to the plugin's conversions — and POSTs them to n20s-server. No plugin install, works with Aura.

```python
from neo4j import GraphDatabase
from n20s import N20s

driver = GraphDatabase.driver("neo4j://localhost:7687", auth=("neo4j", "…"))
n20s = N20s("http://localhost:7475", driver=driver)

# Scope with Cypher, shape with a template, reason with Jena
n20s.graph.projectTemplate(
    "check_lasagna", template,
    cypher="MATCH (:Recipe {id:$id})-[:CONTAINS*0..]->(n) RETURN n",
    params={"id": "lasagna"},
)
violations = n20s.graph.validate("check_lasagna")
n20s.graph.drop("check_lasagna")
```

No driver? Pass `rows=[…]` directly — the client is then a plain HTTP wrapper.

## Install

```bash
pip install -e n20s-python          # from the repo, with:
pip install -e 'n20s-python[neo4j]' # …the Bolt driver included
```

## Test

```bash
cd n20s-python && python -m pytest
# integration tests spawn the server jar automatically (build it first:
#   mvn -pl n20s-server -am package)
# Bolt round-trip test: set NEO4J_URI / NEO4J_USER / NEO4J_PASSWORD
```

Template semantics: [TEMPLATES.md](../TEMPLATES.md). Row conversions mirror the plugin exactly — one template, three transports (Cypher, HTTP, Python).
