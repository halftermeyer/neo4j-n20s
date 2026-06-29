package n20s;

import org.apache.jena.rdf.model.*;
import org.neo4j.procedure.*;

import java.util.Map;

/**
 * Aggregating function that projects (s, p, o) rows into a named in-memory RDF graph.
 *
 * Usage:
 *   MATCH (t:Triple)
 *   WITH t.s AS s, t.p AS p, t.o AS o
 *   WITH n20s.graph.project('myGraph', s, p, o) AS g
 *   RETURN g.graphName, g.tripleCount
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

            Resource subject;
            if (s.startsWith("_:")) {
                subject = model.createResource(new AnonId(s.substring(2)));
            } else {
                subject = model.createResource(s);
            }

            Property predicate = model.createProperty(p);

            RDFNode object;
            if (o.startsWith("\"")) {
                object = parseLiteral(model, o);
            } else if (o.startsWith("_:")) {
                object = model.createResource(new AnonId(o.substring(2)));
            } else {
                object = model.createResource(o);
            }

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

        /**
         * Parse RDF literal from N-Triples-style string:
         *   "value" — plain literal
         *   "value"@en — language-tagged
         *   "value"^^<datatype> — typed literal
         */
        private static RDFNode parseLiteral(Model model, String raw) {
            if (raw.contains("\"^^<")) {
                int split = raw.lastIndexOf("\"^^<");
                String value = raw.substring(1, split);
                String datatype = raw.substring(split + 4, raw.length() - 1);
                // Parse typed literals properly for SHACL (needs actual Java types)
                if (datatype.endsWith("integer") || datatype.endsWith("int")) {
                    try { return model.createTypedLiteral(Integer.parseInt(value)); }
                    catch (NumberFormatException e) { /* fall through */ }
                } else if (datatype.endsWith("decimal") || datatype.endsWith("double") || datatype.endsWith("float")) {
                    try { return model.createTypedLiteral(Double.parseDouble(value)); }
                    catch (NumberFormatException e) { /* fall through */ }
                } else if (datatype.endsWith("boolean")) {
                    return model.createTypedLiteral(Boolean.parseBoolean(value));
                }
                return model.createTypedLiteral(value, datatype);
            }
            if (raw.contains("\"@")) {
                int split = raw.lastIndexOf("\"@");
                String value = raw.substring(1, split);
                String lang = raw.substring(split + 2);
                return model.createLiteral(value, lang);
            }
            // Plain literal — strip quotes
            String value = raw;
            if (value.startsWith("\"") && value.endsWith("\"")) {
                value = value.substring(1, value.length() - 1);
            }
            return model.createLiteral(value);
        }
    }
}
