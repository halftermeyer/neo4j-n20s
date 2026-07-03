package n20s.server;

import io.javalin.Javalin;
import io.javalin.http.Context;
import n20s.core.GraphEngine;

import java.util.List;
import java.util.Map;

public final class GraphRoutes {

    private GraphRoutes() {}

    public static void register(Javalin app) {
        // JSON error handling for all exceptions — no plain text "Server Error"
        app.exception(IllegalArgumentException.class, (e, ctx) -> {
            ctx.status(404).json(Map.of("error", e.getMessage()));
        });
        app.exception(RuntimeException.class, (e, ctx) -> {
            ctx.status(400).json(Map.of("error", e.getMessage()));
        });
        app.exception(Exception.class, (e, ctx) -> {
            ctx.status(500).json(Map.of("error", e.getMessage() != null ? e.getMessage() : e.getClass().getName()));
        });

        // Version
        app.get("/version", ctx -> ctx.json(GraphEngine.version("server-dev")));

        // Graph management
        app.get("/graph", ctx -> ctx.json(GraphEngine.list()));
        app.delete("/graph/{name}", ctx -> ctx.json(GraphEngine.drop(ctx.pathParam("name"))));

        // Add Turtle
        app.post("/graph/{name}/turtle", GraphRoutes::handleAddTurtle);

        // Export Turtle
        app.get("/graph/{name}/turtle", ctx ->
                ctx.json(GraphEngine.toTurtle(ctx.pathParam("name"))));

        // Project triples (batch)
        app.post("/graph/{name}/triples", GraphRoutes::handleProjectTriples);

        // Stream triples
        app.get("/graph/{name}/triples", ctx ->
                ctx.json(GraphEngine.triples(ctx.pathParam("name"))));

        // Query
        app.post("/graph/{name}/query", GraphRoutes::handleQuery);

        // Query with rules
        app.post("/graph/{name}/queryWithRules", GraphRoutes::handleQueryWithRules);

        // Construct
        app.post("/graph/{name}/construct", GraphRoutes::handleConstruct);

        // Infer
        app.post("/graph/{name}/infer", GraphRoutes::handleInfer);

        // Infer with rules
        app.post("/graph/{name}/inferWithRules", GraphRoutes::handleInferWithRules);

        // Validate — accepts empty body or no body
        app.post("/graph/{name}/validate", ctx ->
                ctx.json(GraphEngine.validate(ctx.pathParam("name"))));
    }

    private static void handleAddTurtle(Context ctx) {
        String name = ctx.pathParam("name");
        var body = ctx.bodyAsClass(TurtleRequest.class);
        String ifExists = body.ifExists != null ? body.ifExists : "append";
        ctx.json(GraphEngine.addTurtle(name, body.turtle, ifExists));
    }

    private static void handleProjectTriples(Context ctx) {
        String name = ctx.pathParam("name");
        var body = ctx.bodyAsClass(TripleRequest[].class);
        String ifExists = ctx.queryParam("ifExists") != null ? ctx.queryParam("ifExists") : "replace";
        List<String[]> triples = new java.util.ArrayList<>();
        for (var t : body) {
            triples.add(new String[]{t.s, t.p, t.o});
        }
        ctx.json(GraphEngine.projectTriples(name, triples, ifExists));
    }

    private static void handleQuery(Context ctx) {
        String name = ctx.pathParam("name");
        var body = ctx.bodyAsClass(QueryRequest.class);
        String profile = body.profile != null ? body.profile : "";
        ctx.json(GraphEngine.query(name, body.sparql, profile));
    }

    private static void handleQueryWithRules(Context ctx) {
        String name = ctx.pathParam("name");
        var body = ctx.bodyAsClass(QueryWithRulesRequest.class);
        String profile = body.profile != null ? body.profile : "";
        ctx.json(GraphEngine.queryWithRules(name, body.sparql, body.rules, profile));
    }

    private static void handleConstruct(Context ctx) {
        String name = ctx.pathParam("name");
        var body = ctx.bodyAsClass(SparqlRequest.class);
        ctx.json(GraphEngine.construct(name, body.sparql));
    }

    private static void handleInfer(Context ctx) {
        String name = ctx.pathParam("name");
        var body = ctx.bodyAsClass(InferRequest.class);
        ctx.json(GraphEngine.infer(name, body.profile));
    }

    private static void handleInferWithRules(Context ctx) {
        String name = ctx.pathParam("name");
        var body = ctx.bodyAsClass(InferWithRulesRequest.class);
        String profile = body.profile != null ? body.profile : "";
        ctx.json(GraphEngine.inferWithRules(name, body.rules, profile));
    }

    // ── Request DTOs ────────────────────────────────────────────

    public static class TurtleRequest {
        public String turtle;
        public String ifExists;
    }

    public static class TripleRequest {
        public String s;
        public String p;
        public String o;
    }

    public static class QueryRequest {
        public String sparql;
        public String profile;
    }

    public static class QueryWithRulesRequest {
        public String sparql;
        public String rules;
        public String profile;
    }

    public static class SparqlRequest {
        public String sparql;
    }

    public static class InferRequest {
        public String profile;
    }

    public static class InferWithRulesRequest {
        public String rules;
        public String profile;
    }
}
