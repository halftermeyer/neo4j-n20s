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
                // Strip the leading " before parsing — our convention
                // is that literals start with " in the (s,p,o) model
                String stripped = o.substring(1);
                if (stripped.endsWith("\"")) {
                    stripped = stripped.substring(0, stripped.length() - 1);
                }
                object = parseLiteral(model, stripped);
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
         * Parse a pre-stripped literal value (outer quotes already removed by caller).
         * Input formats:
         *   value              — plain literal
         *   value@en           — language-tagged (trailing @lang)
         *   value^^<datatype>  — typed literal (trailing ^^<uri>)
         */
        private static RDFNode parseLiteral(Model model, String value) {
            // Typed literal: value^^<datatype>
            if (value.contains("^^<") && value.endsWith(">")) {
                int split = value.lastIndexOf("^^<");
                String val = value.substring(0, split);
                String datatype = value.substring(split + 3, value.length() - 1);
                if (datatype.endsWith("integer") || datatype.endsWith("int")) {
                    try { return model.createTypedLiteral(Integer.parseInt(val)); }
                    catch (NumberFormatException e) { /* fall through */ }
                } else if (datatype.endsWith("decimal") || datatype.endsWith("double") || datatype.endsWith("float")) {
                    try { return model.createTypedLiteral(Double.parseDouble(val)); }
                    catch (NumberFormatException e) { /* fall through */ }
                } else if (datatype.endsWith("boolean")) {
                    return model.createTypedLiteral(Boolean.parseBoolean(val));
                }
                return model.createTypedLiteral(val, datatype);
            }
            // Language-tagged: value@lang
            if (value.matches(".*@[a-zA-Z]{2,}$")) {
                int split = value.lastIndexOf('@');
                String val = value.substring(0, split);
                String lang = value.substring(split + 1);
                return model.createLiteral(val, lang);
            }
            // Plain literal
            return model.createLiteral(value);
        }
    }
}
