package n20s;

import n20s.core.GraphCatalog;
import n20s.core.GraphEngine;
import org.apache.jena.rdf.model.Model;
import org.apache.jena.riot.RiotException;
import org.neo4j.procedure.*;

import java.io.StringReader;
import java.util.Map;

/**
 * Aggregating function that collects Turtle strings into a named in-memory RDF graph.
 *
 * Usage:
 *   MATCH (i:Ingredient) WHERE i.turtle IS NOT NULL
 *   WITH n20s.graph.addTurtle('g', i.turtle) AS g
 *   RETURN g.graphName, g.tripleCount
 *
 *   // With ifExists flag:
 *   WITH n20s.graph.addTurtle('g', i.turtle, 'replace') AS g   — replaces existing graph
 *   WITH n20s.graph.addTurtle('g', i.turtle, 'fail') AS g      — errors if graph exists
 */
public class GraphAddTurtle {

    @UserAggregationFunction("n20s.graph.addTurtle")
    @Description("Collect Turtle strings into a named in-memory RDF graph. Optional ifExists: 'append' (default), 'replace', 'fail'.")
    public AddTurtleAggregator create() {
        return new AddTurtleAggregator();
    }

    public static class AddTurtleAggregator {

        private String graphName;
        private Model model;
        private long triplesBefore;

        @UserAggregationUpdate
        public void update(
                @Name("name") String name,
                @Name("turtle") String turtle,
                @Name(value = "ifExists", defaultValue = "append") String ifExists) {

            if (model == null) {
                graphName = name;
                model = GraphEngine.resolveModelForWrite(name, ifExists);
                triplesBefore = model.size();
            } else if (!graphName.equals(name)) {
                throw new RuntimeException("Mixed graph names in addTurtle(): started with '"
                        + graphName + "' but received '" + name + "'. Use a single graph name per aggregation.");
            }

            if (turtle == null || turtle.isBlank()) {
                return; // skip null/empty turtle strings silently
            }

            try {
                model.read(new StringReader(turtle), null, "TURTLE");
            } catch (RiotException e) {
                throw new RuntimeException("Invalid Turtle syntax: " + e.getMessage(), e);
            }
        }

        @UserAggregationResult
        public Map<String, Object> result() {
            if (model == null) {
                return Map.of("graphName", "", "tripleCount", 0L, "added", 0L, "status", "empty");
            }

            if (!GraphCatalog.exists(graphName)) {
                GraphCatalog.put(graphName, model);
            }

            long triplesAfter = model.size();
            return Map.of(
                    "graphName", graphName,
                    "tripleCount", triplesAfter,
                    "added", triplesAfter - triplesBefore,
                    "status", "projected"
            );
        }
    }
}
