# n20s — Neo4j In-Memory Semantics

GDS-style in-memory RDF reasoning for Neo4j. Project scoped triples, run RDFS/OWL inference, query with SPARQL — all from Cypher.

## Quick Start

```cypher
// Project triples into a named in-memory RDF graph
UNWIND [
  {s: 'http://ex.org/Zeus',   p: 'http://www.w3.org/1999/02/22-rdf-syntax-ns#type', o: 'http://ex.org/God'},
  {s: 'http://ex.org/Athena', p: 'http://www.w3.org/1999/02/22-rdf-syntax-ns#type', o: 'http://ex.org/God'},
  {s: 'http://ex.org/God',    p: 'http://www.w3.org/2000/01/rdf-schema#subClassOf', o: 'http://ex.org/Being'}
] AS t
WITH n20s.graph.project('myGraph', t.s, t.p, t.o) AS g
RETURN g.graphName, g.tripleCount;

// Run RDFS inference
CALL n20s.graph.infer('myGraph', 'RDFS');

// SPARQL query — Zeus is now a Being (inferred)
CALL n20s.graph.query('myGraph',
  'SELECT ?x WHERE { ?x a <http://ex.org/Being> }')
YIELD row RETURN row;

// Cleanup
CALL n20s.graph.drop('myGraph');
```

## API

| Function / Procedure | Description |
|---|---|
| `n20s.graph.project(name, s, p, o)` | Aggregating function — collect (s, p, o) rows into a named in-memory RDF graph |
| `n20s.graph.list()` | List all in-memory graphs with triple counts |
| `n20s.graph.triples(name)` | Stream all triples from a named graph |
| `n20s.graph.query(name, sparql)` | Run a SPARQL SELECT query |
| `n20s.graph.construct(name, sparql)` | Run a SPARQL CONSTRUCT query, return triples |
| `n20s.graph.infer(name, profile)` | Run inference. Profiles: `RDFS`, `OWL_MICRO`, `OWL_MINI`, `OWL` |
| `n20s.graph.drop(name)` | Drop a named graph and free memory |

## The GDS Analogy

| GDS | n20s |
|-----|------|
| `gds.graph.project()` | `n20s.graph.project()` |
| `gds.graph.list()` | `n20s.graph.list()` |
| `gds.graph.drop()` | `n20s.graph.drop()` |
| `gds.pageRank.stream()` | `n20s.graph.query()` (SPARQL) |
| `gds.wcc.stream()` | `n20s.graph.infer()` (reasoning) |

## Scoped Reasoning

The key idea: use Cypher to scope, then reason on the subset.

```cypher
// Scope: get triples about a product's BOM tree
MATCH (p:Product {name: 'SmartSensor'})<-[:USED_IN*]-(part)
MATCH (part)-[:HAS_TRIPLE]->(t)
WITH n20s.graph.project('bomScope', t.s, t.p, t.o) AS g
RETURN g.tripleCount;

// Reason over the scoped subset (fast — hundreds of triples, not millions)
CALL n20s.graph.infer('bomScope', 'RDFS');

// SPARQL query on the reasoned graph
CALL n20s.graph.query('bomScope',
  'SELECT ?x WHERE { ?x a <http://example.org/bom#Part> }')
YIELD row RETURN row;
```

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

## Requirements

- Neo4j 5.x / 2025.x / 2026.x
- Java 17+
- Apache Jena 4.10 (bundled)

## License

Apache 2.0
