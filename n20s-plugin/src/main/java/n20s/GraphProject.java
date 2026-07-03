package n20s;

import n20s.core.GraphCatalog;
import n20s.core.GraphEngine;
import n20s.core.TripleParser;
import org.apache.jena.rdf.model.*;
import org.neo4j.procedure.*;

import java.util.Map;

/**
 * Aggregating function that projects (s, p, o) rows into a named in-memory RDF graph.
 *
 * Usage:
 *   WITH n20s.graph.project('g', s, p, o) AS g              — replaces existing graph (default)
 *   WITH n20s.graph.project('g', s, p, o, 'append') AS g    — appends to existing graph
 *   WITH n20s.graph.project('g', s, p, o, 'fail') AS g      — errors if graph exists
 */
public class GraphProject {

    @UserAggregationFunction("n20s.graph.project")
    @Description("Project (s, p, o) rows into a named in-memory RDF graph. Optional ifExists: 'replace' (default), 'append', 'fail'.")
    public ProjectAggregator create() {
        return new ProjectAggregator();
    }

    public static class ProjectAggregator {

        private String graphName;
        private String ifExists;
        private Model model;
        private long count = 0;

        @UserAggregationUpdate
        public void update(
                @Name("name") String name,
                @Name("s") String s,
                @Name("p") String p,
                @Name("o") String o,
                @Name(value = "ifExists", defaultValue = "replace") String ifExists) {

            if (model == null) {
                graphName = name;
                this.ifExists = ifExists;
                model = GraphEngine.resolveModelForWrite(name, ifExists);
            } else if (!graphName.equals(name)) {
                throw new RuntimeException("Mixed graph names in project(): started with '"
                        + graphName + "' but received '" + name + "'. Use a single graph name per aggregation.");
            }

            if (p == null) {
                throw new IllegalArgumentException("Triple predicate (p) must not be null");
            }

            Resource subject = TripleParser.parseSubject(model, s);
            Property predicate = model.createProperty(p);
            RDFNode object = TripleParser.parseObject(model, o);

            model.add(subject, predicate, object);
            count++;
        }

        @UserAggregationResult
        public Map<String, Object> result() {
            if (model == null) {
                return Map.of("graphName", "", "tripleCount", 0L, "status", "empty");
            }

            if (!GraphCatalog.exists(graphName)) {
                GraphCatalog.put(graphName, model);
            }

            return Map.of(
                    "graphName", graphName,
                    "tripleCount", count,
                    "status", "projected"
            );
        }
    }
}
