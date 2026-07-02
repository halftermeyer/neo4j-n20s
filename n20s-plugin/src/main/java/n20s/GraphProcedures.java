package n20s;

import n20s.core.GraphEngine;
import n20s.core.model.*;
import org.neo4j.procedure.*;

import java.util.stream.Stream;

public class GraphProcedures {

    // ── n20s.version() ──────────────────────────────────────────

    @Procedure(name = "n20s.version", mode = Mode.READ)
    @Description("Return n20s plugin and Apache Jena versions.")
    public Stream<VersionResult> version() {
        String pluginVersion = getClass().getPackage().getImplementationVersion();
        if (pluginVersion == null) pluginVersion = "dev";
        return Stream.of(GraphEngine.version(pluginVersion));
    }

    // ── n20s.graph.list() ─────────────────────────────────────

    @Procedure(name = "n20s.graph.list", mode = Mode.READ)
    @Description("List all in-memory RDF graphs.")
    public Stream<GraphInfo> list() {
        return GraphEngine.list().stream();
    }

    // ── n20s.graph.drop() ─────────────────────────────────────

    @Procedure(name = "n20s.graph.drop", mode = Mode.READ)
    @Description("Drop a named in-memory RDF graph.")
    public Stream<DropResult> drop(@Name("name") String name) {
        return Stream.of(GraphEngine.drop(name));
    }

    // ── n20s.graph.query() ────────────────────────────────────

    @Procedure(name = "n20s.graph.query", mode = Mode.READ)
    @Description("Run a SPARQL SELECT query on a named in-memory RDF graph. Optional reasoning profile (RDFS, OWL_MICRO, OWL_MINI, OWL) enables backward-chaining inference during query execution.")
    public Stream<QueryResult> query(
            @Name("name") String name,
            @Name("sparql") String sparql,
            @Name(value = "reasoningProfile", defaultValue = "") String reasoningProfile) {
        return GraphEngine.query(name, sparql, reasoningProfile).stream();
    }

    // ── n20s.graph.queryWithRules() ────────────────────────────

    @Procedure(name = "n20s.graph.queryWithRules", mode = Mode.READ)
    @Description("Run a SPARQL SELECT query with custom Jena rules applied via backward chaining. Optional reasoning profile is applied first, then custom rules are layered on top.")
    public Stream<QueryResult> queryWithRules(
            @Name("name") String name,
            @Name("sparql") String sparql,
            @Name("rules") String rules,
            @Name(value = "reasoningProfile", defaultValue = "") String reasoningProfile) {
        return GraphEngine.queryWithRules(name, sparql, rules, reasoningProfile).stream();
    }

    // ── n20s.graph.construct() ─────────────────────────────────

    @Procedure(name = "n20s.graph.construct", mode = Mode.READ)
    @Description("Run a SPARQL CONSTRUCT query, return triples.")
    public Stream<TripleResult> construct(
            @Name("name") String name,
            @Name("sparql") String sparql) {
        return GraphEngine.construct(name, sparql).stream();
    }

    // ── n20s.graph.infer() ────────────────────────────────────

    @Procedure(name = "n20s.graph.infer", mode = Mode.READ)
    @Description("Run RDFS or OWL inference on a named in-memory RDF graph. Profiles: RDFS, OWL_MICRO, OWL_MINI, OWL.")
    public Stream<InferResult> infer(
            @Name("name") String name,
            @Name("profile") String profile) {
        return Stream.of(GraphEngine.infer(name, profile));
    }

    // ── n20s.graph.inferWithRules() ────────────────────────────

    @Procedure(name = "n20s.graph.inferWithRules", mode = Mode.READ)
    @Description("Run custom rule-based inference on a named in-memory RDF graph using Jena rule syntax. Optional reasoning profile is applied first, then custom rules are layered on top.")
    public Stream<InferResult> inferWithRules(
            @Name("name") String name,
            @Name("rules") String rules,
            @Name(value = "reasoningProfile", defaultValue = "") String reasoningProfile) {
        return Stream.of(GraphEngine.inferWithRules(name, rules, reasoningProfile));
    }

    // ── n20s.graph.validate() ────────────────────────────────

    @Procedure(name = "n20s.graph.validate", mode = Mode.READ)
    @Description("Validate a named in-memory RDF graph against SHACL shapes contained in the same graph.")
    public Stream<ValidationResult> validate(@Name("name") String name) {
        return GraphEngine.validate(name).stream();
    }

    // ── n20s.graph.addTurtle() ─────────────────────────────────

    @Procedure(name = "n20s.graph.addTurtle", mode = Mode.READ)
    @Description("Parse a Turtle string and add its triples to a named in-memory RDF graph. Creates the graph if it doesn't exist.")
    public Stream<AddTurtleResult> addTurtle(
            @Name("name") String name,
            @Name("turtle") String turtle) {
        return Stream.of(GraphEngine.addTurtle(name, turtle));
    }

    // ── n20s.graph.toTurtle() ─────────────────────────────────

    @Procedure(name = "n20s.graph.toTurtle", mode = Mode.READ)
    @Description("Serialize a named in-memory RDF graph as a Turtle string.")
    public Stream<TurtleExportResult> toTurtle(@Name("name") String name) {
        return Stream.of(GraphEngine.toTurtle(name));
    }

    // ── n20s.graph.triples() ──────────────────────────────────

    @Procedure(name = "n20s.graph.triples", mode = Mode.READ)
    @Description("Stream all triples from a named in-memory RDF graph.")
    public Stream<TripleResult> triples(@Name("name") String name) {
        return GraphEngine.triples(name).stream();
    }
}
