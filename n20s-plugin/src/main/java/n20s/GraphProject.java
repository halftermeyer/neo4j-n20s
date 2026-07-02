package n20s;

import n20s.core.GraphCatalog;
import n20s.core.TripleParser;
import org.apache.jena.rdf.model.*;
import org.neo4j.procedure.*;

import java.util.Map;

/**
 * Aggregating function that projects (s, p, o) rows into a named in-memory RDF graph.
 */
public class GraphProject {

    @UserAggregationFunction("n20s.graph.project")
    @Description("Project (s, p, o) rows into a named in-memory RDF graph.")
    public ProjectAggregator create() {
        return new ProjectAggregator();
    }

    public static class ProjectAggregator {

        private String graphName;
        private Model model;
        private long count = 0;

        @UserAggregationUpdate
        public void update(
                @Name("name") String name,
                @Name("s") String s,
                @Name("p") String p,
                @Name("o") String o) {

            if (model == null) {
                graphName = name;
                model = ModelFactory.createDefaultModel();
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

            GraphCatalog.put(graphName, model);

            return Map.of(
                    "graphName", graphName,
                    "tripleCount", count,
                    "status", "projected"
            );
        }
    }
}
