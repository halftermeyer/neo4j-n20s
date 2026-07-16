package n20s;

import n20s.core.GraphCatalog;
import org.junit.jupiter.api.*;
import org.neo4j.driver.*;
import org.neo4j.harness.Neo4j;
import org.neo4j.harness.Neo4jBuilders;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class N20sTest {

    static Neo4j neo4j;
    static Driver driver;

    @BeforeAll
    static void setup() {
        neo4j = Neo4jBuilders.newInProcessBuilder()
                .withAggregationFunction(GraphProject.class)
                .withAggregationFunction(GraphAddTurtle.class)
                .withAggregationFunction(GraphProjectTemplate.class)
                .withProcedure(GraphProcedures.class)
                .build();
        driver = GraphDatabase.driver(neo4j.boltURI());
    }

    @AfterAll
    static void teardown() {
        driver.close();
        neo4j.close();
    }

    @AfterEach
    void cleanup() {
        GraphCatalog.list().forEach(GraphCatalog::drop);
    }

    // ── project + query ─────────────────────────────────────────

    @Test
    void testProjectAndQuery() {
        try (Session session = driver.session()) {
            var result = session.run("""
                UNWIND [
                    {s: 'http://ex.org/Zeus', p: 'http://www.w3.org/1999/02/22-rdf-syntax-ns#type', o: 'http://ex.org/God'},
                    {s: 'http://ex.org/Athena', p: 'http://www.w3.org/1999/02/22-rdf-syntax-ns#type', o: 'http://ex.org/God'},
                    {s: 'http://ex.org/Hercules', p: 'http://www.w3.org/1999/02/22-rdf-syntax-ns#type', o: 'http://ex.org/Hero'},
                    {s: 'http://ex.org/God', p: 'http://www.w3.org/2000/01/rdf-schema#subClassOf', o: 'http://ex.org/Being'},
                    {s: 'http://ex.org/Hero', p: 'http://www.w3.org/2000/01/rdf-schema#subClassOf', o: 'http://ex.org/Being'}
                ] AS t
                WITH n20s.graph.project('test', t.s, t.p, t.o) AS g
                RETURN g.graphName AS name, g.tripleCount AS count
                """);

            var record = result.single();
            assertEquals("test", record.get("name").asString());
            assertEquals(5, record.get("count").asLong());
        }

        try (Session session = driver.session()) {
            var result = session.run("""
                CALL n20s.graph.query('test',
                    'SELECT ?x WHERE { ?x <http://www.w3.org/1999/02/22-rdf-syntax-ns#type> <http://ex.org/God> }')
                YIELD row
                RETURN row
                """);

            assertEquals(2, result.list().size());
        }
    }

    // ── addTurtle ───────────────────────────────────────────────

    @Test
    void testAddTurtle_createsGraph() {
        try (Session session = driver.session()) {
            var result = session.run("""
                CALL n20s.graph.addTurtle('turtle_test', '
                    @prefix ex: <http://ex.org/> .
                    ex:Zeus a ex:God .
                    ex:Athena a ex:God .
                    ex:God <http://www.w3.org/2000/01/rdf-schema#subClassOf> ex:Being .
                ')
                YIELD graphName, triplesBefore, triplesAfter, added
                RETURN graphName, triplesBefore, triplesAfter, added
                """);

            var record = result.single();
            assertEquals("turtle_test", record.get("graphName").asString());
            assertEquals(0, record.get("triplesBefore").asLong());
            assertEquals(3, record.get("triplesAfter").asLong());
            assertEquals(3, record.get("added").asLong());
        }
    }

    @Test
    void testAddTurtle_incrementalAdd() {
        try (Session session = driver.session()) {
            session.run("""
                CALL n20s.graph.addTurtle('incr_test', '
                    @prefix ex: <http://ex.org/> .
                    ex:Zeus a ex:God .
                ')
                """);
        }

        try (Session session = driver.session()) {
            var result = session.run("""
                CALL n20s.graph.addTurtle('incr_test', '
                    @prefix ex: <http://ex.org/> .
                    ex:Athena a ex:God .
                    ex:Hercules a ex:Hero .
                ')
                YIELD triplesBefore, triplesAfter, added
                RETURN triplesBefore, triplesAfter, added
                """);

            var record = result.single();
            assertEquals(1, record.get("triplesBefore").asLong());
            assertEquals(3, record.get("triplesAfter").asLong());
            assertEquals(2, record.get("added").asLong());
        }
    }

    @Test
    void testAddTurtle_typedLiterals() {
        try (Session session = driver.session()) {
            session.run("""
                CALL n20s.graph.addTurtle('literal_test', '
                    @prefix ex: <http://ex.org/> .
                    @prefix xsd: <http://www.w3.org/2001/XMLSchema#> .
                    ex:Retinol ex:maxConcentration "0.05"^^xsd:decimal ;
                              ex:name "Retinol"@en .
                ')
                """);
        }

        try (Session session = driver.session()) {
            var result = session.run("""
                CALL n20s.graph.query('literal_test',
                    'SELECT ?val WHERE { <http://ex.org/Retinol> <http://ex.org/maxConcentration> ?val }')
                YIELD row
                RETURN row
                """);

            var rows = result.list();
            assertEquals(1, rows.size());
        }
    }

    // ── infer (forward chaining) ────────────────────────────────

    @Test
    void testInfer_rdfsSubClassOf() {
        try (Session session = driver.session()) {
            session.run("""
                CALL n20s.graph.addTurtle('infer_test', '
                    @prefix ex: <http://ex.org/> .
                    @prefix rdfs: <http://www.w3.org/2000/01/rdf-schema#> .
                    ex:Zeus a ex:God .
                    ex:God rdfs:subClassOf ex:Being .
                ')
                """);
        }

        try (Session session = driver.session()) {
            var result = session.run("""
                CALL n20s.graph.infer('infer_test', 'RDFS')
                YIELD triplesBefore, triplesAfter, newTriples
                RETURN triplesBefore, triplesAfter, newTriples
                """);

            var record = result.single();
            assertEquals(2, record.get("triplesBefore").asLong());
            assertTrue(record.get("newTriples").asLong() > 0);
        }

        // Zeus should now be a Being
        try (Session session = driver.session()) {
            var result = session.run("""
                CALL n20s.graph.query('infer_test',
                    'SELECT ?x WHERE { ?x <http://www.w3.org/1999/02/22-rdf-syntax-ns#type> <http://ex.org/Being> }')
                YIELD row
                RETURN row
                """);

            var rows = result.list();
            assertTrue(rows.stream().anyMatch(r ->
                    r.get("row").asMap().get("x").toString().contains("Zeus")));
        }
    }

    @Test
    void testInfer_owlMicro() {
        try (Session session = driver.session()) {
            session.run("""
                CALL n20s.graph.addTurtle('owl_test', '
                    @prefix ex: <http://ex.org/> .
                    @prefix rdfs: <http://www.w3.org/2000/01/rdf-schema#> .
                    ex:Zeus a ex:God .
                    ex:God rdfs:subClassOf ex:Immortal .
                    ex:Immortal rdfs:subClassOf ex:Being .
                ')
                """);
        }

        try (Session session = driver.session()) {
            session.run("""
                CALL n20s.graph.infer('owl_test', 'OWL_MICRO')
                YIELD newTriples RETURN newTriples
                """);
        }

        try (Session session = driver.session()) {
            var result = session.run("""
                CALL n20s.graph.query('owl_test',
                    'SELECT ?x WHERE { ?x <http://www.w3.org/1999/02/22-rdf-syntax-ns#type> <http://ex.org/Being> }')
                YIELD row
                RETURN row
                """);

            assertTrue(result.list().stream().anyMatch(r ->
                    r.get("row").asMap().get("x").toString().contains("Zeus")));
        }
    }

    // ── inferWithRules (custom Jena rules) ────────────────────

    @Test
    void testInferWithRules_basic() {
        try (Session session = driver.session()) {
            session.run("""
                CALL n20s.graph.addTurtle('rules_test', '
                    @prefix ex: <http://ex.org/> .
                    ex:Zeus a ex:God .
                    ex:Athena a ex:God .
                    ex:Hercules a ex:Hero .
                ')
                """);
        }

        // Custom rule: Gods and Heroes are both Beings
        try (Session session = driver.session()) {
            var result = session.run("""
                CALL n20s.graph.inferWithRules('rules_test', '
                    [godToBeing: (?x http://www.w3.org/1999/02/22-rdf-syntax-ns#type http://ex.org/God)
                        -> (?x http://www.w3.org/1999/02/22-rdf-syntax-ns#type http://ex.org/Being)]
                    [heroToBeing: (?x http://www.w3.org/1999/02/22-rdf-syntax-ns#type http://ex.org/Hero)
                        -> (?x http://www.w3.org/1999/02/22-rdf-syntax-ns#type http://ex.org/Being)]
                ')
                YIELD newTriples, profile
                RETURN newTriples, profile
                """);

            var record = result.single();
            assertTrue(record.get("newTriples").asLong() >= 3, "Should infer 3 new Being triples");
            assertTrue(record.get("profile").asString().contains("2 rules"));
        }

        // Verify all three are now Beings
        try (Session session = driver.session()) {
            var result = session.run("""
                CALL n20s.graph.query('rules_test',
                    'SELECT ?x WHERE { ?x <http://www.w3.org/1999/02/22-rdf-syntax-ns#type> <http://ex.org/Being> }')
                YIELD row RETURN row
                """);
            assertEquals(3, result.list().size());
        }
    }

    @Test
    void testInferWithRules_withBuiltins() {
        try (Session session = driver.session()) {
            session.run("""
                CALL n20s.graph.addTurtle('builtin_test', '
                    @prefix ex: <http://ex.org/> .
                    @prefix xsd: <http://www.w3.org/2001/XMLSchema#> .
                    ex:Alice a ex:Person ; ex:age 25 .
                    ex:Bob a ex:Person ; ex:age 15 .
                ')
                """);
        }

        // Rule with greaterThan builtin: age > 17 → Adult
        try (Session session = driver.session()) {
            session.run("""
                CALL n20s.graph.inferWithRules('builtin_test', '
                    [adult: (?x http://www.w3.org/1999/02/22-rdf-syntax-ns#type http://ex.org/Person)
                            (?x http://ex.org/age ?age)
                            greaterThan(?age, 17)
                        -> (?x http://www.w3.org/1999/02/22-rdf-syntax-ns#type http://ex.org/Adult)]
                ')
                """);
        }

        // Only Alice should be an Adult
        try (Session session = driver.session()) {
            var result = session.run("""
                CALL n20s.graph.query('builtin_test',
                    'SELECT ?x WHERE { ?x <http://www.w3.org/1999/02/22-rdf-syntax-ns#type> <http://ex.org/Adult> }')
                YIELD row RETURN row
                """);
            var rows = result.list();
            assertEquals(1, rows.size());
            assertTrue(rows.get(0).get("row").asMap().get("x").toString().contains("Alice"));
        }
    }

    @Test
    void testInferWithRules_invalidSyntax() {
        try (Session session = driver.session()) {
            session.run("""
                CALL n20s.graph.addTurtle('bad_rules', '
                    @prefix ex: <http://ex.org/> .
                    ex:Zeus a ex:God .
                ')
                """);
        }

        try (Session session = driver.session()) {
            assertThrows(Exception.class, () ->
                    session.run("CALL n20s.graph.inferWithRules('bad_rules', 'not valid rule syntax!!!') YIELD newTriples RETURN newTriples").list());
        }
    }

    // ── queryWithRules (backward chaining on custom rules) ────

    @Test
    void testQueryWithRules() {
        try (Session session = driver.session()) {
            session.run("""
                CALL n20s.graph.addTurtle('qr_test', '
                    @prefix ex: <http://ex.org/> .
                    ex:Zeus a ex:God .
                    ex:Athena a ex:God .
                    ex:Hercules a ex:Hero .
                ')
                """);
        }

        // Query with custom rule — backward chaining, no materialization
        try (Session session = driver.session()) {
            var result = session.run("""
                CALL n20s.graph.queryWithRules('qr_test',
                    'SELECT ?x WHERE { ?x <http://www.w3.org/1999/02/22-rdf-syntax-ns#type> <http://ex.org/Being> }',
                    '
                    [godBeing: (?x http://www.w3.org/1999/02/22-rdf-syntax-ns#type http://ex.org/God)
                        -> (?x http://www.w3.org/1999/02/22-rdf-syntax-ns#type http://ex.org/Being)]
                    [heroBeing: (?x http://www.w3.org/1999/02/22-rdf-syntax-ns#type http://ex.org/Hero)
                        -> (?x http://www.w3.org/1999/02/22-rdf-syntax-ns#type http://ex.org/Being)]
                    ')
                YIELD row RETURN row
                """);

            assertEquals(3, result.list().size(), "All three should be inferred as Beings");
        }

        // Verify base model was NOT modified
        try (Session session = driver.session()) {
            var result = session.run("""
                CALL n20s.graph.triples('qr_test')
                YIELD subject RETURN count(*) AS cnt
                """);
            assertEquals(3, result.single().get("cnt").asLong(),
                    "queryWithRules should not add triples to the base model");
        }
    }

    @Test
    void testQueryWithRules_withBuiltins() {
        try (Session session = driver.session()) {
            session.run("""
                CALL n20s.graph.addTurtle('qr_builtin', '
                    @prefix ex: <http://ex.org/> .
                    ex:Alice a ex:Person ; ex:age 25 .
                    ex:Bob a ex:Person ; ex:age 15 .
                ')
                """);
        }

        try (Session session = driver.session()) {
            var result = session.run("""
                CALL n20s.graph.queryWithRules('qr_builtin',
                    'SELECT ?x WHERE { ?x <http://www.w3.org/1999/02/22-rdf-syntax-ns#type> <http://ex.org/Adult> }',
                    '[adult: (?x http://www.w3.org/1999/02/22-rdf-syntax-ns#type http://ex.org/Person)
                             (?x http://ex.org/age ?a)
                             greaterThan(?a, 17)
                         -> (?x http://www.w3.org/1999/02/22-rdf-syntax-ns#type http://ex.org/Adult)]')
                YIELD row RETURN row
                """);

            var rows = result.list();
            assertEquals(1, rows.size());
            assertTrue(rows.get(0).get("row").asMap().get("x").toString().contains("Alice"));
        }
    }

    @Test
    void testQueryWithRules_plusRDFS() {
        // RDFS infers Zeus is a Being (God subClassOf Being)
        // Custom rule infers Beings are Worshipped
        // Combined: Zeus → God → Being → Worshipped
        try (Session session = driver.session()) {
            session.run("""
                CALL n20s.graph.addTurtle('combo_test', '
                    @prefix ex: <http://ex.org/> .
                    @prefix rdfs: <http://www.w3.org/2000/01/rdf-schema#> .
                    ex:Zeus a ex:God .
                    ex:God rdfs:subClassOf ex:Being .
                ')
                """);
        }

        // Without RDFS profile: custom rule alone can't see Zeus as a Being
        try (Session session = driver.session()) {
            var result = session.run("""
                CALL n20s.graph.queryWithRules('combo_test',
                    'SELECT ?x WHERE { ?x <http://www.w3.org/1999/02/22-rdf-syntax-ns#type> <http://ex.org/Worshipped> }',
                    '[worship: (?x rdf:type http://ex.org/Being) -> (?x rdf:type http://ex.org/Worshipped)]')
                YIELD row RETURN row
                """);
            assertTrue(result.list().isEmpty(), "Without RDFS, Zeus is not a Being yet");
        }

        // With RDFS profile: RDFS infers Being, custom rule infers Worshipped
        try (Session session = driver.session()) {
            var result = session.run("""
                CALL n20s.graph.queryWithRules('combo_test',
                    'SELECT ?x WHERE { ?x <http://www.w3.org/1999/02/22-rdf-syntax-ns#type> <http://ex.org/Worshipped> }',
                    '[worship: (?x rdf:type http://ex.org/Being) -> (?x rdf:type http://ex.org/Worshipped)]',
                    'RDFS')
                YIELD row RETURN row
                """);
            var rows = result.list();
            assertEquals(1, rows.size());
            assertTrue(rows.get(0).get("row").asMap().get("x").toString().contains("Zeus"));
        }

        // Base model unchanged
        try (Session session = driver.session()) {
            var result = session.run("""
                CALL n20s.graph.triples('combo_test') YIELD subject RETURN count(*) AS cnt
                """);
            assertEquals(2, result.single().get("cnt").asLong());
        }
    }

    // ── backward chaining ───────────────────────────────────────

    @Test
    void testBackwardChaining() {
        try (Session session = driver.session()) {
            session.run("""
                CALL n20s.graph.addTurtle('bc_test', '
                    @prefix ex: <http://ex.org/> .
                    @prefix rdfs: <http://www.w3.org/2000/01/rdf-schema#> .
                    ex:Zeus a ex:God .
                    ex:God rdfs:subClassOf ex:Being .
                ')
                """);
        }

        // Query with RDFS backward chaining — no infer() step
        try (Session session = driver.session()) {
            var result = session.run("""
                CALL n20s.graph.query('bc_test',
                    'SELECT ?x WHERE { ?x <http://www.w3.org/1999/02/22-rdf-syntax-ns#type> <http://ex.org/Being> }',
                    'RDFS')
                YIELD row
                RETURN row
                """);

            var rows = result.list();
            assertTrue(rows.stream().anyMatch(r ->
                    r.get("row").asMap().get("x").toString().contains("Zeus")),
                    "Backward chaining should infer Zeus as a Being");
        }

        // Verify base model was NOT modified (no materialization)
        try (Session session = driver.session()) {
            var result = session.run("""
                CALL n20s.graph.triples('bc_test')
                YIELD subject, predicate, object
                RETURN count(*) AS cnt
                """);

            assertEquals(2, result.single().get("cnt").asLong(),
                    "Backward chaining should not add triples to the base model");
        }
    }

    // ── construct ───────────────────────────────────────────────

    @Test
    void testConstruct() {
        try (Session session = driver.session()) {
            session.run("""
                CALL n20s.graph.addTurtle('construct_test', '
                    @prefix ex: <http://ex.org/> .
                    ex:Zeus a ex:God .
                    ex:Athena a ex:God .
                    ex:Hercules a ex:Hero .
                ')
                """);
        }

        try (Session session = driver.session()) {
            var result = session.run("""
                CALL n20s.graph.construct('construct_test',
                    'CONSTRUCT { ?x <http://ex.org/is> <http://ex.org/divine> }
                     WHERE { ?x <http://www.w3.org/1999/02/22-rdf-syntax-ns#type> <http://ex.org/God> }')
                YIELD subject, predicate, object
                RETURN subject, predicate, object
                """);

            var rows = result.list();
            assertEquals(2, rows.size());
            assertTrue(rows.stream().allMatch(r ->
                    r.get("predicate").asString().equals("http://ex.org/is")));
        }
    }

    // ── triples ─────────────────────────────────────────────────

    @Test
    void testTriples() {
        try (Session session = driver.session()) {
            session.run("""
                CALL n20s.graph.addTurtle('triples_test', '
                    @prefix ex: <http://ex.org/> .
                    ex:Zeus a ex:God .
                    ex:Zeus ex:name "Zeus"@en .
                ')
                """);
        }

        try (Session session = driver.session()) {
            var result = session.run("""
                CALL n20s.graph.triples('triples_test')
                YIELD subject, predicate, object
                RETURN subject, predicate, object
                """);

            var rows = result.list();
            assertEquals(2, rows.size());

            // Check language-tagged literal formatting
            var nameTriple = rows.stream()
                    .filter(r -> r.get("predicate").asString().contains("name"))
                    .findFirst().orElseThrow();
            assertTrue(nameTriple.get("object").asString().contains("@en"));
        }
    }

    // ── list + drop ─────────────────────────────────────────────

    @Test
    void testListAndDrop() {
        try (Session session = driver.session()) {
            session.run("""
                UNWIND [{s: 'http://ex.org/a', p: 'http://ex.org/b', o: 'http://ex.org/c'}] AS t
                WITH n20s.graph.project('g1', t.s, t.p, t.o) AS g
                RETURN g
                """);
        }

        try (Session session = driver.session()) {
            var result = session.run("CALL n20s.graph.list() YIELD graphName RETURN graphName");
            var names = result.list().stream().map(r -> r.get("graphName").asString()).toList();
            assertTrue(names.contains("g1"));
        }

        try (Session session = driver.session()) {
            var result = session.run("CALL n20s.graph.drop('g1') YIELD status RETURN status");
            assertEquals("dropped", result.single().get("status").asString());
        }

        try (Session session = driver.session()) {
            var result = session.run("CALL n20s.graph.list() YIELD graphName RETURN graphName");
            assertTrue(result.list().isEmpty());
        }
    }

    @Test
    void testDropNonExistent() {
        try (Session session = driver.session()) {
            var result = session.run("CALL n20s.graph.drop('no_such_graph') YIELD status RETURN status");
            assertEquals("not found", result.single().get("status").asString());
        }
    }

    // ── SHACL validation ────────────────────────────────────────

    @Test
    void testValidate_conforms() {
        try (Session session = driver.session()) {
            session.run("""
                CALL n20s.graph.addTurtle('valid_test', '
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
                ')
                """);
        }

        try (Session session = driver.session()) {
            var result = session.run("""
                CALL n20s.graph.validate('valid_test')
                YIELD severity, message
                RETURN severity, message
                """);

            var record = result.single();
            assertEquals("INFO", record.get("severity").asString());
            assertTrue(record.get("message").asString().contains("conforms"));
        }
    }

    @Test
    void testValidate_violation() {
        try (Session session = driver.session()) {
            session.run("""
                CALL n20s.graph.addTurtle('violation_test', '
                    @prefix ex: <http://ex.org/> .
                    @prefix sh: <http://www.w3.org/ns/shacl#> .
                    @prefix xsd: <http://www.w3.org/2001/XMLSchema#> .

                    ex:PersonShape a sh:NodeShape ;
                        sh:targetClass ex:Person ;
                        sh:property [
                            sh:path ex:name ;
                            sh:minCount 1 ;
                            sh:message "Person must have a name"
                        ] .

                    ex:Alice a ex:Person .
                ')
                """);
        }

        try (Session session = driver.session()) {
            var result = session.run("""
                CALL n20s.graph.validate('violation_test')
                YIELD focusNode, severity, message
                RETURN focusNode, severity, message
                """);

            var rows = result.list();
            assertEquals(1, rows.size());
            var row = rows.get(0);
            assertTrue(row.get("focusNode").asString().contains("Alice"));
            assertEquals("Violation", row.get("severity").asString());
            assertTrue(row.get("message").asString().contains("name"));
        }
    }

    @Test
    void testValidate_sparqlConstraint() {
        try (Session session = driver.session()) {
            session.run("""
                CALL n20s.graph.addTurtle('sparql_shacl_test', '
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
                ')
                """);
        }

        try (Session session = driver.session()) {
            var result = session.run("""
                CALL n20s.graph.validate('sparql_shacl_test')
                YIELD focusNode, severity, message
                RETURN focusNode, severity, message
                """);

            var rows = result.list();
            assertTrue(rows.size() >= 2, "Both items should violate the constraint");
        }
    }

    // ── project with literals ───────────────────────────────────

    @Test
    void testProject_literals() {
        try (Session session = driver.session()) {
            session.run("""
                UNWIND [
                    {s: 'http://ex.org/Zeus', p: 'http://ex.org/name', o: '"Zeus"'},
                    {s: 'http://ex.org/Zeus', p: 'http://ex.org/label', o: '"Zeus"@en'},
                    {s: 'http://ex.org/Zeus', p: 'http://ex.org/age', o: '"3000"^^<http://www.w3.org/2001/XMLSchema#integer>'}
                ] AS t
                WITH n20s.graph.project('lit_test', t.s, t.p, t.o) AS g
                RETURN g.tripleCount AS count
                """);
        }

        try (Session session = driver.session()) {
            var result = session.run("""
                CALL n20s.graph.query('lit_test',
                    'SELECT ?age WHERE { <http://ex.org/Zeus> <http://ex.org/age> ?age }')
                YIELD row
                RETURN row
                """);

            var rows = result.list();
            assertEquals(1, rows.size());
            assertEquals(3000, ((Number) rows.get(0).get("row").asMap().get("age")).intValue());
        }
    }

    // ── blank nodes ─────────────────────────────────────────────

    @Test
    void testProject_blankNodes() {
        try (Session session = driver.session()) {
            session.run("""
                UNWIND [
                    {s: '_:b1', p: 'http://www.w3.org/1999/02/22-rdf-syntax-ns#type', o: 'http://ex.org/Thing'},
                    {s: '_:b1', p: 'http://ex.org/name', o: '"Anonymous"'}
                ] AS t
                WITH n20s.graph.project('bnode_test', t.s, t.p, t.o) AS g
                RETURN g.tripleCount AS count
                """);
        }

        try (Session session = driver.session()) {
            var result = session.run("""
                CALL n20s.graph.query('bnode_test',
                    'SELECT ?name WHERE { ?x <http://ex.org/name> ?name }')
                YIELD row
                RETURN row
                """);

            assertEquals(1, result.list().size());
        }
    }

    // ── empty projection ────────────────────────────────────────

    @Test
    void testProject_empty() {
        try (Session session = driver.session()) {
            var result = session.run("""
                UNWIND [] AS t
                WITH n20s.graph.project('empty_test', t.s, t.p, t.o) AS g
                RETURN g.status AS status, g.tripleCount AS count
                """);

            var record = result.single();
            assertEquals("empty", record.get("status").asString());
            assertEquals(0, record.get("count").asLong());
        }
    }

    // ── multiple graphs isolation ───────────────────────────────

    @Test
    void testMultipleGraphs_isolated() {
        try (Session session = driver.session()) {
            session.run("""
                CALL n20s.graph.addTurtle('graph_a', '
                    @prefix ex: <http://ex.org/> .
                    ex:Zeus a ex:God .
                ')
                """);
            session.run("""
                CALL n20s.graph.addTurtle('graph_b', '
                    @prefix ex: <http://ex.org/> .
                    ex:Hercules a ex:Hero .
                ')
                """);
        }

        // graph_a should only have God
        try (Session session = driver.session()) {
            var result = session.run("""
                CALL n20s.graph.query('graph_a',
                    'SELECT ?x WHERE { ?x <http://www.w3.org/1999/02/22-rdf-syntax-ns#type> <http://ex.org/Hero> }')
                YIELD row RETURN row
                """);
            assertTrue(result.list().isEmpty(), "graph_a should not contain Hero triples");
        }

        // graph_b should only have Hero
        try (Session session = driver.session()) {
            var result = session.run("""
                CALL n20s.graph.query('graph_b',
                    'SELECT ?x WHERE { ?x <http://www.w3.org/1999/02/22-rdf-syntax-ns#type> <http://ex.org/God> }')
                YIELD row RETURN row
                """);
            assertTrue(result.list().isEmpty(), "graph_b should not contain God triples");
        }

        // list should show both
        try (Session session = driver.session()) {
            var result = session.run("CALL n20s.graph.list() YIELD graphName RETURN graphName ORDER BY graphName");
            var names = result.list().stream().map(r -> r.get("graphName").asString()).toList();
            assertEquals(List.of("graph_a", "graph_b"), names);
        }
    }

    // ── toTurtle ─────────────────────────────────────────────────

    @Test
    void testToTurtle() {
        try (Session session = driver.session()) {
            session.run("""
                CALL n20s.graph.addTurtle('export_test', '
                    @prefix ex: <http://ex.org/> .
                    ex:Zeus a ex:God .
                    ex:Athena a ex:God .
                ')
                """);
        }

        try (Session session = driver.session()) {
            var result = session.run("""
                CALL n20s.graph.toTurtle('export_test')
                YIELD graphName, tripleCount, turtle
                RETURN graphName, tripleCount, turtle
                """);

            var record = result.single();
            assertEquals("export_test", record.get("graphName").asString());
            assertEquals(2, record.get("tripleCount").asLong());
            String turtle = record.get("turtle").asString();
            assertTrue(turtle.contains("Zeus"));
            assertTrue(turtle.contains("Athena"));
            assertTrue(turtle.contains("God"));
        }
    }

    @Test
    void testToTurtle_roundtrip() {
        // Create graph, export, re-import into new graph, verify same triple count
        try (Session session = driver.session()) {
            session.run("""
                CALL n20s.graph.addTurtle('rt_source', '
                    @prefix ex: <http://ex.org/> .
                    ex:Zeus a ex:God ;
                        ex:name "Zeus"@en ;
                        ex:power "lightning" .
                ')
                """);
        }

        String exportedTurtle;
        try (Session session = driver.session()) {
            var result = session.run("""
                CALL n20s.graph.toTurtle('rt_source')
                YIELD turtle RETURN turtle
                """);
            exportedTurtle = result.single().get("turtle").asString();
        }

        try (Session session = driver.session()) {
            session.run("CALL n20s.graph.addTurtle('rt_target', $ttl)",
                    Map.of("ttl", exportedTurtle));
        }

        try (Session session = driver.session()) {
            var result = session.run("""
                CALL n20s.graph.list() YIELD graphName, tripleCount
                WHERE graphName IN ['rt_source', 'rt_target']
                RETURN graphName, tripleCount ORDER BY graphName
                """);
            var rows = result.list();
            assertEquals(rows.get(0).get("tripleCount").asLong(),
                         rows.get(1).get("tripleCount").asLong(),
                         "Round-trip should preserve triple count");
        }
    }

    // ── n20s.version() ──────────────────────────────────────────

    @Test
    void testVersion() {
        try (Session session = driver.session()) {
            var result = session.run("CALL n20s.version() YIELD version, jenaVersion RETURN version, jenaVersion");
            var record = result.single();
            assertNotNull(record.get("version").asString());
            assertNotNull(record.get("jenaVersion").asString());
            assertFalse(record.get("jenaVersion").asString().isEmpty());
        }
    }

    // ── error handling ──────────────────────────────────────────

    @Test
    void testQuery_nonExistentGraph() {
        try (Session session = driver.session()) {
            var ex = assertThrows(Exception.class, () ->
                    session.run("CALL n20s.graph.query('no_such_graph', 'SELECT * WHERE { ?s ?p ?o }') YIELD row RETURN row").list());
            assertTrue(ex.getMessage().contains("not found"));
        }
    }

    @Test
    void testQuery_invalidSparql() {
        try (Session session = driver.session()) {
            session.run("""
                CALL n20s.graph.addTurtle('sparql_err', '
                    @prefix ex: <http://ex.org/> .
                    ex:Zeus a ex:God .
                ')
                """);
        }

        try (Session session = driver.session()) {
            assertThrows(Exception.class, () ->
                    session.run("CALL n20s.graph.query('sparql_err', 'NOT VALID SPARQL') YIELD row RETURN row").list());
        }
    }

    @Test
    void testInfer_invalidProfile() {
        try (Session session = driver.session()) {
            session.run("""
                CALL n20s.graph.addTurtle('profile_err', '
                    @prefix ex: <http://ex.org/> .
                    ex:Zeus a ex:God .
                ')
                """);
        }

        try (Session session = driver.session()) {
            var ex = assertThrows(Exception.class, () ->
                    session.run("CALL n20s.graph.infer('profile_err', 'INVALID_PROFILE') YIELD graphName RETURN graphName").list());
            assertTrue(ex.getMessage().contains("Unknown reasoning profile"));
        }
    }

    @Test
    void testAddTurtle_invalidSyntax() {
        try (Session session = driver.session()) {
            var ex = assertThrows(Exception.class, () ->
                    session.run("CALL n20s.graph.addTurtle('bad_turtle', 'this is not valid turtle @#$%') YIELD added RETURN added").list());
            assertTrue(ex.getMessage().contains("Invalid Turtle") || ex.getMessage().contains("RiotException"));
        }
    }

    // ── projectTemplate ─────────────────────────────────────────

    @Test
    void testProjectTemplate_nodeWithLabelFanOutAndFilter() {
        try (Session session = driver.session()) {
            session.run("CREATE (:Thing:`_Scratch` {id: 'id', prop: ['p1', 'p2', 'p3']})");

            var result = session.run("""
                MATCH (t:Thing)
                WITH n20s.graph.projectTemplate('tpl_test', '{
                  "subject": "http://example.com#thing_{id}",
                  "triples": [
                    { "predicate": "http://www.w3.org/1999/02/22-rdf-syntax-ns#type",
                      "object": "http://example.com#{_labels}",
                      "kind": "iri",
                      "exclude": ["_Scratch"] },
                    { "predicate": "http://example.com#has_prop",
                      "object": "http://example.com#{prop}",
                      "kind": "iri" }
                  ]
                }', t) AS g
                RETURN g.graphName AS name, g.rows AS rows, g.tripleCount AS count
                """);

            var record = result.single();
            assertEquals("tpl_test", record.get("name").asString());
            assertEquals(1, record.get("rows").asLong());
            assertEquals(4, record.get("count").asLong()); // 1 rdf:type (label filtered) + 3 has_prop

            var typed = session.run("""
                CALL n20s.graph.query('tpl_test',
                    'SELECT ?x WHERE { ?x a <http://example.com#Thing> }')
                YIELD row RETURN row
                """).list();
            assertEquals(1, typed.size());

            session.run("MATCH (t:Thing) DETACH DELETE t");
        }
    }

    @Test
    void testProjectTemplate_mapRow() {
        try (Session session = driver.session()) {
            var result = session.run("""
                UNWIND [{id: 'w1', score: 42}, {id: 'w2', score: 7}] AS row
                WITH n20s.graph.projectTemplate('tpl_map', '{
                  "subject": "http://ex.org#w_{id}",
                  "triples": [
                    { "predicate": "http://ex.org#score", "object": "{score}" }
                  ]
                }', row) AS g
                RETURN g.rows AS rows, g.tripleCount AS count
                """);

            var record = result.single();
            assertEquals(2, record.get("rows").asLong());
            assertEquals(2, record.get("count").asLong());
        }
    }

    @Test
    void testProjectTemplate_entityListRow() {
        try (Session session = driver.session()) {
            session.run("""
                CREATE (:Thing {id: 's1'})-[:RELATES_TO {weight: 3}]->(:OtherThing {id: 't1'}),
                       (:Thing {id: 's2'})-[:RELATES_TO {weight: 5}]->(:OtherThing {id: 't2'})
                """);

            var result = session.run("""
                MATCH (s:Thing)-[r:RELATES_TO]->(t:OtherThing)
                WITH n20s.graph.projectTemplate('tpl_rel', '{
                  "subject": "http://ex.org#thing_{_0.id}",
                  "triples": [
                    { "predicate": { "from": "_1._type", "map": {
                        "RELATES_TO": "http://ex.org#relatesTo"
                      }},
                      "object": "http://ex.org#other_{_2.id}",
                      "kind": "iri" },
                    { "predicate": "http://ex.org#weight", "object": "{_1.weight}" }
                  ]
                }', [s, r, t]) AS g
                RETURN g.graphName AS name, g.rows AS rows, g.tripleCount AS count
                """);

            var record = result.single();
            assertEquals("tpl_rel", record.get("name").asString());
            assertEquals(2, record.get("rows").asLong());
            assertEquals(4, record.get("count").asLong()); // 2 × (relatesTo + weight)

            var linked = session.run("""
                CALL n20s.graph.query('tpl_rel',
                    'SELECT ?o WHERE { <http://ex.org#thing_s1> <http://ex.org#relatesTo> ?o }')
                YIELD row RETURN row
                """).list();
            assertEquals(1, linked.size());

            session.run("MATCH (n) WHERE n:Thing OR n:OtherThing DETACH DELETE n");
        }
    }

    @Test
    void testProjectTemplate_namedEntityMapRow() {
        try (Session session = driver.session()) {
            session.run("CREATE (:Thing {id: 's1'})-[:RELATES_TO]->(:OtherThing {id: 't1'})");

            var result = session.run("""
                MATCH (s:Thing)-[r:RELATES_TO]->(t:OtherThing)
                WITH n20s.graph.projectTemplate('tpl_named', '{
                  "subject": "http://ex.org#thing_{s.id}",
                  "triples": [
                    { "predicate": { "from": "r._type", "map": {
                        "RELATES_TO": "http://ex.org#relatesTo"
                      }},
                      "object": "http://ex.org#other_{t.id}",
                      "kind": "iri" }
                  ]
                }', {s: s, r: r, t: t}) AS g
                RETURN g.tripleCount AS count
                """);

            assertEquals(1, result.single().get("count").asLong());

            session.run("MATCH (n) WHERE n:Thing OR n:OtherThing DETACH DELETE n");
        }
    }

    @Test
    void testProjectTemplate_relationshipRow_varLengthUnwind() {
        try (Session session = driver.session()) {
            session.run("""
                CREATE (:Thing {id: 'a'})-[:RELATES_TO]->(:Thing {id: 'b'})
                       -[:RELATES_TO]->(:Thing {id: 'c'})
                """);

            // r in a var-length pattern is a LIST of relationships — one row per hop,
            // each self-contained via _start/_end
            var result = session.run("""
                MATCH (:Thing {id: 'a'})-[rels:RELATES_TO*2]->(:Thing)
                UNWIND rels AS hop
                WITH n20s.graph.projectTemplate('tpl_hops', '{
                  "subject": "http://ex.org#thing_{_start.id}",
                  "triples": [
                    { "predicate": { "from": "_type", "map": {
                        "RELATES_TO": "http://ex.org#relatesTo"
                      }},
                      "object": "http://ex.org#thing_{_end.id}",
                      "kind": "iri" }
                  ]
                }', hop) AS g
                RETURN g.rows AS rows, g.tripleCount AS count
                """);

            var record = result.single();
            assertEquals(2, record.get("rows").asLong());
            assertEquals(2, record.get("count").asLong());

            var chained = session.run("""
                CALL n20s.graph.query('tpl_hops',
                    'SELECT ?x WHERE { <http://ex.org#thing_a> <http://ex.org#relatesTo> ?x }')
                YIELD row RETURN row
                """).list();
            assertEquals(1, chained.size());

            session.run("MATCH (n:Thing) DETACH DELETE n");
        }
    }

    @Test
    void testProjectTemplate_pathRow() {
        try (Session session = driver.session()) {
            session.run("CREATE (:Thing {id: 'a'})-[:RELATES_TO]->(:OtherThing {id: 'b'})");

            var result = session.run("""
                MATCH p = (:Thing)-[:RELATES_TO]->(:OtherThing)
                WITH n20s.graph.projectTemplate('tpl_path', '{
                  "subject": "http://ex.org#thing_{_0.id}",
                  "triples": [
                    { "predicate": { "from": "_1._type", "map": {
                        "RELATES_TO": "http://ex.org#relatesTo"
                      }},
                      "object": "http://ex.org#other_{_2.id}",
                      "kind": "iri" }
                  ]
                }', p) AS g
                RETURN g.tripleCount AS count
                """);

            assertEquals(1, result.single().get("count").asLong());

            session.run("MATCH (n) WHERE n:Thing OR n:OtherThing DETACH DELETE n");
        }
    }
}
