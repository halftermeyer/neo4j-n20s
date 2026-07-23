"""Python client for n20s — mirrors the Cypher API over the HTTP sidecar.

The client is the middleware layer: it fetches rows over Bolt (optional),
converts graph entities to the canonical row shapes the template engine
expects, and talks to n20s-server over HTTP. Method names deliberately
mirror the Cypher procedures (``n20s.graph.projectTemplate`` ↔
``n20s.graph.projectTemplate(...)``), in the spirit of the GDS Python client.

Canonical row conversions (identical to the Neo4j plugin's):

    node          → {…props, "_labels": […], "_elementId": "…"}
    relationship  → {…props, "_type": "…", "_elementId": "…",
                     "_start": <node row>, "_end": <node row>}
    path          → positional row {"_0": …, "_1": …} (node, rel, node, …)
    map / record  → keys preserved; entity values converted recursively
"""

from __future__ import annotations

import json
from typing import Any, Iterable, Mapping, Optional, Sequence, Union

import requests

RESERVED_NODE_KEYS = ("_labels", "_elementId")
RESERVED_REL_KEYS = ("_type", "_elementId", "_start", "_end")


class N20sError(RuntimeError):
    """Raised when n20s-server returns an error, or on client misuse."""


# ── Entity → row conversion (mirrors the plugin's toRow) ──────────

def _is_node(v: Any) -> bool:
    return hasattr(v, "labels") and hasattr(v, "element_id") and hasattr(v, "items")


def _is_relationship(v: Any) -> bool:
    return hasattr(v, "type") and hasattr(v, "start_node") and hasattr(v, "end_node")


def _is_path(v: Any) -> bool:
    return hasattr(v, "nodes") and hasattr(v, "relationships") and not hasattr(v, "items")


def node_row(node: Any) -> dict:
    row = dict(node.items())
    for key in RESERVED_NODE_KEYS:
        if key in row:
            raise N20sError(
                f"Node property collides with reserved template key '{key}'."
                " Rename the property or project a map instead."
            )
    row["_labels"] = list(node.labels)
    row["_elementId"] = node.element_id
    return row


def relationship_row(rel: Any) -> dict:
    row = dict(rel.items())
    for key in RESERVED_REL_KEYS:
        if key in row:
            raise N20sError(
                f"Relationship property collides with reserved template key '{key}'."
                " Rename the property or project a map instead."
            )
    row["_type"] = rel.type
    row["_elementId"] = rel.element_id
    row["_start"] = node_row(rel.start_node)
    row["_end"] = node_row(rel.end_node)
    return row


def convert(value: Any) -> Any:
    """Convert a driver value to template-engine row material, recursively."""
    if _is_node(value):
        return node_row(value)
    if _is_relationship(value):
        return relationship_row(value)
    if _is_path(value):
        row: dict = {}
        i = 0
        nodes = list(value.nodes)
        rels = list(value.relationships)
        for idx, n in enumerate(nodes):
            row[f"_{i}"] = node_row(n)
            i += 1
            if idx < len(rels):
                row[f"_{i}"] = relationship_row(rels[idx])
                i += 1
        return row
    if isinstance(value, Mapping):
        return {str(k): convert(v) for k, v in value.items()}
    if isinstance(value, (list, tuple)):
        return [convert(v) for v in value]
    return value


def record_row(record: Any) -> dict:
    """Convert one driver record to a template row.

    Single column → the converted value is the row (must be a node,
    relationship, path, or map). Multiple columns → a named map, one key per
    column — the recommended shape (e.g. ``RETURN s, r, t`` → ``{s, r, t}``).
    """
    keys = list(record.keys())
    if len(keys) == 1:
        row = convert(record[keys[0]])
        if isinstance(row, list):  # entity list → positional row
            return {f"_{i}": v for i, v in enumerate(row)}
        if not isinstance(row, dict):
            raise N20sError(
                "A single-column row must be a node, relationship, path, list,"
                f" or map — got {type(record[keys[0]]).__name__}."
                " Return multiple columns for a named map."
            )
        return row
    return {k: convert(record[k]) for k in keys}


# ── HTTP client ────────────────────────────────────────────────────

class _GraphAPI:
    """``n20s.graph.*`` — mirrors the Cypher procedures and functions."""

    def __init__(self, client: "N20s"):
        self._c = client

    # — projection —

    def projectTemplate(
        self,
        name: str,
        template: Union[str, Mapping],
        rows: Optional[Sequence[Mapping]] = None,
        *,
        cypher: Optional[str] = None,
        params: Optional[Mapping] = None,
        ifExists: str = "replace",
    ) -> dict:
        """Project rows into a named graph via a JSON template.

        Provide either ``rows`` (pre-built row maps) or ``cypher`` (+
        ``params``): the client runs the Cypher over Bolt and converts each
        record to the canonical row shape.
        """
        if (rows is None) == (cypher is None):
            raise N20sError("Provide exactly one of 'rows' or 'cypher'.")
        if cypher is not None:
            rows = self._c._fetch_rows(cypher, params)
            if not rows:
                raise N20sError(
                    "The scoping Cypher returned no rows — nothing to project."
                    " Check that the data exists in the database and the"
                    f" parameters match. Query: {cypher.strip()!r}"
                )
        body = {"template": template, "rows": list(rows or []), "ifExists": ifExists}
        return self._c._post(f"/graph/{name}/projectTemplate", body)

    def addTurtle(
        self,
        name: str,
        turtle: Union[str, Sequence[str]],
        ifExists: str = "append",
    ) -> dict:
        body: dict = {"ifExists": ifExists}
        if isinstance(turtle, str):
            body["turtle"] = turtle
        else:
            body["turtles"] = list(turtle)
        return self._c._post(f"/graph/{name}/turtle", body)

    def project(
        self,
        name: str,
        triples: Iterable[Sequence[str]],
        ifExists: str = "replace",
    ) -> dict:
        body = [{"s": s, "p": p, "o": o} for s, p, o in triples]
        return self._c._post(f"/graph/{name}/triples?ifExists={ifExists}", body)

    # — reasoning —

    def query(self, name: str, sparql: str, profile: str = "") -> list:
        rows = self._c._post(f"/graph/{name}/query", {"sparql": sparql, "profile": profile})
        return [r["row"] for r in rows]

    def queryWithRules(self, name: str, sparql: str, rules: str, profile: str = "") -> list:
        rows = self._c._post(
            f"/graph/{name}/queryWithRules",
            {"sparql": sparql, "rules": rules, "profile": profile},
        )
        return [r["row"] for r in rows]

    def construct(self, name: str, sparql: str) -> list:
        return self._c._post(f"/graph/{name}/construct", {"sparql": sparql})

    def infer(self, name: str, profile: str) -> dict:
        return self._c._post(f"/graph/{name}/infer", {"profile": profile})

    def inferWithRules(self, name: str, rules: str, profile: str = "") -> dict:
        return self._c._post(
            f"/graph/{name}/inferWithRules", {"rules": rules, "profile": profile}
        )

    def validate(self, name: str) -> list:
        return self._c._post(f"/graph/{name}/validate", None)

    def validateWithRules(self, name: str, rules: str = "", profile: str = "") -> list:
        """SHACL validation with ephemeral inference — the graph is never modified."""
        return self._c._post(
            f"/graph/{name}/validateWithRules", {"rules": rules, "profile": profile}
        )

    def explain(self, name: str, s: str, p: str, o: str,
                rules: str = "", profile: str = "") -> list:
        """Derivation trace for an entailed statement — depth-first steps of kind
        'derived' (with the rule), 'asserted', or 'axiom'. Never modifies the graph."""
        return self._c._post(
            f"/graph/{name}/explain",
            {"s": s, "p": p, "o": o, "rules": rules, "profile": profile},
        )

    # — export & management —

    def toTurtle(self, name: str) -> dict:
        return self._c._get(f"/graph/{name}/turtle")

    def triples(self, name: str) -> list:
        return self._c._get(f"/graph/{name}/triples")

    def list(self) -> list:
        return self._c._get("/graph")

    def drop(self, name: str) -> dict:
        return self._c._delete(f"/graph/{name}")


class N20s:
    """Client for n20s-server, mirroring the Cypher API.

    Args:
        url: base URL of a running n20s-server (default ``http://localhost:7475``).
        driver: optional ``neo4j.Driver`` — enables ``cypher=`` fetches in
            ``graph.projectTemplate`` (the client acts as the Bolt→RDF middleware).
        database: optional Neo4j database name for Bolt sessions.
    """

    def __init__(
        self,
        url: str = "http://localhost:7475",
        driver: Any = None,
        database: Optional[str] = None,
        timeout: float = 30.0,
    ):
        self._url = url.rstrip("/")
        self._driver = driver
        self._database = database
        self._timeout = timeout
        self.graph = _GraphAPI(self)

    def version(self) -> dict:
        return self._get("/version")

    # — Bolt fetch —

    def _fetch_rows(self, cypher: str, params: Optional[Mapping]) -> list:
        if self._driver is None:
            raise N20sError(
                "No Bolt driver configured — pass driver=GraphDatabase.driver(...)"
                " to N20s(), or provide pre-built 'rows'."
            )
        with self._driver.session(database=self._database) as session:
            result = session.run(cypher, dict(params or {}))
            return [record_row(rec) for rec in result]

    # — HTTP plumbing —

    def _request(self, method: str, path: str, body: Any = None) -> Any:
        resp = requests.request(
            method,
            self._url + path,
            json=body,
            timeout=self._timeout,
        )
        if resp.status_code >= 400:
            try:
                message = resp.json().get("error", resp.text)
            except (ValueError, AttributeError):
                message = resp.text
            raise N20sError(f"{method} {path} → {resp.status_code}: {message}")
        return resp.json()

    def _get(self, path: str) -> Any:
        return self._request("GET", path)

    def _post(self, path: str, body: Any) -> Any:
        return self._request("POST", path, body)

    def _delete(self, path: str) -> Any:
        return self._request("DELETE", path)
