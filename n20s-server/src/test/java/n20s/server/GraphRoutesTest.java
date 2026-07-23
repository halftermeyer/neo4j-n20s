package n20s.server;

import io.javalin.Javalin;
import n20s.core.GraphCatalog;
import org.junit.jupiter.api.*;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;

import static org.junit.jupiter.api.Assertions.*;

class GraphRoutesTest {

    static Javalin app;
    static int port;
    static HttpClient client;

    @BeforeAll
    static void setup() {
        app = N20sServer.createApp();
        app.start(0); // random port
        port = app.port();
        client = HttpClient.newHttpClient();
    }

    @AfterAll
    static void teardown() {
        app.stop();
    }

    @AfterEach
    void cleanup() {
        GraphCatalog.list().forEach(GraphCatalog::drop);
    }

    private String url(String path) {
        return "http://localhost:" + port + path;
    }

    private HttpResponse<String> get(String path) throws Exception {
        return client.send(
                HttpRequest.newBuilder().uri(URI.create(url(path))).GET().build(),
                HttpResponse.BodyHandlers.ofString());
    }

    private HttpResponse<String> post(String path, String json) throws Exception {
        return client.send(
                HttpRequest.newBuilder()
                        .uri(URI.create(url(path)))
                        .header("Content-Type", "application/json")
                        .POST(HttpRequest.BodyPublishers.ofString(json))
                        .build(),
                HttpResponse.BodyHandlers.ofString());
    }

    private HttpResponse<String> delete(String path) throws Exception {
        return client.send(
                HttpRequest.newBuilder().uri(URI.create(url(path))).DELETE().build(),
                HttpResponse.BodyHandlers.ofString());
    }

    // ── Version ─────────────────────────────────────────────────

    @Test
    void testVersion() throws Exception {
        var resp = get("/version");
        assertEquals(200, resp.statusCode());
        assertTrue(resp.body().contains("jenaVersion"));
    }

    // ── Add Turtle + Query ──────────────────────────────────────

    @Test
    void testAddTurtleAndQuery() throws Exception {
        var addResp = post("/graph/test/turtle",
                """
                {"turtle": "@prefix ex: <http://ex.org/> . ex:Zeus a ex:God . ex:Athena a ex:God ."}
                """);
        assertEquals(200, addResp.statusCode());
        assertTrue(addResp.body().contains("\"added\":2"));

        var queryResp = post("/graph/test/query",
                """
                {"sparql": "SELECT ?x WHERE { ?x <http://www.w3.org/1999/02/22-rdf-syntax-ns#type> <http://ex.org/God> }"}
                """);
        assertEquals(200, queryResp.statusCode());
        assertTrue(queryResp.body().contains("Zeus"));
        assertTrue(queryResp.body().contains("Athena"));
    }

    // ── Infer ───────────────────────────────────────────────────

    @Test
    void testInfer() throws Exception {
        post("/graph/inf/turtle",
                """
                {"turtle": "@prefix ex: <http://ex.org/> . @prefix rdfs: <http://www.w3.org/2000/01/rdf-schema#> . ex:Zeus a ex:God . ex:God rdfs:subClassOf ex:Being ."}
                """);

        var inferResp = post("/graph/inf/infer", "{\"profile\": \"RDFS\"}");
        assertEquals(200, inferResp.statusCode());
        assertTrue(inferResp.body().contains("newTriples"));

        var queryResp = post("/graph/inf/query",
                """
                {"sparql": "SELECT ?x WHERE { ?x <http://www.w3.org/1999/02/22-rdf-syntax-ns#type> <http://ex.org/Being> }"}
                """);
        assertTrue(queryResp.body().contains("Zeus"));
    }

    // ── Infer With Rules ────────────────────────────────────────

    @Test
    void testInferWithRules() throws Exception {
        post("/graph/rules/turtle",
                """
                {"turtle": "@prefix ex: <http://ex.org/> . ex:Zeus a ex:God ."}
                """);

        var resp = post("/graph/rules/inferWithRules",
                """
                {"rules": "[godBeing: (?x http://www.w3.org/1999/02/22-rdf-syntax-ns#type http://ex.org/God) -> (?x http://www.w3.org/1999/02/22-rdf-syntax-ns#type http://ex.org/Being)]"}
                """);
        assertEquals(200, resp.statusCode());

        var queryResp = post("/graph/rules/query",
                """
                {"sparql": "SELECT ?x WHERE { ?x <http://www.w3.org/1999/02/22-rdf-syntax-ns#type> <http://ex.org/Being> }"}
                """);
        assertTrue(queryResp.body().contains("Zeus"));
    }

    // ── Query With Rules ────────────────────────────────────────

    @Test
    void testQueryWithRules() throws Exception {
        post("/graph/qr/turtle",
                """
                {"turtle": "@prefix ex: <http://ex.org/> . ex:Zeus a ex:God ."}
                """);

        var resp = post("/graph/qr/queryWithRules",
                """
                {"sparql": "SELECT ?x WHERE { ?x <http://www.w3.org/1999/02/22-rdf-syntax-ns#type> <http://ex.org/Being> }",
                 "rules": "[godBeing: (?x http://www.w3.org/1999/02/22-rdf-syntax-ns#type http://ex.org/God) -> (?x http://www.w3.org/1999/02/22-rdf-syntax-ns#type http://ex.org/Being)]"}
                """);
        assertEquals(200, resp.statusCode());
        assertTrue(resp.body().contains("Zeus"));
    }

    // ── Construct ───────────────────────────────────────────────

    @Test
    void testConstruct() throws Exception {
        post("/graph/con/turtle",
                """
                {"turtle": "@prefix ex: <http://ex.org/> . ex:Zeus a ex:God . ex:Athena a ex:God ."}
                """);

        var resp = post("/graph/con/construct",
                """
                {"sparql": "CONSTRUCT { ?x <http://ex.org/is> <http://ex.org/divine> } WHERE { ?x <http://www.w3.org/1999/02/22-rdf-syntax-ns#type> <http://ex.org/God> }"}
                """);
        assertEquals(200, resp.statusCode());
        assertTrue(resp.body().contains("divine"));
    }

    // ── Triples ─────────────────────────────────────────────────

    @Test
    void testTriples() throws Exception {
        post("/graph/tri/turtle",
                """
                {"turtle": "@prefix ex: <http://ex.org/> . ex:Zeus a ex:God ."}
                """);

        var resp = get("/graph/tri/triples");
        assertEquals(200, resp.statusCode());
        assertTrue(resp.body().contains("Zeus"));
    }

    // ── Export Turtle ────────────────────────────────────────────

    @Test
    void testToTurtle() throws Exception {
        post("/graph/exp/turtle",
                """
                {"turtle": "@prefix ex: <http://ex.org/> . ex:Zeus a ex:God ."}
                """);

        var resp = get("/graph/exp/turtle");
        assertEquals(200, resp.statusCode());
        assertTrue(resp.body().contains("turtle"));
        assertTrue(resp.body().contains("Zeus"));
    }

    // ── List + Drop ─────────────────────────────────────────────

    @Test
    void testListAndDrop() throws Exception {
        post("/graph/g1/turtle",
                "{\"turtle\": \"@prefix ex: <http://ex.org/> . ex:A a ex:B .\"}");

        var listResp = get("/graph");
        assertEquals(200, listResp.statusCode());
        assertTrue(listResp.body().contains("g1"));

        var dropResp = delete("/graph/g1");
        assertEquals(200, dropResp.statusCode());
        assertTrue(dropResp.body().contains("dropped"));

        var emptyResp = get("/graph");
        assertEquals(200, emptyResp.statusCode());
        assertEquals("[]", emptyResp.body());
    }

    // ── Project Triples ─────────────────────────────────────────

    @Test
    void testProjectTriples() throws Exception {
        var resp = post("/graph/proj/triples",
                """
                [
                    {"s": "http://ex.org/Zeus", "p": "http://www.w3.org/1999/02/22-rdf-syntax-ns#type", "o": "http://ex.org/God"},
                    {"s": "http://ex.org/Athena", "p": "http://www.w3.org/1999/02/22-rdf-syntax-ns#type", "o": "http://ex.org/God"}
                ]
                """);
        assertEquals(200, resp.statusCode());
        assertTrue(resp.body().contains("\"tripleCount\":2"));

        var queryResp = post("/graph/proj/query",
                """
                {"sparql": "SELECT ?x WHERE { ?x <http://www.w3.org/1999/02/22-rdf-syntax-ns#type> <http://ex.org/God> }"}
                """);
        assertTrue(queryResp.body().contains("Zeus"));
    }

    // ── SHACL Validation ────────────────────────────────────────

    @Test
    void testValidate_conforms() throws Exception {
        post("/graph/valid/turtle",
                "{\"turtle\": \"@prefix ex: <http://ex.org/> . @prefix sh: <http://www.w3.org/ns/shacl#> . @prefix xsd: <http://www.w3.org/2001/XMLSchema#> . ex:PersonShape a sh:NodeShape ; sh:targetClass ex:Person ; sh:property [ sh:path ex:name ; sh:minCount 1 ; sh:datatype xsd:string ] . ex:Alice a ex:Person ; ex:name \\\"Alice\\\" .\"}");

        var resp = post("/graph/valid/validate", "{}");
        assertEquals(200, resp.statusCode());
        assertTrue(resp.body().contains("conforms"));
    }

    @Test
    void testValidate_violation() throws Exception {
        post("/graph/viol/turtle",
                "{\"turtle\": \"@prefix ex: <http://ex.org/> . @prefix sh: <http://www.w3.org/ns/shacl#> . ex:PersonShape a sh:NodeShape ; sh:targetClass ex:Person ; sh:property [ sh:path ex:name ; sh:minCount 1 ; sh:message \\\"Person must have a name\\\" ] . ex:Alice a ex:Person .\"}");

        var resp = post("/graph/viol/validate", "{}");
        assertEquals(200, resp.statusCode());
        assertTrue(resp.body().contains("Violation"));
        assertTrue(resp.body().contains("Alice"));
    }

    // ── Error handling ──────────────────────────────────────────

    @Test
    void testQuery_nonExistentGraph() throws Exception {
        var resp = post("/graph/no_such_graph/query",
                "{\"sparql\": \"SELECT * WHERE { ?s ?p ?o }\"}");
        assertEquals(404, resp.statusCode());
        assertTrue(resp.body().contains("not found"));
    }

    @Test
    void testQuery_invalidSparql() throws Exception {
        post("/graph/err/turtle",
                "{\"turtle\": \"@prefix ex: <http://ex.org/> . ex:Zeus a ex:God .\"}");

        var resp = post("/graph/err/query",
                "{\"sparql\": \"NOT VALID SPARQL\"}");
        assertEquals(400, resp.statusCode());
        assertTrue(resp.body().contains("\"error\""), "Error response should be JSON with error field");
    }

    @Test
    void testQuery_withProfile() throws Exception {
        post("/graph/prof/turtle",
                """
                {"turtle": "@prefix ex: <http://ex.org/> . @prefix rdfs: <http://www.w3.org/2000/01/rdf-schema#> . ex:Zeus a ex:God . ex:God rdfs:subClassOf ex:Being ."}
                """);

        var resp = post("/graph/prof/query",
                "{\"sparql\": \"SELECT ?x WHERE { ?x a <http://ex.org/Being> }\", \"profile\": \"RDFS\"}");
        assertEquals(200, resp.statusCode());
        assertTrue(resp.body().contains("Zeus"));
    }

    @Test
    void testAddTurtle_batch() throws Exception {
        var resp = post("/graph/batch/turtle",
                """
                {"turtles": [
                    "@prefix ex: <http://ex.org/> . ex:Zeus a ex:God .",
                    "@prefix ex: <http://ex.org/> . ex:Athena a ex:God .",
                    "@prefix ex: <http://ex.org/> . ex:Hercules a ex:Hero ."
                ]}
                """);
        assertEquals(200, resp.statusCode());

        var queryResp = post("/graph/batch/query",
                "{\"sparql\": \"SELECT ?x WHERE { ?x a <http://ex.org/God> }\"}");
        assertTrue(queryResp.body().contains("Zeus"));
        assertTrue(queryResp.body().contains("Athena"));
    }

    @Test
    void testAddTurtle_batchWithSingle() throws Exception {
        // Both "turtle" and "turtles" in the same request
        var resp = post("/graph/both/turtle",
                """
                {"turtle": "@prefix ex: <http://ex.org/> . ex:Zeus a ex:God .",
                 "turtles": ["@prefix ex: <http://ex.org/> . ex:Athena a ex:God ."]}
                """);
        assertEquals(200, resp.statusCode());

        var triples = get("/graph/both/triples");
        assertTrue(triples.body().contains("Zeus"));
        assertTrue(triples.body().contains("Athena"));
    }

    @Test
    void testValidate_emptyBody() throws Exception {
        post("/graph/val_empty/turtle",
                "{\"turtle\": \"@prefix ex: <http://ex.org/> . ex:Zeus a ex:God .\"}");

        // validate with empty string body (no JSON)
        var resp = post("/graph/val_empty/validate", "");
        assertEquals(200, resp.statusCode());
    }

    // ── validateWithRules ───────────────────────────────────────

    @Test
    void testValidateWithRules_ephemeralInference() throws Exception {
        post("/graph/vwr/turtle", """
                {"turtle": "@prefix ex: <http://ex.org/> . @prefix rdfs: <http://www.w3.org/2000/01/rdf-schema#> . @prefix sh: <http://www.w3.org/ns/shacl#> . ex:butter a ex:Dairy . ex:Dairy rdfs:subClassOf ex:AnimalProduct . ex:lasagna ex:claims ex:vegan ; ex:contains ex:butter . ex:VeganShape a sh:NodeShape ; sh:targetSubjectsOf ex:claims ; sh:sparql [ sh:message \\"Contains an animal product\\" ; sh:select \\"PREFIX ex: <http://ex.org/> SELECT $this ?value WHERE { $this ex:contains ?value . ?value a ex:AnimalProduct . }\\" ] ."}
                """);

        var withRules = post("/graph/vwr/validateWithRules", "{\"profile\": \"RDFS\"}");
        assertEquals(200, withRules.statusCode());
        assertTrue(withRules.body().contains("Violation"));
        assertTrue(withRules.body().contains("animal product"));

        // ephemeral: plain validate on the untouched graph still conforms
        var plain = post("/graph/vwr/validate", "");
        assertTrue(plain.body().contains("INFO"));
    }

    // ── explain ─────────────────────────────────────────────────

    @Test
    void testExplain_derivationTrace() throws Exception {
        post("/graph/expl/turtle", """
                {"turtle": "@prefix ex: <http://ex.org/> . @prefix rdfs: <http://www.w3.org/2000/01/rdf-schema#> . ex:butter a ex:Dairy . ex:Dairy rdfs:subClassOf ex:AnimalProduct ."}
                """);

        var resp = post("/graph/expl/explain", """
                {"s": "http://ex.org/butter",
                 "p": "http://www.w3.org/1999/02/22-rdf-syntax-ns#type",
                 "o": "http://ex.org/AnimalProduct",
                 "profile": "RDFS"}
                """);
        assertEquals(200, resp.statusCode());
        assertTrue(resp.body().contains("\"kind\":\"derived\""));
        assertTrue(resp.body().contains("\"kind\":\"asserted\""));
    }

    @Test
    void testExplain_notEntailed() throws Exception {
        post("/graph/expl2/turtle", "{\"turtle\": \"@prefix ex: <http://ex.org/> . ex:a ex:p ex:b .\"}");
        var resp = post("/graph/expl2/explain", """
                {"s": "http://ex.org/a", "p": "http://ex.org/q", "o": "http://ex.org/b", "profile": "RDFS"}
                """);
        assertEquals(400, resp.statusCode());
        assertTrue(resp.body().contains("not asserted or entailed"));
    }

    // ── projectTemplate ─────────────────────────────────────────

    @Test
    void testProjectTemplate() throws Exception {
        var resp = post("/graph/tplg/projectTemplate", """
                {
                  "template": {
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
                  },
                  "rows": [
                    { "id": "id", "_labels": ["Thing", "_Scratch"], "prop": ["p1", "p2", "p3"] },
                    { "id": "other", "_labels": ["Thing"], "prop": ["p1"] }
                  ]
                }
                """);
        assertEquals(200, resp.statusCode());
        assertTrue(resp.body().contains("\"rows\":2"));
        assertTrue(resp.body().contains("\"tripleCount\":6")); // (1 type + 3 props) + (1 type + 1 prop)

        var triples = get("/graph/tplg/triples");
        assertTrue(triples.body().contains("http://example.com#thing_other"));
        assertTrue(triples.body().contains("http://example.com#p2"));
        assertFalse(triples.body().contains("_Scratch"));
    }

    @Test
    void testProjectTemplate_templateAsString() throws Exception {
        var resp = post("/graph/tplstr/projectTemplate", """
                {
                  "template": "{\\"subject\\": \\"http://ex.org#n_{id}\\", \\"triples\\": [{\\"predicate\\": \\"http://ex.org#p\\", \\"object\\": \\"{v}\\"}]}",
                  "rows": [ { "id": "1", "v": "hello" } ]
                }
                """);
        assertEquals(200, resp.statusCode());
        assertTrue(resp.body().contains("\"tripleCount\":1"));
    }

    @Test
    void testProjectTemplate_nestedRows() throws Exception {
        // Positional entity rows — what a middleware posts after fetching (s)-[r]->(t) over Bolt
        var resp = post("/graph/tplnest/projectTemplate", """
                {
                  "template": {
                    "subject": "http://ex.org#thing_{_0.id}",
                    "triples": [
                      { "predicate": { "from": "_1._type", "map": {
                          "RELATES_TO": "http://ex.org#relatesTo"
                        }},
                        "object": "http://ex.org#other_{_2.id}",
                        "kind": "iri" }
                    ]
                  },
                  "rows": [
                    { "_0": {"id": "s1"}, "_1": {"_type": "RELATES_TO"}, "_2": {"id": "t1"} }
                  ]
                }
                """);
        assertEquals(200, resp.statusCode());
        assertTrue(resp.body().contains("\"tripleCount\":1"));

        var triples = get("/graph/tplnest/triples");
        assertTrue(triples.body().contains("http://ex.org#relatesTo"));
    }

    @Test
    void testProjectTemplate_missingRows() throws Exception {
        var resp = post("/graph/tplerr/projectTemplate", """
                {"template": {"subject": "http://ex.org#{id}", "triples": [{"predicate": "http://ex.org#p", "object": "{v}"}]}}
                """);
        assertEquals(404, resp.statusCode());
        assertTrue(resp.body().contains("rows"));
    }
}
