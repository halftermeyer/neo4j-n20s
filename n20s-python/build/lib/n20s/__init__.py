"""n20s Python client — the Cypher API of n20s, over the HTTP sidecar.

Usage:
    from n20s import N20s

    n20s = N20s("http://localhost:7475", driver=neo4j_driver)

    n20s.graph.projectTemplate(
        "check_lasagna", template,
        cypher="MATCH (:Recipe {id:$id})-[:CONTAINS*0..]->(n) RETURN n",
        params={"id": "lasagna"},
    )
    n20s.graph.query("check_lasagna", "SELECT ?x WHERE { ... }", profile="RDFS")
    n20s.graph.validate("check_lasagna")
    n20s.graph.drop("check_lasagna")
"""

from .client import N20s, N20sError

__all__ = ["N20s", "N20sError"]
