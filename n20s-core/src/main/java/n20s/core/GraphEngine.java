package n20s.core;

import org.apache.jena.graph.Graph;
import org.apache.jena.query.*;
import org.apache.jena.rdf.model.*;
import org.apache.jena.reasoner.Reasoner;
import org.apache.jena.reasoner.ReasonerRegistry;
import org.apache.jena.reasoner.rulesys.GenericRuleReasoner;
import org.apache.jena.reasoner.rulesys.Rule;
import org.apache.jena.riot.RiotException;
import org.apache.jena.shacl.ShaclValidator;
import org.apache.jena.shacl.Shapes;
import org.apache.jena.shacl.ValidationReport;
import org.apache.jena.shacl.validation.ReportEntry;

import n20s.core.model.*;

import java.io.StringReader;
import java.io.StringWriter;
import java.util.*;

/**
 * Pure Jena reasoning engine — no Neo4j dependency.
 * Every method operates on the shared GraphCatalog.
 */
public final class GraphEngine {

    private GraphEngine() {}

    private static final Map<String, String> VALID_PROFILES = Map.of(
            "RDFS", "RDFS",
            "OWL_MICRO", "OWL_MICRO",
            "OWL_MINI", "OWL_MINI",
            "OWL", "OWL"
    );

    // ── Version ─────────────────────────────────────────────────

    public static VersionResult version(String moduleVersion) {
        String jenaVersion = org.apache.jena.Jena.VERSION;
        return new VersionResult(moduleVersion, jenaVersion);
    }

    // ── List ────────────────────────────────────────────────────

    public static List<GraphInfo> list() {
        List<GraphInfo> result = new ArrayList<>();
        for (String name : GraphCatalog.list()) {
            result.add(new GraphInfo(name, GraphCatalog.tripleCount(name)));
        }
        return result;
    }

    // ── Drop ────────────────────────────────────────────────────

    public static DropResult drop(String name) {
        if (!GraphCatalog.exists(name)) {
            return new DropResult(name, "not found");
        }
        GraphCatalog.drop(name);
        return new DropResult(name, "dropped");
    }

    // ── Add Turtle ──────────────────────────────────────────────

    public static AddTurtleResult addTurtle(String name, String turtle) {
        Model model = GraphCatalog.exists(name)
                ? GraphCatalog.get(name)
                : ModelFactory.createDefaultModel();

        long before = model.size();

        try {
            model.read(new StringReader(turtle), null, "TURTLE");
        } catch (RiotException e) {
            throw new RuntimeException("Invalid Turtle syntax: " + e.getMessage(), e);
        }

        if (!GraphCatalog.exists(name)) {
            GraphCatalog.put(name, model);
        }

        long after = model.size();
        return new AddTurtleResult(name, before, after);
    }

    // ── Project Triples (batch) ─────────────────────────────────

    public static ProjectResult projectTriples(String name, List<String[]> triples) {
        if (triples == null || triples.isEmpty()) {
            return new ProjectResult(name, 0, "empty");
        }

        Model model = ModelFactory.createDefaultModel();
        long count = 0;

        for (String[] spo : triples) {
            Resource subject = TripleParser.parseSubject(model, spo[0]);
            Property predicate = model.createProperty(spo[1]);
            RDFNode object = TripleParser.parseObject(model, spo[2]);
            model.add(subject, predicate, object);
            count++;
        }

        GraphCatalog.put(name, model);
        return new ProjectResult(name, count, "projected");
    }

    // ── Query ───────────────────────────────────────────────────

    public static List<QueryResult> query(String name, String sparql, String reasoningProfile) {
        Model model = GraphCatalog.get(name);

        if (reasoningProfile != null && !reasoningProfile.isEmpty()) {
            Reasoner reasoner = resolveReasoner(reasoningProfile);
            model = ModelFactory.createInfModel(reasoner, model);
        }

        return executeSparqlSelect(sparql, model);
    }

    // ── Query With Rules ────────────────────────────────────────

    public static List<QueryResult> queryWithRules(String name, String sparql,
                                                    String rules, String reasoningProfile) {
        Model model = GraphCatalog.get(name);

        if (reasoningProfile != null && !reasoningProfile.isEmpty()) {
            Reasoner builtinReasoner = resolveReasoner(reasoningProfile);
            model = ModelFactory.createInfModel(builtinReasoner, model);
        }

        List<Rule> ruleList = parseRules(rules);
        GenericRuleReasoner reasoner = new GenericRuleReasoner(ruleList);
        Model infModel = ModelFactory.createInfModel(reasoner, model);

        return executeSparqlSelect(sparql, infModel);
    }

    // ── Construct ───────────────────────────────────────────────

    public static List<TripleResult> construct(String name, String sparql) {
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
            return triples;
        } catch (QueryParseException e) {
            throw new RuntimeException("Invalid SPARQL query: " + e.getMessage(), e);
        }
    }

    // ── Infer ───────────────────────────────────────────────────

    public static InferResult infer(String name, String profile) {
        Model model = GraphCatalog.get(name);
        long before = model.size();

        Reasoner reasoner = resolveReasoner(profile);
        InfModel infModel = ModelFactory.createInfModel(reasoner, model);
        Model materialized = ModelFactory.createDefaultModel();
        materialized.add(infModel.listStatements());
        infModel.close();

        GraphCatalog.put(name, materialized);
        long after = materialized.size();

        return new InferResult(name, before, after, profile.toUpperCase());
    }

    // ── Infer With Rules ────────────────────────────────────────

    public static InferResult inferWithRules(String name, String rules, String reasoningProfile) {
        Model model = GraphCatalog.get(name);
        long before = model.size();

        if (reasoningProfile != null && !reasoningProfile.isEmpty()) {
            Reasoner builtinReasoner = resolveReasoner(reasoningProfile);
            model = ModelFactory.createInfModel(builtinReasoner, model);
        }

        List<Rule> ruleList = parseRules(rules);

        GenericRuleReasoner reasoner = new GenericRuleReasoner(ruleList);
        reasoner.setOWLTranslation(false);
        reasoner.setTransitiveClosureCaching(false);

        InfModel infModel = ModelFactory.createInfModel(reasoner, model);
        Model materialized = ModelFactory.createDefaultModel();
        materialized.add(infModel.listStatements());
        infModel.close();

        GraphCatalog.put(name, materialized);
        long after = materialized.size();

        return new InferResult(name, before, after, "CUSTOM (" + ruleList.size() + " rules)");
    }

    // ── Validate ────────────────────────────────────────────────

    public static List<ValidationResult> validate(String name) {
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
            return results;
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

        return results;
    }

    // ── To Turtle ───────────────────────────────────────────────

    public static TurtleExportResult toTurtle(String name) {
        Model model = GraphCatalog.get(name);
        StringWriter writer = new StringWriter();
        model.write(writer, "TURTLE");
        return new TurtleExportResult(name, model.size(), writer.toString());
    }

    // ── Triples ─────────────────────────────────────────────────

    public static List<TripleResult> triples(String name) {
        Model model = GraphCatalog.get(name);
        List<TripleResult> results = new ArrayList<>();
        StmtIterator it = model.listStatements();
        while (it.hasNext()) {
            Statement stmt = it.next();
            results.add(new TripleResult(
                    stmt.getSubject().toString(),
                    stmt.getPredicate().toString(),
                    TripleParser.formatObject(stmt)
            ));
        }
        return results;
    }

    // ── Helpers ─────────────────────────────────────────────────

    public static Reasoner resolveReasoner(String profile) {
        String key = profile.toUpperCase();
        if (!VALID_PROFILES.containsKey(key)) {
            throw new RuntimeException("Unknown reasoning profile: '" + profile
                    + "'. Valid profiles: RDFS, OWL_MICRO, OWL_MINI, OWL");
        }
        return switch (key) {
            case "OWL_MICRO" -> ReasonerRegistry.getOWLMicroReasoner();
            case "OWL_MINI" -> ReasonerRegistry.getOWLMiniReasoner();
            case "OWL" -> ReasonerRegistry.getOWLReasoner();
            default -> ReasonerRegistry.getRDFSReasoner();
        };
    }

    private static List<Rule> parseRules(String rules) {
        List<Rule> ruleList;
        try {
            ruleList = Rule.parseRules(rules);
        } catch (Rule.ParserException e) {
            throw new RuntimeException("Invalid rule syntax: " + e.getMessage(), e);
        }

        if (ruleList.isEmpty()) {
            throw new RuntimeException("No rules parsed. Jena rule format: [name: (?x p ?y) -> (?x q ?y)]");
        }

        return ruleList;
    }

    private static List<QueryResult> executeSparqlSelect(String sparql, Model model) {
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
                        row.put(var, TripleParser.toLiteralValue(node.asLiteral()));
                    } else {
                        row.put(var, node.toString());
                    }
                }
                results.add(new QueryResult(row));
            }
            return results;
        } catch (QueryParseException e) {
            throw new RuntimeException("Invalid SPARQL query: " + e.getMessage(), e);
        }
    }
}
