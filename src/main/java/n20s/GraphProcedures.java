package n20s;

import org.apache.jena.graph.Graph;
import org.apache.jena.query.*;
import org.apache.jena.rdf.model.*;
import org.apache.jena.reasoner.ReasonerRegistry;
import org.apache.jena.reasoner.Reasoner;
import org.apache.jena.riot.RiotException;
import org.apache.jena.shacl.ShaclValidator;
import org.apache.jena.shacl.Shapes;
import org.apache.jena.shacl.ValidationReport;
import org.apache.jena.shacl.validation.ReportEntry;
import org.neo4j.procedure.*;

import java.util.*;
import java.util.stream.Stream;

public class GraphProcedures {

    // ── n20s.version() ──────────────────────────────────────────

    public static class VersionResult {
        public String version;
        public String jenaVersion;

        public VersionResult(String version, String jenaVersion) {
            this.version = version;
            this.jenaVersion = jenaVersion;
        }
    }

    @Procedure(name = "n20s.version", mode = Mode.READ)
    @Description("Return n20s plugin and Apache Jena versions.")
    public Stream<VersionResult> version() {
        String jenaVersion = org.apache.jena.Jena.VERSION;
        return Stream.of(new VersionResult("0.1.0", jenaVersion));
    }

    // ── n20s.graph.list() ─────────────────────────────────────

    public static class GraphInfo {
        public String graphName;
        public long tripleCount;

        public GraphInfo(String name, long count) {
            this.graphName = name;
            this.tripleCount = count;
        }
    }

    @Procedure(name = "n20s.graph.list", mode = Mode.READ)
    @Description("List all in-memory RDF graphs.")
    public Stream<GraphInfo> list() {
        return GraphCatalog.list().stream()
                .map(name -> new GraphInfo(name, GraphCatalog.tripleCount(name)));
    }

    // ── n20s.graph.drop() ─────────────────────────────────────

    public static class DropResult {
        public String graphName;
        public String status;

        public DropResult(String name, String status) {
            this.graphName = name;
            this.status = status;
        }
    }

    @Procedure(name = "n20s.graph.drop", mode = Mode.READ)
    @Description("Drop a named in-memory RDF graph.")
    public Stream<DropResult> drop(@Name("name") String name) {
        if (!GraphCatalog.exists(name)) {
            return Stream.of(new DropResult(name, "not found"));
        }
        GraphCatalog.drop(name);
        return Stream.of(new DropResult(name, "dropped"));
    }

    // ── n20s.graph.query() ────────────────────────────────────

    public static class QueryResult {
        public Map<String, Object> row;

        public QueryResult(Map<String, Object> row) {
            this.row = row;
        }
    }

    @Procedure(name = "n20s.graph.query", mode = Mode.READ)
    @Description("Run a SPARQL SELECT query on a named in-memory RDF graph. Optional reasoning profile (RDFS, OWL_MICRO, OWL_MINI, OWL) enables backward-chaining inference during query execution — no separate infer() step needed.")
    public Stream<QueryResult> query(
            @Name("name") String name,
            @Name("sparql") String sparql,
            @Name(value = "reasoningProfile", defaultValue = "") String reasoningProfile) {

        Model model = GraphCatalog.get(name);

        // If reasoning profile specified, wrap in InfModel for backward chaining
        if (reasoningProfile != null && !reasoningProfile.isEmpty()) {
            Reasoner reasoner = resolveReasoner(reasoningProfile);
            model = ModelFactory.createInfModel(reasoner, model);
        }

        try (QueryExecution qe = QueryExecutionFactory.create(sparql, model)) {
            ResultSet rs = qe.execSelect();
            List<QueryResult> results = new ArrayList<>();
            List<String> vars = rs.getResultVars();

            while (rs.hasNext()) {
                QuerySolution sol = rs.next();
                Map<String, Object> row = new LinkedHashMap<>();
                for (String var : vars) {
                    RDFNode node = sol.get(var);
                    if (node == null) {
                        row.put(var, null);
                    } else if (node.isLiteral()) {
                        row.put(var, toLiteralValue(node.asLiteral()));
                    } else {
                        row.put(var, node.toString());
                    }
                }
                results.add(new QueryResult(row));
            }
            return results.stream();
        } catch (QueryParseException e) {
            throw new RuntimeException("Invalid SPARQL query: " + e.getMessage(), e);
        }
    }

    // ── n20s.graph.construct() ─────────────────────────────────

    public static class TripleResult {
        public String subject;
        public String predicate;
        public String object;

        public TripleResult(String s, String p, String o) {
            this.subject = s;
            this.predicate = p;
            this.object = o;
        }
    }

    @Procedure(name = "n20s.graph.construct", mode = Mode.READ)
    @Description("Run a SPARQL CONSTRUCT query, return triples.")
    public Stream<TripleResult> construct(
            @Name("name") String name,
            @Name("sparql") String sparql) {

        Model model = GraphCatalog.get(name);

        try (QueryExecution qe = QueryExecutionFactory.create(sparql, model)) {
            Model result = qe.execConstruct();
            List<TripleResult> triples = new ArrayList<>();
            StmtIterator it = result.listStatements();
            while (it.hasNext()) {
                Statement stmt = it.next();
                triples.add(new TripleResult(
                        stmt.getSubject().toString(),
                        stmt.getPredicate().toString(),
                        stmt.getObject().toString()
                ));
            }
            return triples.stream();
        } catch (QueryParseException e) {
            throw new RuntimeException("Invalid SPARQL query: " + e.getMessage(), e);
        }
    }

    // ── n20s.graph.infer() ────────────────────────────────────

    public static class InferResult {
        public String graphName;
        public long triplesBefore;
        public long triplesAfter;
        public long newTriples;
        public String profile;

        public InferResult(String name, long before, long after, String profile) {
            this.graphName = name;
            this.triplesBefore = before;
            this.triplesAfter = after;
            this.newTriples = after - before;
            this.profile = profile;
        }
    }

    @Procedure(name = "n20s.graph.infer", mode = Mode.READ)
    @Description("Run RDFS or OWL inference on a named in-memory RDF graph. Profiles: RDFS, OWL_MICRO, OWL_MINI, OWL.")
    public Stream<InferResult> infer(
            @Name("name") String name,
            @Name("profile") String profile) {

        Model model = GraphCatalog.get(name);
        long before = model.size();

        Reasoner reasoner = resolveReasoner(profile);

        // Create inference model and replace the base model in the catalog
        InfModel infModel = ModelFactory.createInfModel(reasoner, model);
        // Materialize inferences into a plain model (faster subsequent queries)
        Model materialized = ModelFactory.createDefaultModel();
        materialized.add(infModel.listStatements());
        infModel.close();

        GraphCatalog.put(name, materialized);
        long after = materialized.size();

        return Stream.of(new InferResult(name, before, after, profile.toUpperCase()));
    }

    // ── n20s.graph.validate() ────────────────────────────────

    public static class ValidationResult {
        public String focusNode;
        public String path;
        public String severity;
        public String message;
        public String value;
        public String sourceShape;

        public ValidationResult(String focusNode, String path, String severity,
                                String message, String value, String sourceShape) {
            this.focusNode = focusNode;
            this.path = path;
            this.severity = severity;
            this.message = message;
            this.value = value;
            this.sourceShape = sourceShape;
        }
    }

    @Procedure(name = "n20s.graph.validate", mode = Mode.READ)
    @Description("Validate a named in-memory RDF graph against SHACL shapes contained in the same graph.")
    public Stream<ValidationResult> validate(@Name("name") String name) {

        Model model = GraphCatalog.get(name);
        Graph dataGraph = model.getGraph();

        Shapes shapes;
        try {
            shapes = Shapes.parse(dataGraph);
        } catch (Exception e) {
            throw new RuntimeException("Failed to parse SHACL shapes: " + e.getMessage(), e);
        }

        ValidationReport report = ShaclValidator.get().validate(shapes, dataGraph);

        List<ValidationResult> results = new ArrayList<>();

        if (report.conforms()) {
            results.add(new ValidationResult(
                    null, null, "INFO", "Validation passed — graph conforms to all shapes.",
                    null, null));
            return results.stream();
        }

        for (ReportEntry entry : report.getEntries()) {
            String focusNode = entry.focusNode() != null ? entry.focusNode().toString() : null;
            String path = entry.resultPath() != null ? entry.resultPath().toString() : null;
            String severity = "Violation";
            if (entry.severity() != null) {
                String sevStr = entry.severity().level().getURI();
                if (sevStr.contains("#")) severity = sevStr.substring(sevStr.lastIndexOf('#') + 1);
                else severity = sevStr;
            }
            String message = entry.message() != null ? entry.message() : "";
            String value = entry.value() != null ? entry.value().toString() : null;
            String sourceShape = entry.source() != null ? entry.source().toString() : null;

            results.add(new ValidationResult(focusNode, path, severity, message, value, sourceShape));
        }

        return results.stream();
    }

    // ── n20s.graph.addTurtle() ─────────────────────────────────

    public static class AddTurtleResult {
        public String graphName;
        public long triplesBefore;
        public long triplesAfter;
        public long added;

        public AddTurtleResult(String name, long before, long after) {
            this.graphName = name;
            this.triplesBefore = before;
            this.triplesAfter = after;
            this.added = after - before;
        }
    }

    @Procedure(name = "n20s.graph.addTurtle", mode = Mode.READ)
    @Description("Parse a Turtle string and add its triples to a named in-memory RDF graph. Creates the graph if it doesn't exist.")
    public Stream<AddTurtleResult> addTurtle(
            @Name("name") String name,
            @Name("turtle") String turtle) {

        Model model = GraphCatalog.exists(name)
                ? GraphCatalog.get(name)
                : ModelFactory.createDefaultModel();

        long before = model.size();

        try {
            model.read(new java.io.StringReader(turtle), null, "TURTLE");
        } catch (RiotException e) {
            throw new RuntimeException("Invalid Turtle syntax: " + e.getMessage(), e);
        }

        if (!GraphCatalog.exists(name)) {
            GraphCatalog.put(name, model);
        }

        long after = model.size();
        return Stream.of(new AddTurtleResult(name, before, after));
    }

    // ── n20s.graph.toTurtle() ─────────────────────────────────

    public static class TurtleExportResult {
        public String graphName;
        public long tripleCount;
        public String turtle;

        public TurtleExportResult(String name, long count, String turtle) {
            this.graphName = name;
            this.tripleCount = count;
            this.turtle = turtle;
        }
    }

    @Procedure(name = "n20s.graph.toTurtle", mode = Mode.READ)
    @Description("Serialize a named in-memory RDF graph as a Turtle string.")
    public Stream<TurtleExportResult> toTurtle(@Name("name") String name) {
        Model model = GraphCatalog.get(name);
        java.io.StringWriter writer = new java.io.StringWriter();
        model.write(writer, "TURTLE");
        return Stream.of(new TurtleExportResult(name, model.size(), writer.toString()));
    }

    // ── n20s.graph.triples() ──────────────────────────────────

    @Procedure(name = "n20s.graph.triples", mode = Mode.READ)
    @Description("Stream all triples from a named in-memory RDF graph.")
    public Stream<TripleResult> triples(@Name("name") String name) {
        Model model = GraphCatalog.get(name);
        List<TripleResult> results = new ArrayList<>();
        StmtIterator it = model.listStatements();
        while (it.hasNext()) {
            Statement stmt = it.next();
            String obj;
            if (stmt.getObject().isLiteral()) {
                Literal lit = stmt.getObject().asLiteral();
                if (lit.getLanguage() != null && !lit.getLanguage().isEmpty()) {
                    obj = "\"" + lit.getString() + "\"@" + lit.getLanguage();
                } else if (lit.getDatatypeURI() != null) {
                    obj = "\"" + lit.getString() + "\"^^<" + lit.getDatatypeURI() + ">";
                } else {
                    obj = "\"" + lit.getString() + "\"";
                }
            } else {
                obj = stmt.getObject().toString();
            }
            results.add(new TripleResult(
                    stmt.getSubject().toString(),
                    stmt.getPredicate().toString(),
                    obj
            ));
        }
        return results.stream();
    }

    // ── Helpers ──────────────────────────────────────────────────

    private static final Map<String, String> VALID_PROFILES = Map.of(
            "RDFS", "RDFS",
            "OWL_MICRO", "OWL_MICRO",
            "OWL_MINI", "OWL_MINI",
            "OWL", "OWL"
    );

    private static Reasoner resolveReasoner(String profile) {
        String key = profile.toUpperCase();
        if (!VALID_PROFILES.containsKey(key)) {
            throw new RuntimeException("Unknown reasoning profile: '" + profile
                    + "'. Valid profiles: RDFS, OWL_MICRO, OWL_MINI, OWL");
        }
        switch (key) {
            case "RDFS":      return ReasonerRegistry.getRDFSReasoner();
            case "OWL_MICRO": return ReasonerRegistry.getOWLMicroReasoner();
            case "OWL_MINI":  return ReasonerRegistry.getOWLMiniReasoner();
            case "OWL":       return ReasonerRegistry.getOWLReasoner();
            default:          return ReasonerRegistry.getRDFSReasoner();
        }
    }

    /**
     * Convert a Jena Literal value to a Neo4j-compatible type.
     * Neo4j supports: Long, Double, Boolean, String.
     */
    private static Object toLiteralValue(Literal lit) {
        Object val = lit.getValue();
        if (val instanceof Integer || val instanceof Long) {
            return ((Number) val).longValue();
        } else if (val instanceof Float || val instanceof Double) {
            return ((Number) val).doubleValue();
        } else if (val instanceof java.math.BigDecimal) {
            return ((java.math.BigDecimal) val).doubleValue();
        } else if (val instanceof java.math.BigInteger) {
            return ((java.math.BigInteger) val).longValue();
        } else if (val instanceof Boolean) {
            return val;
        } else if (val instanceof Number) {
            return ((Number) val).doubleValue();
        } else {
            return val.toString();
        }
    }
}
