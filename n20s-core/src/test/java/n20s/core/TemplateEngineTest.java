package n20s.core;

import org.junit.jupiter.api.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class TemplateEngineTest {

    @AfterEach
    void cleanup() {
        GraphCatalog.list().forEach(GraphCatalog::drop);
    }

    private static final String THING_TEMPLATE = """
            {
              "subject": "http://example.com#thing_{id}",
              "triples": [
                { "predicate": "http://example.com#has_prop",
                  "object": "http://example.com#{prop}",
                  "kind": "iri" }
              ]
            }
            """;

    // ── list fan-out ────────────────────────────────────────────

    @Test
    void testListFanOut() {
        var result = GraphEngine.projectTemplate("tpl", THING_TEMPLATE,
                List.of(Map.of("id", "id", "prop", List.of("p1", "p2", "p3"))));

        assertEquals(1, result.rows);
        assertEquals(3, result.tripleCount);

        var triples = GraphEngine.triples("tpl");
        assertEquals(3, triples.size());
        assertTrue(triples.stream().allMatch(t -> t.subject.equals("http://example.com#thing_id")));
        assertTrue(triples.stream().allMatch(t -> t.predicate.equals("http://example.com#has_prop")));
        assertTrue(triples.stream().anyMatch(t -> t.object.equals("http://example.com#p2")));
    }

    @Test
    void testScalarValue_singleTriple() {
        var result = GraphEngine.projectTemplate("tpl", THING_TEMPLATE,
                List.of(Map.of("id", "1", "prop", "solo")));

        assertEquals(1, result.tripleCount);
        assertEquals("http://example.com#solo", GraphEngine.triples("tpl").get(0).object);
    }

    // ── filters ─────────────────────────────────────────────────

    @Test
    void testLabelsFanOut_excludeFilter() {
        String tpl = """
                {
                  "subject": "http://ex.org#n_{id}",
                  "triples": [
                    { "predicate": "http://www.w3.org/1999/02/22-rdf-syntax-ns#type",
                      "object": "http://ex.org#{_labels}",
                      "kind": "iri",
                      "exclude": ["_Imported"] }
                  ]
                }
                """;
        var result = GraphEngine.projectTemplate("g", tpl,
                List.of(Map.of("id", "1", "_labels", List.of("Thing", "Ingredient", "_Imported"))));

        assertEquals(2, result.tripleCount);
        assertTrue(GraphEngine.triples("g").stream()
                .noneMatch(t -> t.object.contains("_Imported")));
    }

    @Test
    void testIncludeFilter() {
        var result = GraphEngine.projectTemplate("g", """
                {
                  "subject": "http://ex.org#n_{id}",
                  "triples": [
                    { "predicate": "http://ex.org#p",
                      "object": "http://ex.org#{prop}",
                      "kind": "iri",
                      "include": ["a", "c"] }
                  ]
                }
                """,
                List.of(Map.of("id", "1", "prop", List.of("a", "b", "c", "d"))));

        assertEquals(2, result.tripleCount);
    }

    // ── map: rename + filter ────────────────────────────────────

    @Test
    void testMap_renamesAndFilters() {
        String tpl = """
                {
                  "subject": "http://ex.org#n_{id}",
                  "triples": [
                    { "predicate": "http://www.w3.org/1999/02/22-rdf-syntax-ns#type",
                      "object": { "from": "_labels", "map": {
                          "Thing": "http://ex.org#Thing",
                          "Ingredient": "http://example.org/cosmo#Ingredient"
                      }},
                      "kind": "iri" }
                  ]
                }
                """;
        var result = GraphEngine.projectTemplate("g", tpl,
                List.of(Map.of("id", "1", "_labels", List.of("Thing", "Ingredient", "_Imported"))));

        assertEquals(2, result.tripleCount);
        var triples = GraphEngine.triples("g");
        assertTrue(triples.stream().anyMatch(t -> t.object.equals("http://example.org/cosmo#Ingredient")));
        assertTrue(triples.stream().noneMatch(t -> t.object.contains("_Imported")));
    }

    @Test
    void testMap_scalarSource() {
        var result = GraphEngine.projectTemplate("g", """
                {
                  "subject": "http://ex.org#n_{id}",
                  "triples": [
                    { "predicate": "http://ex.org#status",
                      "object": { "from": "state", "map": { "ok": "http://ex.org#Valid" } },
                      "kind": "iri" }
                  ]
                }
                """,
                List.of(Map.of("id", "1", "state", "ok")));

        assertEquals(1, result.tripleCount);
        assertEquals("http://ex.org#Valid", GraphEngine.triples("g").get(0).object);
    }

    // ── skip semantics ──────────────────────────────────────────

    @Test
    void testMissingProperty_skipsPatternNotRow() {
        String tpl = """
                {
                  "subject": "http://ex.org#n_{id}",
                  "triples": [
                    { "predicate": "http://ex.org#name", "object": "{name}" },
                    { "predicate": "http://ex.org#missing", "object": "{nope}" }
                  ]
                }
                """;
        var result = GraphEngine.projectTemplate("g", tpl,
                List.of(Map.of("id", "1", "name", "Retinol")));

        assertEquals(1, result.tripleCount); // name emitted, nope skipped silently
    }

    @Test
    void testMissingSubjectPlaceholder_skipsRow() {
        var result = GraphEngine.projectTemplate("g", THING_TEMPLATE, List.of(
                Map.of("prop", List.of("p1")),               // no id → skipped
                Map.of("id", "2", "prop", List.of("p1"))));

        assertEquals(2, result.rows);
        assertEquals(1, result.tripleCount);
    }

    // ── errors ──────────────────────────────────────────────────

    @Test
    void testTwoListsInOnePattern_throws() {
        var ex = assertThrows(RuntimeException.class, () ->
                GraphEngine.projectTemplate("g", """
                        {
                          "subject": "http://ex.org#n_{id}",
                          "triples": [
                            { "predicate": "http://ex.org#p", "object": "http://ex.org#{a}_{b}", "kind": "iri" }
                          ]
                        }
                        """,
                        List.of(Map.of("id", "1", "a", List.of("x"), "b", List.of("y")))));
        assertTrue(ex.getMessage().contains("at most one list"));
    }

    @Test
    void testListInSubject_throws() {
        var ex = assertThrows(RuntimeException.class, () ->
                GraphEngine.projectTemplate("g", THING_TEMPLATE,
                        List.of(Map.of("id", List.of("a", "b"), "prop", "x"))));
        assertTrue(ex.getMessage().contains("object position"));
    }

    @Test
    void testInvalidTemplateJson_throws() {
        var ex = assertThrows(RuntimeException.class, () ->
                GraphEngine.projectTemplate("g", "not json {", List.of(Map.of("id", "1"))));
        assertTrue(ex.getMessage().contains("Invalid template JSON"));
    }

    // ── literals & datatypes ────────────────────────────────────

    @Test
    void testNativeTypedLiteral() {
        var result = GraphEngine.projectTemplate("g", """
                {
                  "subject": "http://ex.org#n_{id}",
                  "triples": [
                    { "predicate": "http://ex.org#age", "object": "{age}" },
                    { "predicate": "http://ex.org#name", "object": "{name}" }
                  ]
                }
                """,
                List.of(Map.of("id", "1", "age", 42L, "name", "Retinol")));

        assertEquals(2, result.tripleCount);
        var triples = GraphEngine.triples("g");
        assertTrue(triples.stream().anyMatch(t ->
                t.object.startsWith("\"42\"^^") && t.object.contains("long")));
        // Jena 6 / RDF 1.1: plain literals are xsd:string-typed
        assertTrue(triples.stream().anyMatch(t -> t.object.startsWith("\"Retinol\"")));
    }

    @Test
    void testExplicitDatatype() {
        var result = GraphEngine.projectTemplate("g", """
                {
                  "subject": "http://ex.org#n_{id}",
                  "triples": [
                    { "predicate": "http://ex.org#max",
                      "object": "{max}",
                      "datatype": "http://www.w3.org/2001/XMLSchema#decimal" }
                  ]
                }
                """,
                List.of(Map.of("id", "1", "max", "0.05")));

        assertEquals(1, result.tripleCount);
        assertTrue(GraphEngine.triples("g").get(0).object
                .equals("\"0.05\"^^<http://www.w3.org/2001/XMLSchema#decimal>"));
    }

    // ── IRI safety ──────────────────────────────────────────────

    @Test
    void testIriEncoding() {
        var result = GraphEngine.projectTemplate("g", THING_TEMPLATE,
                List.of(Map.of("id", "my id", "prop", List.of("a/b"))));

        var triples = GraphEngine.triples("g");
        assertEquals("http://example.com#thing_my%20id", triples.get(0).subject);
        assertEquals("http://example.com#a%2Fb", triples.get(0).object);
    }

    // ── arrays (Neo4j property style) ───────────────────────────

    @Test
    void testArrayValue_fansOutLikeList() {
        Map<String, Object> row = new HashMap<>();
        row.put("id", "1");
        row.put("prop", new String[]{"p1", "p2"});

        var result = GraphEngine.projectTemplate("g", THING_TEMPLATE, List.of(row));
        assertEquals(2, result.tripleCount);
    }

    // ── dotted placeholders ─────────────────────────────────────

    private static final String REL_TEMPLATE = """
            {
              "subject": "http://ex.org#thing_{_0.id}",
              "triples": [
                { "predicate": { "from": "_1._type", "map": {
                    "RELATES_TO": "http://ex.org#relatesTo"
                  }},
                  "object": "http://ex.org#other_{_2.id}",
                  "kind": "iri" }
              ]
            }
            """;

    @Test
    void testDottedPlaceholders_positionalEntityRow() {
        var result = GraphEngine.projectTemplate("g", REL_TEMPLATE, List.of(Map.of(
                "_0", Map.of("id", "s1"),
                "_1", Map.of("_type", "RELATES_TO"),
                "_2", Map.of("id", "t1"))));

        assertEquals(1, result.tripleCount);
        var t = GraphEngine.triples("g").get(0);
        assertEquals("http://ex.org#thing_s1", t.subject);
        assertEquals("http://ex.org#relatesTo", t.predicate);
        assertEquals("http://ex.org#other_t1", t.object);
    }

    @Test
    void testPredicateMap_unmappedType_skipsPattern() {
        var result = GraphEngine.projectTemplate("g", REL_TEMPLATE, List.of(Map.of(
                "_0", Map.of("id", "s1"),
                "_1", Map.of("_type", "IGNORED_TYPE"),
                "_2", Map.of("id", "t1"))));

        assertEquals(1, result.rows);
        assertEquals(0, result.tripleCount); // type absent from predicate map → skipped
    }

    @Test
    void testDottedFanOut_listOfMaps() {
        var result = GraphEngine.projectTemplate("g", """
                {
                  "subject": "http://ex.org#prod_{id}",
                  "triples": [
                    { "predicate": "http://ex.org#contains",
                      "object": "http://ex.org#ing_{ingredients.id}",
                      "kind": "iri" }
                  ]
                }
                """,
                List.of(Map.of("id", "p1", "ingredients",
                        List.of(Map.of("id", "retinol"), Map.of("id", "aqua")))));

        assertEquals(2, result.tripleCount);
        var triples = GraphEngine.triples("g");
        assertTrue(triples.stream().anyMatch(t -> t.object.equals("http://ex.org#ing_retinol")));
        assertTrue(triples.stream().anyMatch(t -> t.object.equals("http://ex.org#ing_aqua")));
    }

    @Test
    void testDottedFanOut_twoPlaceholdersSameList() {
        var result = GraphEngine.projectTemplate("g", """
                {
                  "subject": "http://ex.org#prod_{id}",
                  "triples": [
                    { "predicate": "http://ex.org#batch",
                      "object": "{ingredients.id}-{ingredients.conc}" }
                  ]
                }
                """,
                List.of(Map.of("id", "p1", "ingredients",
                        List.of(Map.of("id", "retinol", "conc", "low"),
                                Map.of("id", "aqua", "conc", "high")))));

        assertEquals(2, result.tripleCount); // same list → same pinned element, no cartesian
        assertTrue(GraphEngine.triples("g").stream()
                .anyMatch(t -> t.object.startsWith("\"retinol-low\"")));
    }

    @Test
    void testMapValuedPlaceholderWithoutDot_throws() {
        var ex = assertThrows(RuntimeException.class, () ->
                GraphEngine.projectTemplate("g", """
                        {
                          "subject": "http://ex.org#n_{_0}",
                          "triples": [
                            { "predicate": "http://ex.org#p", "object": "{v}" }
                          ]
                        }
                        """,
                        List.of(Map.of("_0", Map.of("id", "s1"), "v", "x"))));
        assertTrue(ex.getMessage().contains("resolves to a map"));
    }

    @Test
    void testMissingNestedKey_skipsPattern() {
        var result = GraphEngine.projectTemplate("g", """
                {
                  "subject": "http://ex.org#n_{_0.id}",
                  "triples": [
                    { "predicate": "http://ex.org#p", "object": "{_0.grade}" },
                    { "predicate": "http://ex.org#q", "object": "{_0.id}" }
                  ]
                }
                """,
                List.of(Map.of("_0", Map.of("id", "s1"))));

        assertEquals(1, result.tripleCount); // grade missing → pattern skipped, id emitted
    }

    @Test
    void testFiltersOnMapElements_throw() {
        var ex = assertThrows(RuntimeException.class, () ->
                GraphEngine.projectTemplate("g", """
                        {
                          "subject": "http://ex.org#prod_{id}",
                          "triples": [
                            { "predicate": "http://ex.org#contains",
                              "object": "http://ex.org#ing_{ingredients.id}",
                              "kind": "iri",
                              "exclude": ["aqua"] }
                          ]
                        }
                        """,
                        List.of(Map.of("id", "p1", "ingredients",
                                List.of(Map.of("id", "retinol"))))));
        assertTrue(ex.getMessage().contains("do not apply to map fan-out elements"));
    }

    @Test
    void testDottedNativeTypedLiteral() {
        var result = GraphEngine.projectTemplate("g", """
                {
                  "subject": "http://ex.org#rel_{_1._elementId}",
                  "triples": [
                    { "predicate": "http://ex.org#weight", "object": "{_1.weight}" }
                  ]
                }
                """,
                List.of(Map.of("_1", Map.of("_elementId", "e1", "weight", 42L))));

        assertEquals(1, result.tripleCount);
        assertTrue(GraphEngine.triples("g").get(0).object.startsWith("\"42\"^^"));
    }
}
