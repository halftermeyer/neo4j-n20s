package n20s;

import n20s.core.GraphCatalog;
import n20s.core.GraphEngine;
import n20s.core.TemplateEngine;
import org.apache.jena.rdf.model.Model;
import org.neo4j.graphdb.Label;
import org.neo4j.graphdb.Node;
import org.neo4j.procedure.*;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Aggregating function that projects rows (nodes or maps) into a named in-memory
 * RDF graph through a declarative JSON template (MarkLogic TDE / R2RML style).
 *
 * Usage:
 *   MATCH (t:Thing), (tpl:Template {name: 'thing_mapping'})
 *   WITH n20s.graph.projectTemplate('g', tpl.template, t) AS g
 *   RETURN g.graphName, g.tripleCount
 *
 * Rows may be nodes — labels are exposed to the template as {_labels}, the
 * element id as {_elementId} — or maps (e.g. properties(t), or a computed row
 * when the mapping needs Cypher logic).
 */
public class GraphProjectTemplate {

    @UserAggregationFunction("n20s.graph.projectTemplate")
    @Description("Project nodes or maps into a named in-memory RDF graph via a JSON template. Optional ifExists: 'replace' (default), 'append', 'fail'.")
    public ProjectTemplateAggregator create() {
        return new ProjectTemplateAggregator();
    }

    public static class ProjectTemplateAggregator {

        private String graphName;
        private String templateJson;
        private TemplateEngine.Template template;
        private Model model;
        private long rows = 0;
        private long triples = 0;

        @UserAggregationUpdate
        public void update(
                @Name("name") String name,
                @Name("template") String template,
                @Name("row") Object row,
                @Name(value = "ifExists", defaultValue = "replace") String ifExists) {

            if (model == null) {
                graphName = name;
                templateJson = template;
                this.template = TemplateEngine.parse(template);
                model = GraphEngine.resolveModelForWrite(name, ifExists);
            } else if (!graphName.equals(name)) {
                throw new RuntimeException("Mixed graph names in projectTemplate(): started with '"
                        + graphName + "' but received '" + name + "'. Use a single graph name per aggregation.");
            } else if (!templateJson.equals(template)) {
                throw new RuntimeException("Mixed templates in projectTemplate(): use a single template per aggregation.");
            }

            if (row == null) {
                return; // skip null rows silently
            }

            triples += TemplateEngine.expandInto(model, this.template, toRow(row));
            rows++;
        }

        @UserAggregationResult
        public Map<String, Object> result() {
            if (model == null) {
                return Map.of("graphName", "", "rows", 0L, "tripleCount", 0L, "status", "empty");
            }

            if (!GraphCatalog.exists(graphName)) {
                GraphCatalog.put(graphName, model);
            }

            return Map.of(
                    "graphName", graphName,
                    "rows", rows,
                    "tripleCount", triples,
                    "status", "projected"
            );
        }

        /** Canonical row conversion: node → properties + _labels + _elementId; map passes through. */
        private static Map<String, Object> toRow(Object row) {
            if (row instanceof Node node) {
                Map<String, Object> r = new LinkedHashMap<>(node.getAllProperties());
                if (r.containsKey(TemplateEngine.LABELS_KEY) || r.containsKey(TemplateEngine.ELEMENT_ID_KEY)) {
                    throw new RuntimeException("Node property collides with reserved template key ('"
                            + TemplateEngine.LABELS_KEY + "' / '" + TemplateEngine.ELEMENT_ID_KEY
                            + "'). Rename the property or project a map instead.");
                }
                List<String> labels = new ArrayList<>();
                for (Label l : node.getLabels()) {
                    labels.add(l.name());
                }
                r.put(TemplateEngine.LABELS_KEY, labels);
                r.put(TemplateEngine.ELEMENT_ID_KEY, node.getElementId());
                return r;
            }
            if (row instanceof Map<?, ?> map) {
                @SuppressWarnings("unchecked")
                Map<String, Object> r = (Map<String, Object>) map;
                return r;
            }
            throw new RuntimeException("projectTemplate row must be a node or a map, got: "
                    + row.getClass().getSimpleName());
        }
    }
}
