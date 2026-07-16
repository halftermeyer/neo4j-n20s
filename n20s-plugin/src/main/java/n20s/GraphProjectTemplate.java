package n20s;

import n20s.core.GraphCatalog;
import n20s.core.GraphEngine;
import n20s.core.TemplateEngine;
import org.apache.jena.rdf.model.Model;
import org.neo4j.graphdb.Label;
import org.neo4j.graphdb.Node;
import org.neo4j.graphdb.Path;
import org.neo4j.graphdb.Relationship;
import org.neo4j.procedure.*;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Aggregating function that projects rows (nodes, relationships, paths, lists,
 * or maps) into a named in-memory RDF graph through a declarative JSON template
 * (MarkLogic TDE / R2RML style).
 *
 * Usage:
 *   MATCH (t:Thing), (tpl:Template {name: 'thing_mapping'})
 *   WITH n20s.graph.projectTemplate('g', tpl.template, t) AS g
 *   RETURN g.graphName, g.tripleCount
 *
 *   // Entity lists are positional: {_0.id}, {_1._type}, {_2.id}
 *   MATCH (s:Thing)-[r:RELATES_TO]->(t:OtherThing)
 *   WITH n20s.graph.projectTemplate('g', tpl.template, [s, r, t]) AS g
 *   RETURN g.tripleCount
 *
 * Canonical conversions:
 *   node          → {…props, _labels: […], _elementId: "…"}
 *   relationship  → {…props, _type: "…", _elementId: "…", _start: <node row>, _end: <node row>}
 *   list / path   → positional row {_0: …, _1: …, …} (a path alternates node, rel, node, …)
 *   map           → passes through; entity values are converted recursively
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

        /**
         * Canonical row conversion — see class javadoc. Entity lists and paths
         * become positional rows ({_0}, {_1}, …); entity values inside maps and
         * lists are converted recursively.
         */
        private static Map<String, Object> toRow(Object row) {
            if (row instanceof Node node) {
                return nodeRow(node);
            }
            if (row instanceof Relationship rel) {
                return relRow(rel);
            }
            if (row instanceof Path path) {
                Map<String, Object> r = new LinkedHashMap<>();
                int i = 0;
                for (var entity : path) {
                    r.put("_" + (i++), convert(entity));
                }
                return r;
            }
            if (row instanceof List<?> list) {
                Map<String, Object> r = new LinkedHashMap<>();
                for (int i = 0; i < list.size(); i++) {
                    r.put("_" + i, convert(list.get(i)));
                }
                return r;
            }
            if (row instanceof Map<?, ?> map) {
                Map<String, Object> r = new LinkedHashMap<>();
                for (var e : map.entrySet()) {
                    r.put(String.valueOf(e.getKey()), convert(e.getValue()));
                }
                return r;
            }
            throw new RuntimeException("projectTemplate row must be a node, relationship, path,"
                    + " list, or map — got: " + row.getClass().getSimpleName());
        }

        private static Object convert(Object v) {
            if (v instanceof Node node) {
                return nodeRow(node);
            }
            if (v instanceof Relationship rel) {
                return relRow(rel);
            }
            if (v instanceof List<?> list) {
                List<Object> out = new ArrayList<>(list.size());
                for (Object e : list) {
                    out.add(convert(e));
                }
                return out;
            }
            if (v instanceof Map<?, ?> map) {
                Map<String, Object> out = new LinkedHashMap<>();
                for (var e : map.entrySet()) {
                    out.put(String.valueOf(e.getKey()), convert(e.getValue()));
                }
                return out;
            }
            return v;
        }

        private static Map<String, Object> nodeRow(Node node) {
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

        private static Map<String, Object> relRow(Relationship rel) {
            Map<String, Object> r = new LinkedHashMap<>(rel.getAllProperties());
            if (r.containsKey(TemplateEngine.TYPE_KEY) || r.containsKey(TemplateEngine.ELEMENT_ID_KEY)
                    || r.containsKey(TemplateEngine.START_KEY) || r.containsKey(TemplateEngine.END_KEY)) {
                throw new RuntimeException("Relationship property collides with reserved template key ('"
                        + TemplateEngine.TYPE_KEY + "' / '" + TemplateEngine.ELEMENT_ID_KEY + "' / '"
                        + TemplateEngine.START_KEY + "' / '" + TemplateEngine.END_KEY
                        + "'). Rename the property or project a map instead.");
            }
            r.put(TemplateEngine.TYPE_KEY, rel.getType().name());
            r.put(TemplateEngine.ELEMENT_ID_KEY, rel.getElementId());
            r.put(TemplateEngine.START_KEY, nodeRow(rel.getStartNode()));
            r.put(TemplateEngine.END_KEY, nodeRow(rel.getEndNode()));
            return r;
        }
    }
}
