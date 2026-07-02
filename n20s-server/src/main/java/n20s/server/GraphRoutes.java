package n20s.server;

import io.javalin.Javalin;
import io.javalin.http.Context;
import n20s.core.GraphEngine;

import java.util.List;
import java.util.Map;

public final class GraphRoutes {

    private GraphRoutes() {}

    public static void register(Javalin app) {
        // Error handling
        app.exception(IllegalArgumentException.class, (e, ctx) -> {
            ctx.status(404).json(Map.of("error", e.getMessage()));
        });
        app.exception(RuntimeException.class, (e, ctx) -> {
            ctx.status(400).json(Map.of("error", e.getMessage()));
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

        // Validate
        app.post("/graph/{name}/validate", ctx ->
                ctx.json(GraphEngine.validate(ctx.pathParam("name"))));
    }

    private static void handleAddTurtle(Context ctx) {
        String name = ctx.pathParam("name");
        var body = ctx.bodyAsClass(TurtleRequest.class);
        ctx.json(GraphEngine.addTurtle(name, body.turtle));
    }

    private static void handleProjectTriples(Context ctx) {
        String name = ctx.pathParam("name");
        var body = ctx.bodyAsClass(TripleRequest[].class);
        List<String[]> triples = new java.util.ArrayList<>();
        for (var t : body) {
            triples.add(new String[]{t.s, t.p, t.o});
        }
        ctx.json(GraphEngine.projectTriples(name, triples));
    }

    private static void handleQuery(Context ctx) {
        String name = ctx.pathParam("name");
        var body = ctx.bodyAsClass(QueryRequest.class);
        ctx.json(GraphEngine.query(name, body.sparql,
                body.reasoningProfile != null ? body.reasoningProfile : ""));
    }

    private static void handleQueryWithRules(Context ctx) {
        String name = ctx.pathParam("name");
        var body = ctx.bodyAsClass(QueryWithRulesRequest.class);
        ctx.json(GraphEngine.queryWithRules(name, body.sparql, body.rules,
                body.reasoningProfile != null ? body.reasoningProfile : ""));
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
        ctx.json(GraphEngine.inferWithRules(name, body.rules,
                body.reasoningProfile != null ? body.reasoningProfile : ""));
    }

    // ── Request DTOs ────────────────────────────────────────────

    public static class TurtleRequest {
        public String turtle;
    }

    public static class TripleRequest {
        public String s;
        public String p;
        public String o;
    }

    public static class QueryRequest {
        public String sparql;
        public String reasoningProfile;
    }

    public static class QueryWithRulesRequest {
        public String sparql;
        public String rules;
        public String reasoningProfile;
    }

    public static class SparqlRequest {
        public String sparql;
    }

    public static class InferRequest {
        public String profile;
    }

    public static class InferWithRulesRequest {
        public String rules;
        public String reasoningProfile;
    }
}
