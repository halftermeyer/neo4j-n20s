package n20s.core;

import n20s.core.model.*;
import org.junit.jupiter.api.*;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class GraphEngineTest {

    @AfterEach
    void cleanup() {
        GraphCatalog.list().forEach(GraphCatalog::drop);
    }

    // ── addTurtle + query ───────────────────────────────────────

    @Test
    void testAddTurtleAndQuery() {
        GraphEngine.addTurtle("test", """
                @prefix ex: <http://ex.org/> .
                ex:Zeus a ex:God .
                ex:Athena a ex:God .
                """);

        var results = GraphEngine.query("test",
                "SELECT ?x WHERE { ?x <http://www.w3.org/1999/02/22-rdf-syntax-ns#type> <http://ex.org/God> }", "");
        assertEquals(2, results.size());
    }

    @Test
    void testAddTurtle_incrementalAdd() {
        var r1 = GraphEngine.addTurtle("incr", "@prefix ex: <http://ex.org/> . ex:Zeus a ex:God .");
        assertEquals(0, r1.triplesBefore);
        assertEquals(1, r1.triplesAfter);

        var r2 = GraphEngine.addTurtle("incr", """
                @prefix ex: <http://ex.org/> .
                ex:Athena a ex:God .
                ex:Hercules a ex:Hero .
                """);
        assertEquals(1, r2.triplesBefore);
        assertEquals(3, r2.triplesAfter);
        assertEquals(2, r2.added);
    }

    @Test
    void testAddTurtle_typedLiterals() {
        GraphEngine.addTurtle("lit", """
                @prefix ex: <http://ex.org/> .
                @prefix xsd: <http://www.w3.org/2001/XMLSchema#> .
                ex:Retinol ex:maxConcentration "0.05"^^xsd:decimal ;
                           ex:name "Retinol"@en .
                """);

        var results = GraphEngine.query("lit",
                "SELECT ?val WHERE { <http://ex.org/Retinol> <http://ex.org/maxConcentration> ?val }", "");
        assertEquals(1, results.size());
    }

    @Test
    void testAddTurtle_invalidSyntax() {
        assertThrows(RuntimeException.class, () ->
                GraphEngine.addTurtle("bad", "this is not valid turtle @#$%"));
    }

    // ── infer (forward chaining) ────────────────────────────────

    @Test
    void testInfer_rdfsSubClassOf() {
        GraphEngine.addTurtle("infer", """
                @prefix ex: <http://ex.org/> .
                @prefix rdfs: <http://www.w3.org/2000/01/rdf-schema#> .
                ex:Zeus a ex:God .
                ex:God rdfs:subClassOf ex:Being .
                """);

        var result = GraphEngine.infer("infer", "RDFS");
        assertEquals(2, result.triplesBefore);
        assertTrue(result.newTriples > 0);

        var beings = GraphEngine.query("infer",
                "SELECT ?x WHERE { ?x <http://www.w3.org/1999/02/22-rdf-syntax-ns#type> <http://ex.org/Being> }", "");
        assertTrue(beings.stream().anyMatch(r -> r.row.get("x").toString().contains("Zeus")));
    }

    @Test
    void testInfer_owlMicro() {
        GraphEngine.addTurtle("owl", """
                @prefix ex: <http://ex.org/> .
                @prefix rdfs: <http://www.w3.org/2000/01/rdf-schema#> .
                ex:Zeus a ex:God .
                ex:God rdfs:subClassOf ex:Immortal .
                ex:Immortal rdfs:subClassOf ex:Being .
                """);

        GraphEngine.infer("owl", "OWL_MICRO");

        var beings = GraphEngine.query("owl",
                "SELECT ?x WHERE { ?x <http://www.w3.org/1999/02/22-rdf-syntax-ns#type> <http://ex.org/Being> }", "");
        assertTrue(beings.stream().anyMatch(r -> r.row.get("x").toString().contains("Zeus")));
    }

    @Test
    void testInfer_invalidProfile() {
        GraphEngine.addTurtle("err", "@prefix ex: <http://ex.org/> . ex:Zeus a ex:God .");
        var ex = assertThrows(RuntimeException.class, () -> GraphEngine.infer("err", "INVALID_PROFILE"));
        assertTrue(ex.getMessage().contains("Unknown reasoning profile"));
    }

    // ── inferWithRules ──────────────────────────────────────────

    @Test
    void testInferWithRules_basic() {
        GraphEngine.addTurtle("rules", """
                @prefix ex: <http://ex.org/> .
                ex:Zeus a ex:God .
                ex:Athena a ex:God .
                ex:Hercules a ex:Hero .
                """);

        var result = GraphEngine.inferWithRules("rules", """
                [godToBeing: (?x http://www.w3.org/1999/02/22-rdf-syntax-ns#type http://ex.org/God)
                    -> (?x http://www.w3.org/1999/02/22-rdf-syntax-ns#type http://ex.org/Being)]
                [heroToBeing: (?x http://www.w3.org/1999/02/22-rdf-syntax-ns#type http://ex.org/Hero)
                    -> (?x http://www.w3.org/1999/02/22-rdf-syntax-ns#type http://ex.org/Being)]
                """, "");

        assertTrue(result.newTriples >= 3);
        assertTrue(result.profile.contains("2 rules"));

        var beings = GraphEngine.query("rules",
                "SELECT ?x WHERE { ?x <http://www.w3.org/1999/02/22-rdf-syntax-ns#type> <http://ex.org/Being> }", "");
        assertEquals(3, beings.size());
    }

    @Test
    void testInferWithRules_withBuiltins() {
        GraphEngine.addTurtle("builtin", """
                @prefix ex: <http://ex.org/> .
                @prefix xsd: <http://www.w3.org/2001/XMLSchema#> .
                ex:Alice a ex:Person ; ex:age 25 .
                ex:Bob a ex:Person ; ex:age 15 .
                """);

        GraphEngine.inferWithRules("builtin", """
                [adult: (?x http://www.w3.org/1999/02/22-rdf-syntax-ns#type http://ex.org/Person)
                        (?x http://ex.org/age ?age)
                        greaterThan(?age, 17)
                    -> (?x http://www.w3.org/1999/02/22-rdf-syntax-ns#type http://ex.org/Adult)]
                """, "");

        var adults = GraphEngine.query("builtin",
                "SELECT ?x WHERE { ?x <http://www.w3.org/1999/02/22-rdf-syntax-ns#type> <http://ex.org/Adult> }", "");
        assertEquals(1, adults.size());
        assertTrue(adults.get(0).row.get("x").toString().contains("Alice"));
    }

    @Test
    void testInferWithRules_invalidSyntax() {
        GraphEngine.addTurtle("bad_rules", "@prefix ex: <http://ex.org/> . ex:Zeus a ex:God .");
        assertThrows(RuntimeException.class, () ->
                GraphEngine.inferWithRules("bad_rules", "not valid rule syntax!!!", ""));
    }

    // ── queryWithRules (backward chaining) ──────────────────────

    @Test
    void testQueryWithRules() {
        GraphEngine.addTurtle("qr", """
                @prefix ex: <http://ex.org/> .
                ex:Zeus a ex:God .
                ex:Athena a ex:God .
                ex:Hercules a ex:Hero .
                """);

        var results = GraphEngine.queryWithRules("qr",
                "SELECT ?x WHERE { ?x <http://www.w3.org/1999/02/22-rdf-syntax-ns#type> <http://ex.org/Being> }",
                """
                [godBeing: (?x http://www.w3.org/1999/02/22-rdf-syntax-ns#type http://ex.org/God)
                    -> (?x http://www.w3.org/1999/02/22-rdf-syntax-ns#type http://ex.org/Being)]
                [heroBeing: (?x http://www.w3.org/1999/02/22-rdf-syntax-ns#type http://ex.org/Hero)
                    -> (?x http://www.w3.org/1999/02/22-rdf-syntax-ns#type http://ex.org/Being)]
                """, "");

        assertEquals(3, results.size());

        // Verify base model was NOT modified
        var triples = GraphEngine.triples("qr");
        assertEquals(3, triples.size());
    }

    @Test
    void testQueryWithRules_withBuiltins() {
        GraphEngine.addTurtle("qr_b", """
                @prefix ex: <http://ex.org/> .
                ex:Alice a ex:Person ; ex:age 25 .
                ex:Bob a ex:Person ; ex:age 15 .
                """);

        var results = GraphEngine.queryWithRules("qr_b",
                "SELECT ?x WHERE { ?x <http://www.w3.org/1999/02/22-rdf-syntax-ns#type> <http://ex.org/Adult> }",
                """
                [adult: (?x http://www.w3.org/1999/02/22-rdf-syntax-ns#type http://ex.org/Person)
                        (?x http://ex.org/age ?a)
                        greaterThan(?a, 17)
                    -> (?x http://www.w3.org/1999/02/22-rdf-syntax-ns#type http://ex.org/Adult)]
                """, "");

        assertEquals(1, results.size());
        assertTrue(results.get(0).row.get("x").toString().contains("Alice"));
    }

    @Test
    void testQueryWithRules_plusRDFS() {
        GraphEngine.addTurtle("combo", """
                @prefix ex: <http://ex.org/> .
                @prefix rdfs: <http://www.w3.org/2000/01/rdf-schema#> .
                ex:Zeus a ex:God .
                ex:God rdfs:subClassOf ex:Being .
                """);

        // Without RDFS profile
        var noRdfs = GraphEngine.queryWithRules("combo",
                "SELECT ?x WHERE { ?x <http://www.w3.org/1999/02/22-rdf-syntax-ns#type> <http://ex.org/Worshipped> }",
                "[worship: (?x rdf:type http://ex.org/Being) -> (?x rdf:type http://ex.org/Worshipped)]", "");
        assertTrue(noRdfs.isEmpty());

        // With RDFS profile
        var withRdfs = GraphEngine.queryWithRules("combo",
                "SELECT ?x WHERE { ?x <http://www.w3.org/1999/02/22-rdf-syntax-ns#type> <http://ex.org/Worshipped> }",
                "[worship: (?x rdf:type http://ex.org/Being) -> (?x rdf:type http://ex.org/Worshipped)]", "RDFS");
        assertEquals(1, withRdfs.size());
        assertTrue(withRdfs.get(0).row.get("x").toString().contains("Zeus"));

        // Base model unchanged
        assertEquals(2, GraphEngine.triples("combo").size());
    }

    // ── backward chaining ───────────────────────────────────────

    @Test
    void testBackwardChaining() {
        GraphEngine.addTurtle("bc", """
                @prefix ex: <http://ex.org/> .
                @prefix rdfs: <http://www.w3.org/2000/01/rdf-schema#> .
                ex:Zeus a ex:God .
                ex:God rdfs:subClassOf ex:Being .
                """);

        var results = GraphEngine.query("bc",
                "SELECT ?x WHERE { ?x <http://www.w3.org/1999/02/22-rdf-syntax-ns#type> <http://ex.org/Being> }",
                "RDFS");
        assertTrue(results.stream().anyMatch(r -> r.row.get("x").toString().contains("Zeus")));

        // Base model unchanged
        assertEquals(2, GraphEngine.triples("bc").size());
    }

    // ── construct ───────────────────────────────────────────────

    @Test
    void testConstruct() {
        GraphEngine.addTurtle("con", """
                @prefix ex: <http://ex.org/> .
                ex:Zeus a ex:God .
                ex:Athena a ex:God .
                ex:Hercules a ex:Hero .
                """);

        var triples = GraphEngine.construct("con",
                """
                CONSTRUCT { ?x <http://ex.org/is> <http://ex.org/divine> }
                WHERE { ?x <http://www.w3.org/1999/02/22-rdf-syntax-ns#type> <http://ex.org/God> }
                """);
        assertEquals(2, triples.size());
        assertTrue(triples.stream().allMatch(t -> t.predicate.equals("http://ex.org/is")));
    }

    // ── triples ─────────────────────────────────────────────────

    @Test
    void testTriples() {
        GraphEngine.addTurtle("tri", """
                @prefix ex: <http://ex.org/> .
                ex:Zeus a ex:God .
                ex:Zeus ex:name "Zeus"@en .
                """);

        var triples = GraphEngine.triples("tri");
        assertEquals(2, triples.size());

        var nameTriple = triples.stream()
                .filter(t -> t.predicate.contains("name"))
                .findFirst().orElseThrow();
        assertTrue(nameTriple.object.contains("@en"));
    }

    // ── list + drop ─────────────────────────────────────────────

    @Test
    void testListAndDrop() {
        GraphEngine.addTurtle("g1", "@prefix ex: <http://ex.org/> . ex:A a ex:B .");

        var graphs = GraphEngine.list();
        assertTrue(graphs.stream().anyMatch(g -> g.graphName.equals("g1")));

        var dropResult = GraphEngine.drop("g1");
        assertEquals("dropped", dropResult.status);

        assertTrue(GraphEngine.list().isEmpty());
    }

    @Test
    void testDropNonExistent() {
        var result = GraphEngine.drop("no_such_graph");
        assertEquals("not found", result.status);
    }

    // ── toTurtle ────────────────────────────────────────────────

    @Test
    void testToTurtle() {
        GraphEngine.addTurtle("export", """
                @prefix ex: <http://ex.org/> .
                ex:Zeus a ex:God .
                ex:Athena a ex:God .
                """);

        var result = GraphEngine.toTurtle("export");
        assertEquals("export", result.graphName);
        assertEquals(2, result.tripleCount);
        assertTrue(result.turtle.contains("Zeus"));
        assertTrue(result.turtle.contains("Athena"));
    }

    @Test
    void testToTurtle_roundtrip() {
        GraphEngine.addTurtle("rt_src", """
                @prefix ex: <http://ex.org/> .
                ex:Zeus a ex:God ;
                    ex:name "Zeus"@en ;
                    ex:power "lightning" .
                """);

        var exported = GraphEngine.toTurtle("rt_src");
        GraphEngine.addTurtle("rt_tgt", exported.turtle);

        assertEquals(GraphEngine.triples("rt_src").size(),
                     GraphEngine.triples("rt_tgt").size());
    }

    // ── SHACL validation ────────────────────────────────────────

    @Test
    void testValidate_conforms() {
        GraphEngine.addTurtle("valid", """
                @prefix ex: <http://ex.org/> .
                @prefix sh: <http://www.w3.org/ns/shacl#> .
                @prefix xsd: <http://www.w3.org/2001/XMLSchema#> .

                ex:PersonShape a sh:NodeShape ;
                    sh:targetClass ex:Person ;
                    sh:property [
                        sh:path ex:name ;
                        sh:minCount 1 ;
                        sh:datatype xsd:string
                    ] .

                ex:Alice a ex:Person ;
                    ex:name "Alice" .
                """);

        var results = GraphEngine.validate("valid");
        assertEquals(1, results.size());
        assertEquals("INFO", results.get(0).severity);
        assertTrue(results.get(0).message.contains("conforms"));
    }

    @Test
    void testValidate_violation() {
        GraphEngine.addTurtle("violation", """
                @prefix ex: <http://ex.org/> .
                @prefix sh: <http://www.w3.org/ns/shacl#> .

                ex:PersonShape a sh:NodeShape ;
                    sh:targetClass ex:Person ;
                    sh:property [
                        sh:path ex:name ;
                        sh:minCount 1 ;
                        sh:message "Person must have a name"
                    ] .

                ex:Alice a ex:Person .
                """);

        var results = GraphEngine.validate("violation");
        assertEquals(1, results.size());
        assertTrue(results.get(0).focusNode.contains("Alice"));
        assertEquals("Violation", results.get(0).severity);
        assertTrue(results.get(0).message.contains("name"));
    }

    @Test
    void testValidate_sparqlConstraint() {
        GraphEngine.addTurtle("sparql_shacl", """
                @prefix ex: <http://ex.org/> .
                @prefix sh: <http://www.w3.org/ns/shacl#> .
                @prefix rdf: <http://www.w3.org/1999/02/22-rdf-syntax-ns#> .

                ex:NoDuplicateShape a sh:NodeShape ;
                    sh:targetClass ex:Item ;
                    sh:sparql [
                        a sh:SPARQLConstraint ;
                        sh:message "Item shares a category with another item" ;
                        sh:select "PREFIX ex: <http://ex.org/> PREFIX rdf: <http://www.w3.org/1999/02/22-rdf-syntax-ns#> SELECT $this WHERE { $this ex:category ?c . ?other ex:category ?c . FILTER(?other != $this) }"
                    ] .

                ex:A a ex:Item ; ex:category ex:Cat1 .
                ex:B a ex:Item ; ex:category ex:Cat1 .
                """);

        var results = GraphEngine.validate("sparql_shacl");
        assertTrue(results.size() >= 2);
    }

    // ── project triples ─────────────────────────────────────────

    @Test
    void testProjectTriples() {
        var result = GraphEngine.projectTriples("proj", List.of(
                new String[]{"http://ex.org/Zeus", "http://www.w3.org/1999/02/22-rdf-syntax-ns#type", "http://ex.org/God"},
                new String[]{"http://ex.org/Athena", "http://www.w3.org/1999/02/22-rdf-syntax-ns#type", "http://ex.org/God"}
        ));
        assertEquals("proj", result.graphName);
        assertEquals(2, result.tripleCount);
        assertEquals("projected", result.status);

        var query = GraphEngine.query("proj",
                "SELECT ?x WHERE { ?x <http://www.w3.org/1999/02/22-rdf-syntax-ns#type> <http://ex.org/God> }", "");
        assertEquals(2, query.size());
    }

    @Test
    void testProjectTriples_empty() {
        var result = GraphEngine.projectTriples("empty", List.of());
        assertEquals("empty", result.status);
        assertEquals(0, result.tripleCount);
    }

    @Test
    void testProjectTriples_literals() {
        var result = GraphEngine.projectTriples("lit_proj", List.of(
                new String[]{"http://ex.org/Zeus", "http://ex.org/name", "\"Zeus\""},
                new String[]{"http://ex.org/Zeus", "http://ex.org/label", "\"Zeus\"@en"},
                new String[]{"http://ex.org/Zeus", "http://ex.org/age", "\"3000\"^^<http://www.w3.org/2001/XMLSchema#integer>"}
        ));
        assertEquals(3, result.tripleCount);

        var ages = GraphEngine.query("lit_proj",
                "SELECT ?age WHERE { <http://ex.org/Zeus> <http://ex.org/age> ?age }", "");
        assertEquals(1, ages.size());
        assertEquals(3000L, ((Number) ages.get(0).row.get("age")).longValue());
    }

    @Test
    void testProjectTriples_blankNodes() {
        GraphEngine.projectTriples("bnode", List.of(
                new String[]{"_:b1", "http://www.w3.org/1999/02/22-rdf-syntax-ns#type", "http://ex.org/Thing"},
                new String[]{"_:b1", "http://ex.org/name", "\"Anonymous\""}
        ));

        var results = GraphEngine.query("bnode",
                "SELECT ?name WHERE { ?x <http://ex.org/name> ?name }", "");
        assertEquals(1, results.size());
    }

    // ── multiple graphs isolation ───────────────────────────────

    @Test
    void testMultipleGraphs_isolated() {
        GraphEngine.addTurtle("ga", "@prefix ex: <http://ex.org/> . ex:Zeus a ex:God .");
        GraphEngine.addTurtle("gb", "@prefix ex: <http://ex.org/> . ex:Hercules a ex:Hero .");

        var heroesInA = GraphEngine.query("ga",
                "SELECT ?x WHERE { ?x <http://www.w3.org/1999/02/22-rdf-syntax-ns#type> <http://ex.org/Hero> }", "");
        assertTrue(heroesInA.isEmpty());

        var godsInB = GraphEngine.query("gb",
                "SELECT ?x WHERE { ?x <http://www.w3.org/1999/02/22-rdf-syntax-ns#type> <http://ex.org/God> }", "");
        assertTrue(godsInB.isEmpty());

        assertEquals(2, GraphEngine.list().size());
    }

    // ── error handling ──────────────────────────────────────────

    @Test
    void testQuery_nonExistentGraph() {
        var ex = assertThrows(IllegalArgumentException.class, () ->
                GraphEngine.query("no_such_graph", "SELECT * WHERE { ?s ?p ?o }", ""));
        assertTrue(ex.getMessage().contains("not found"));
    }

    @Test
    void testQuery_invalidSparql() {
        GraphEngine.addTurtle("sparql_err", "@prefix ex: <http://ex.org/> . ex:Zeus a ex:God .");
        assertThrows(RuntimeException.class, () ->
                GraphEngine.query("sparql_err", "NOT VALID SPARQL", ""));
    }

    // ── version ─────────────────────────────────────────────────

    @Test
    void testVersion() {
        var result = GraphEngine.version("test");
        assertEquals("test", result.version);
        assertNotNull(result.jenaVersion);
        assertFalse(result.jenaVersion.isEmpty());
    }
}
