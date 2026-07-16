"""Unit tests for entity → row conversion (mirrors the plugin's toRow)."""

import pytest

from n20s.client import N20sError, convert, node_row, record_row, relationship_row


# ── driver-shaped stubs ─────────────────────────────────────────────

class StubNode:
    def __init__(self, element_id, labels, props):
        self.element_id = element_id
        self.labels = frozenset(labels)
        self._props = props

    def items(self):
        return self._props.items()


class StubRel:
    def __init__(self, element_id, type_, start, end, props=None):
        self.element_id = element_id
        self.type = type_
        self.start_node = start
        self.end_node = end
        self._props = props or {}

    def items(self):
        return self._props.items()


class StubPath:
    def __init__(self, nodes, relationships):
        self.nodes = nodes
        self.relationships = relationships


class StubRecord:
    def __init__(self, data):
        self._data = data

    def keys(self):
        return list(self._data.keys())

    def __getitem__(self, key):
        return self._data[key]


BUTTER = StubNode("4:x:1", ["Ingredient", "Dairy"], {"id": "butter", "allergens": ["milk"]})
LASAGNA = StubNode("4:x:2", ["Recipe"], {"id": "lasagna"})
CONTAINS = StubRel("5:x:1", "CONTAINS", LASAGNA, BUTTER, {"qty": 0.2})


# ── nodes ───────────────────────────────────────────────────────────

def test_node_row():
    row = node_row(BUTTER)
    assert row["id"] == "butter"
    assert row["allergens"] == ["milk"]
    assert sorted(row["_labels"]) == ["Dairy", "Ingredient"]
    assert row["_elementId"] == "4:x:1"


def test_node_reserved_key_collision():
    bad = StubNode("4:x:9", ["Thing"], {"_labels": "boom"})
    with pytest.raises(N20sError, match="reserved"):
        node_row(bad)


# ── relationships ───────────────────────────────────────────────────

def test_relationship_row_self_contained():
    row = relationship_row(CONTAINS)
    assert row["_type"] == "CONTAINS"
    assert row["qty"] == 0.2
    assert row["_start"]["id"] == "lasagna"
    assert row["_end"]["id"] == "butter"


# ── paths ───────────────────────────────────────────────────────────

def test_path_positional():
    path = StubPath([LASAGNA, BUTTER], [CONTAINS])
    row = convert(path)
    assert row["_0"]["id"] == "lasagna"
    assert row["_1"]["_type"] == "CONTAINS"
    assert row["_2"]["id"] == "butter"


# ── records ─────────────────────────────────────────────────────────

def test_record_single_node_column():
    row = record_row(StubRecord({"n": BUTTER}))
    assert row["id"] == "butter"
    assert "_labels" in row


def test_record_named_map():
    row = record_row(StubRecord({"s": LASAGNA, "r": CONTAINS, "t": BUTTER}))
    assert row["s"]["id"] == "lasagna"
    assert row["r"]["_type"] == "CONTAINS"
    assert row["t"]["id"] == "butter"


def test_record_scalar_single_column_rejected():
    with pytest.raises(N20sError, match="single-column"):
        record_row(StubRecord({"x": 42}))


def test_record_entity_list_becomes_positional():
    row = record_row(StubRecord({"triple": [LASAGNA, CONTAINS, BUTTER]}))
    assert row["_0"]["id"] == "lasagna"
    assert row["_1"]["_type"] == "CONTAINS"
    assert row["_2"]["id"] == "butter"


def test_nested_map_converts_entities():
    row = record_row(StubRecord({"row": {"who": BUTTER, "score": 1}}))
    assert row["who"]["id"] == "butter"
    assert row["score"] == 1
