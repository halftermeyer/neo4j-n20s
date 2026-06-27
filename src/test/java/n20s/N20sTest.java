package n20s;

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

    @Test
    void testProjectAndQuery() {
        try (Session session = driver.session()) {
            // Project some triples
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

        // Query with SPARQL
        try (Session session = driver.session()) {
            var result = session.run("""
                CALL n20s.graph.query('test',
                    'SELECT ?x WHERE { ?x <http://www.w3.org/1999/02/22-rdf-syntax-ns#type> <http://ex.org/God> }')
                YIELD row
                RETURN row
                """);

            var rows = result.list();
            assertEquals(2, rows.size()); // Zeus and Athena
        }
    }

    @Test
    void testInfer() {
        try (Session session = driver.session()) {
            session.run("""
                UNWIND [
                    {s: 'http://ex.org/Zeus', p: 'http://www.w3.org/1999/02/22-rdf-syntax-ns#type', o: 'http://ex.org/God'},
                    {s: 'http://ex.org/God', p: 'http://www.w3.org/2000/01/rdf-schema#subClassOf', o: 'http://ex.org/Being'}
                ] AS t
                WITH n20s.graph.project('infer_test', t.s, t.p, t.o) AS g
                RETURN g
                """);
        }

        // Run RDFS inference
        try (Session session = driver.session()) {
            var result = session.run("""
                CALL n20s.graph.infer('infer_test', 'RDFS')
                YIELD graphName, triplesBefore, triplesAfter, newTriples
                RETURN graphName, triplesBefore, triplesAfter, newTriples
                """);

            var record = result.single();
            assertEquals("infer_test", record.get("graphName").asString());
            assertTrue(record.get("newTriples").asLong() > 0, "RDFS should infer new triples");
        }

        // Verify Zeus is now also a Being
        try (Session session = driver.session()) {
            var result = session.run("""
                CALL n20s.graph.query('infer_test',
                    'SELECT ?x WHERE { ?x <http://www.w3.org/1999/02/22-rdf-syntax-ns#type> <http://ex.org/Being> }')
                YIELD row
                RETURN row
                """);

            var rows = result.list();
            assertTrue(rows.stream().anyMatch(r ->
                    r.get("row").asMap().get("x").toString().contains("Zeus")),
                    "Zeus should be inferred as a Being");
        }
    }

    @Test
    void testListAndDrop() {
        try (Session session = driver.session()) {
            session.run("""
                UNWIND [{s: 'http://ex.org/a', p: 'http://ex.org/b', o: 'http://ex.org/c'}] AS t
                WITH n20s.graph.project('g1', t.s, t.p, t.o) AS g
                RETURN g
                """);
        }

        // List
        try (Session session = driver.session()) {
            var result = session.run("CALL n20s.graph.list() YIELD graphName RETURN graphName");
            var names = result.list().stream().map(r -> r.get("graphName").asString()).toList();
            assertTrue(names.contains("g1"));
        }

        // Drop
        try (Session session = driver.session()) {
            session.run("CALL n20s.graph.drop('g1')");
        }

        // Verify dropped
        try (Session session = driver.session()) {
            var result = session.run("CALL n20s.graph.list() YIELD graphName RETURN graphName");
            assertTrue(result.list().isEmpty());
        }
    }
}
