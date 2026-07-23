"""Integration tests — spawn a real n20s-server and run the AI-chef scenario.

Requires the fat JAR (mvn -pl n20s-server -am package). Skipped if absent.
"""

import glob
import os
import socket
import subprocess
import time

import pytest

from n20s import N20s

JAR_GLOB = os.path.join(
    os.path.dirname(__file__), "..", "..", "n20s-server", "target", "n20s-server-*.jar"
)

FOOD_TEMPLATE = {
    "subject": "http://example.org/food#{id}",
    "triples": [
        {"predicate": "http://www.w3.org/1999/02/22-rdf-syntax-ns#type",
         "object": {"from": "_labels", "map": {
             "Ingredient": "http://example.org/food#Ingredient",
             "Dairy": "http://example.org/food#Dairy",
             "Recipe": "http://example.org/food#Recipe",
         }},
         "kind": "iri"},
        {"predicate": "http://example.org/food#hasAllergen",
         "object": "http://example.org/food#allergen_{allergens}",
         "kind": "iri"},
    ],
}

CONTAINS_TEMPLATE = {
    "subject": "http://example.org/food#{s.id}",
    "triples": [
        {"predicate": {"from": "r._type", "map": {
            "CONTAINS": "http://example.org/food#contains"}},
         "object": "http://example.org/food#{t.id}",
         "kind": "iri"},
    ],
}

PROPAGATION_RULE = (
    "[allergenPropagation: (?r http://example.org/food#contains ?i)"
    " (?i http://example.org/food#hasAllergen ?a)"
    " -> (?r http://example.org/food#hasAllergen ?a)]"
)


def _free_port() -> int:
    with socket.socket() as s:
        s.bind(("", 0))
        return s.getsockname()[1]


@pytest.fixture(scope="session")
def server():
    jars = glob.glob(JAR_GLOB)
    if not jars:
        pytest.skip("n20s-server jar not built (mvn -pl n20s-server -am package)")
    port = _free_port()
    proc = subprocess.Popen(
        ["java", "-jar", jars[0]],
        env={**os.environ, "PORT": str(port)},
        stdout=subprocess.DEVNULL,
        stderr=subprocess.DEVNULL,
    )
    client = N20s(f"http://localhost:{port}")
    try:
        for _ in range(60):
            try:
                client.version()
                break
            except Exception:
                time.sleep(0.5)
        else:
            pytest.fail("n20s-server did not start")
        yield client
    finally:
        proc.terminate()
        proc.wait(timeout=10)


def test_version(server):
    assert "jenaVersion" in server.version()


def test_ai_chef_scenario(server):
    g = "check_lasagna_py"

    # 1. nodes — labels→classes map, allergen fan-out
    result = server.graph.projectTemplate(g, FOOD_TEMPLATE, rows=[
        {"id": "lasagna", "_labels": ["Recipe"]},
        {"id": "bechamel", "_labels": ["Recipe"]},
        {"id": "butter", "_labels": ["Ingredient", "Dairy"], "allergens": ["milk"]},
    ])
    assert result["rows"] == 3

    # 2. hops — named {s, r, t} rows, type→predicate map
    result = server.graph.projectTemplate(g, CONTAINS_TEMPLATE, rows=[
        {"s": {"id": "lasagna"}, "r": {"_type": "CONTAINS"}, "t": {"id": "bechamel"}},
        {"s": {"id": "bechamel"}, "r": {"_type": "CONTAINS"}, "t": {"id": "butter"}},
    ], ifExists="append")
    assert result["tripleCount"] == 2

    # 3. ontology
    server.graph.addTurtle(g, (
        "@prefix food: <http://example.org/food#> ."
        " @prefix rdfs: <http://www.w3.org/2000/01/rdf-schema#> ."
        " food:Dairy rdfs:subClassOf food:AnimalProduct ."
    ))

    # 4. backward chaining — rule recursion finds the milk, nothing materialized
    rows = server.graph.queryWithRules(
        g,
        "SELECT ?a WHERE { <http://example.org/food#lasagna>"
        " <http://example.org/food#hasAllergen> ?a }",
        PROPAGATION_RULE,
    )
    assert {"a": "http://example.org/food#allergen_milk"} in rows

    # 5. backward RDFS — AnimalProduct exists only in the ontology
    rows = server.graph.query(
        g,
        "SELECT ?x WHERE { ?x a <http://example.org/food#AnimalProduct> }",
        profile="RDFS",
    )
    assert {"x": "http://example.org/food#butter"} in rows

    # 6. validateWithRules — SHACL over ephemeral inference, graph untouched
    server.graph.addTurtle(g, (
        "@prefix sh: <http://www.w3.org/ns/shacl#> ."
        " @prefix food: <http://example.org/food#> ."
        " food:AllergenShape a sh:NodeShape ;"
        " sh:targetNode food:lasagna ;"
        " sh:sparql ["
        "  sh:message \"Recipe carries an allergen\" ;"
        "  sh:select \"PREFIX food: <http://example.org/food#> SELECT $this ?value"
        " WHERE { $this food:hasAllergen ?value . }\" ] ."
    ))
    before = next(x["tripleCount"] for x in server.graph.list() if x["graphName"] == g)
    report = server.graph.validateWithRules(g, PROPAGATION_RULE)
    assert any(v["severity"] == "Violation"
               and v["value"] == "http://example.org/food#allergen_milk"
               for v in report)
    after = next(x["tripleCount"] for x in server.graph.list() if x["graphName"] == g)
    assert before == after  # ephemeral: nothing materialized

    # 7. explain — derivation trace for the propagated allergen, graph untouched
    steps = server.graph.explain(
        g,
        "http://example.org/food#lasagna",
        "http://example.org/food#hasAllergen",
        "http://example.org/food#allergen_milk",
        rules=PROPAGATION_RULE,
    )
    assert steps[0]["kind"] == "derived"
    assert "allergenPropagation" in steps[0]["rule"]
    assert any(s["kind"] == "asserted" for s in steps)

    # 8. drop
    assert server.graph.drop(g)["status"] == "dropped"


def test_error_surfaces_as_exception(server):
    from n20s import N20sError
    with pytest.raises(N20sError, match="not found|Graph"):
        server.graph.query("does_not_exist", "SELECT ?x WHERE { ?x ?p ?o }")


@pytest.mark.skipif(
    not os.environ.get("NEO4J_URI"),
    reason="set NEO4J_URI / NEO4J_USER / NEO4J_PASSWORD for the Bolt round-trip test",
)
def test_bolt_fetch_roundtrip(server):
    from neo4j import GraphDatabase

    driver = GraphDatabase.driver(
        os.environ["NEO4J_URI"],
        auth=(os.environ.get("NEO4J_USER", "neo4j"), os.environ.get("NEO4J_PASSWORD", "")),
    )
    n20s = N20s(server._url, driver=driver)
    g = "bolt_roundtrip_py"
    try:
        with driver.session() as s:
            s.run("CREATE (:PyChefThing {id: 'x', props: ['a', 'b']})")
        result = n20s.graph.projectTemplate(
            g,
            {"subject": "http://ex.org#{id}",
             "triples": [{"predicate": "http://ex.org#p",
                          "object": "http://ex.org#{props}", "kind": "iri"}]},
            cypher="MATCH (n:PyChefThing) RETURN n",
        )
        assert result["tripleCount"] == 2
    finally:
        with driver.session() as s:
            s.run("MATCH (n:PyChefThing) DETACH DELETE n")
        n20s.graph.drop(g)
        driver.close()
