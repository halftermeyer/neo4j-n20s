package n20s;

import org.apache.jena.datatypes.TypeMapper;
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
                object = parseLiteralFromSPO(model, o);
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
         * Parse a literal from (s,p,o) string convention.
         * Input formats:
         *   "value"              — plain literal
         *   "value"@en           — language-tagged
         *   "value"^^<datatype>  — typed literal
         */
        private static RDFNode parseLiteralFromSPO(Model model, String o) {
            // Must start with "
            String rest = o.substring(1);

            // Typed literal: "value"^^<datatype>
            int typedIdx = rest.indexOf("\"^^<");
            if (typedIdx >= 0 && rest.endsWith(">")) {
                String val = rest.substring(0, typedIdx);
                String datatypeURI = rest.substring(typedIdx + 4, rest.length() - 1);
                return model.createTypedLiteral(val,
                        TypeMapper.getInstance().getSafeTypeByName(datatypeURI));
            }

            // Language-tagged: "value"@lang
            int langIdx = rest.indexOf("\"@");
            if (langIdx >= 0) {
                String val = rest.substring(0, langIdx);
                String lang = rest.substring(langIdx + 2);
                return model.createLiteral(val, lang);
            }

            // Plain literal: "value"
            if (rest.endsWith("\"")) {
                return model.createLiteral(rest.substring(0, rest.length() - 1));
            }

            // Fallback — treat entire input as plain literal
            return model.createLiteral(rest);
        }
    }
}
