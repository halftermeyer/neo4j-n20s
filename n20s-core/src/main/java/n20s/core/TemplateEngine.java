package n20s.core;

import org.apache.jena.atlas.json.JSON;
import org.apache.jena.atlas.json.JsonObject;
import org.apache.jena.atlas.json.JsonParseException;
import org.apache.jena.atlas.json.JsonValue;
import org.apache.jena.datatypes.TypeMapper;
import org.apache.jena.rdf.model.Model;
import org.apache.jena.rdf.model.Property;
import org.apache.jena.rdf.model.RDFNode;
import org.apache.jena.rdf.model.Resource;

import java.nio.charset.StandardCharsets;
import java.util.AbstractMap;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Template-driven projection — expands rows into RDF triples according to a
 * declarative JSON template, in the spirit of MarkLogic TDEs / R2RML term maps.
 *
 * A row is a {@code Map<String, Object>} of values: scalars, lists, or nested
 * maps (e.g. converted graph entities). Reserved metadata keys on entity rows:
 * {@code _labels}, {@code _elementId} (nodes); {@code _type}, {@code _start},
 * {@code _end}, {@code _elementId} (relationships).
 *
 * Template format:
 * <pre>
 * {
 *   "subject": "http://example.com#thing_{_0.id}",
 *   "triples": [
 *     { "predicate": { "from": "_1._type", "map": { "RELATES_TO": "http://example.com#relatesTo" } },
 *       "object":    "http://example.com#other_{_2.id}",
 *       "kind":      "iri" }
 *   ]
 * }
 * </pre>
 *
 * Semantics:
 * <ul>
 *   <li>Placeholders {@code {name}} are substituted with row values; dotted
 *       paths {@code {a.b}} reach into nested maps.</li>
 *   <li>A list-valued top-level placeholder in the object fans out: one triple
 *       per element. Dotted access into list-of-map elements ({@code {list.key}})
 *       is allowed; all dotted placeholders sharing the list pin to the same
 *       element. At most one list per pattern; lists only fan out in the object
 *       position, from a top-level row key.</li>
 *   <li>A missing/null placeholder value skips the triple pattern (or the whole
 *       row, for subject placeholders) — TDE behavior. A placeholder that
 *       resolves to a map (rather than through it) errors loudly.</li>
 *   <li>{@code kind}: "literal" (default) or "iri". Placeholder values in IRI
 *       templates are percent-encoded (R2RML "IRI-safe").</li>
 *   <li>{@code datatype}: optional XSD datatype IRI for literals. Without it, a
 *       whole-template placeholder like {@code "{age}"} keeps the value's native
 *       type (long, double, boolean); anything else is a plain string literal.</li>
 *   <li>{@code include} / {@code exclude}: optional filters on scalar fan-out
 *       elements.</li>
 *   <li>Spec {@code {from, map}} in object or predicate position: the value(s)
 *       of {@code from} are replaced by their mapped output; values absent from
 *       the map are skipped — the map both renames and filters.</li>
 * </ul>
 */
public final class TemplateEngine {

    private TemplateEngine() {}

    public static final String LABELS_KEY = "_labels";
    public static final String ELEMENT_ID_KEY = "_elementId";
    public static final String TYPE_KEY = "_type";
    public static final String START_KEY = "_start";
    public static final String END_KEY = "_end";

    private static final Pattern PLACEHOLDER =
            Pattern.compile("\\{([A-Za-z_][A-Za-z0-9_]*(?:\\.[A-Za-z_][A-Za-z0-9_]*)*)}");

    // ── Parsed template ─────────────────────────────────────────

    public static final class Template {
        final String subject;
        final List<TriplePattern> patterns;

        Template(String subject, List<TriplePattern> patterns) {
            this.subject = subject;
            this.patterns = patterns;
        }
    }

    static final class TriplePattern {
        final String predicate;              // template form (null when map form)
        final String predFrom;               // map form: source path (null when template form)
        final Map<String, String> predMap;   // map form: value → predicate IRI
        final String objectTemplate;         // template form (null when map form)
        final String mapFrom;                // map form: source path (null when template form)
        final Map<String, String> valueMap;  // map form: element → output
        final boolean iri;
        final String datatype;
        final Set<String> include;
        final Set<String> exclude;

        TriplePattern(String predicate, String predFrom, Map<String, String> predMap,
                      String objectTemplate, String mapFrom, Map<String, String> valueMap,
                      boolean iri, String datatype, Set<String> include, Set<String> exclude) {
            this.predicate = predicate;
            this.predFrom = predFrom;
            this.predMap = predMap;
            this.objectTemplate = objectTemplate;
            this.mapFrom = mapFrom;
            this.valueMap = valueMap;
            this.iri = iri;
            this.datatype = datatype;
            this.include = include;
            this.exclude = exclude;
        }
    }

    // ── Parse ───────────────────────────────────────────────────

    public static Template parse(String templateJson) {
        if (templateJson == null || templateJson.isBlank()) {
            throw new RuntimeException("Template must not be empty");
        }

        JsonObject root;
        try {
            root = JSON.parse(templateJson);
        } catch (JsonParseException e) {
            throw new RuntimeException("Invalid template JSON: " + e.getMessage(), e);
        }

        String subject = requireString(root, "subject");

        JsonValue triplesVal = root.get("triples");
        if (triplesVal == null || !triplesVal.isArray() || triplesVal.getAsArray().isEmpty()) {
            throw new RuntimeException("Template must have a non-empty 'triples' array");
        }

        List<TriplePattern> patterns = new ArrayList<>();
        for (JsonValue tv : triplesVal.getAsArray()) {
            if (!tv.isObject()) {
                throw new RuntimeException("Each entry in 'triples' must be an object");
            }
            patterns.add(parsePattern(tv.getAsObject()));
        }
        return new Template(subject, patterns);
    }

    private static TriplePattern parsePattern(JsonObject p) {
        String kind = optionalString(p, "kind", "literal");
        boolean iri = switch (kind.toLowerCase()) {
            case "iri" -> true;
            case "literal" -> false;
            default -> throw new RuntimeException("Invalid 'kind': '" + kind
                    + "'. Valid kinds: 'iri', 'literal'");
        };

        String datatype = optionalString(p, "datatype", null);
        if (datatype != null && iri) {
            throw new RuntimeException("'datatype' only applies to kind 'literal'");
        }

        Set<String> include = optionalStringSet(p, "include");
        Set<String> exclude = optionalStringSet(p, "exclude");

        // Predicate: template string or {from, map} spec
        JsonValue predVal = p.get("predicate");
        if (predVal == null) {
            throw new RuntimeException("Triple pattern must have a 'predicate'");
        }
        String predicate = null, predFrom = null;
        Map<String, String> predMap = null;
        if (predVal.isString()) {
            predicate = predVal.getAsString().value();
        } else if (predVal.isObject()) {
            var fm = parseFromMap(predVal.getAsObject(), "predicate");
            predFrom = fm.getKey();
            predMap = fm.getValue();
        } else {
            throw new RuntimeException("'predicate' must be a template string or a {from, map} object");
        }

        // Object: template string or {from, map} spec
        JsonValue obj = p.get("object");
        if (obj == null) {
            throw new RuntimeException("Triple pattern must have an 'object'");
        }
        if (obj.isString()) {
            return new TriplePattern(predicate, predFrom, predMap,
                    obj.getAsString().value(), null, null, iri, datatype, include, exclude);
        }
        if (obj.isObject()) {
            var fm = parseFromMap(obj.getAsObject(), "object");
            return new TriplePattern(predicate, predFrom, predMap,
                    null, fm.getKey(), fm.getValue(), iri, datatype, include, exclude);
        }
        throw new RuntimeException("'object' must be a template string or a {from, map} object");
    }

    private static Map.Entry<String, Map<String, String>> parseFromMap(JsonObject spec, String where) {
        String from = requireString(spec, "from");
        JsonValue mapVal = spec.get("map");
        if (mapVal == null || !mapVal.isObject()) {
            throw new RuntimeException("'" + where + "' spec with 'from' must have a 'map' object ({value: output})");
        }
        Map<String, String> valueMap = new LinkedHashMap<>();
        JsonObject m = mapVal.getAsObject();
        for (String key : m.keys()) {
            JsonValue v = m.get(key);
            if (!v.isString()) {
                throw new RuntimeException("'map' values must be strings");
            }
            valueMap.put(key, v.getAsString().value());
        }
        return new AbstractMap.SimpleEntry<>(from, valueMap);
    }

    // ── Expand ──────────────────────────────────────────────────

    /** Expand one row into triples added to the model. Returns the number of triples added. */
    public static long expandInto(Model model, Template tpl, Map<String, Object> row) {
        String subjectIri = substitute(tpl.subject, row, null, null, true, "subject");
        if (subjectIri == null) {
            return 0; // missing subject placeholder → skip row
        }
        Resource subject = model.createResource(subjectIri);
        long count = 0;

        for (TriplePattern p : tpl.patterns) {
            String predicateIri = resolvePredicate(p, row);
            if (predicateIri == null) {
                continue; // missing placeholder or unmapped value → skip pattern
            }
            Property predicate = model.createProperty(predicateIri);

            if (p.mapFrom != null) {
                count += expandMapPattern(model, subject, predicate, p, row);
            } else {
                count += expandTemplatePattern(model, subject, predicate, p, row);
            }
        }
        return count;
    }

    private static String resolvePredicate(TriplePattern p, Map<String, Object> row) {
        if (p.predFrom == null) {
            return substitute(p.predicate, row, null, null, true, "predicate");
        }
        Object v = resolve(p.predFrom, row, null, null, "predicate 'from'");
        if (v == null) {
            return null; // missing value → skip pattern
        }
        if (asList(v) != null) {
            throw new RuntimeException("Predicate 'from' path '" + p.predFrom
                    + "' resolves to a list — predicates must be scalar");
        }
        return p.predMap.get(String.valueOf(v)); // unmapped value → null → skip pattern
    }

    private static long expandTemplatePattern(Model model, Resource subject, Property predicate,
                                              TriplePattern p, Map<String, Object> row) {
        // Find the (at most one) list-valued top-level key — it drives fan-out.
        // Dotted placeholders sharing that key pin to the same element.
        String listName = null;
        Matcher m = PLACEHOLDER.matcher(p.objectTemplate);
        while (m.find()) {
            String path = m.group(1);
            int dot = path.indexOf('.');
            String first = dot < 0 ? path : path.substring(0, dot);
            Object v = row.get(first);
            if (v == null) {
                return 0; // missing property → skip pattern
            }
            if (asList(v) != null) {
                if (listName != null && !listName.equals(first)) {
                    throw new RuntimeException("Triple pattern references two list-valued placeholders ({"
                            + listName + "}, {" + first + "}) — at most one list per pattern");
                }
                listName = first;
            } else if (resolve(path, row, null, null, "object") == null) {
                return 0; // missing nested key → skip pattern
            }
        }

        if (listName == null) {
            RDFNode object = buildObject(model, p, row, null, null);
            if (object == null) {
                return 0;
            }
            model.add(subject, predicate, object);
            return 1;
        }

        long count = 0;
        for (Object el : asList(row.get(listName))) {
            if (el instanceof Map) {
                if (p.include != null || p.exclude != null) {
                    throw new RuntimeException("include/exclude do not apply to map fan-out elements"
                            + " — filter in Cypher and pass a computed row instead");
                }
            } else if (!passesFilters(p, String.valueOf(el))) {
                continue;
            }
            RDFNode object = buildObject(model, p, row, listName, el);
            if (object == null) {
                continue;
            }
            model.add(subject, predicate, object);
            count++;
        }
        return count;
    }

    private static long expandMapPattern(Model model, Resource subject, Property predicate,
                                         TriplePattern p, Map<String, Object> row) {
        Object src = resolve(p.mapFrom, row, null, null, "object 'from'");
        if (src == null) {
            return 0; // missing property → skip pattern
        }
        List<?> elements = asList(src);
        if (elements == null) {
            elements = List.of(src); // scalar → single-element convenience
        }

        long count = 0;
        for (Object el : elements) {
            if (el instanceof Map) {
                throw new RuntimeException("'from' elements must be scalars — got a map;"
                        + " use a dotted path in 'from' or filter in Cypher");
            }
            String key = String.valueOf(el);
            if (!passesFilters(p, key)) {
                continue;
            }
            String out = p.valueMap.get(key);
            if (out == null) {
                continue; // element absent from map → skipped (the map also filters)
            }
            RDFNode object;
            if (p.iri) {
                object = model.createResource(out);
            } else if (p.datatype != null) {
                object = model.createTypedLiteral(out, TypeMapper.getInstance().getSafeTypeByName(p.datatype));
            } else {
                object = model.createLiteral(out);
            }
            model.add(subject, predicate, object);
            count++;
        }
        return count;
    }

    private static RDFNode buildObject(Model model, TriplePattern p, Map<String, Object> row,
                                       String listName, Object element) {
        // Whole-template placeholder + literal kind + no datatype → keep the native value type
        Matcher whole = PLACEHOLDER.matcher(p.objectTemplate);
        if (!p.iri && p.datatype == null && whole.matches()) {
            Object v = resolve(whole.group(1), row, listName, element, "object");
            if (v == null) {
                return null;
            }
            if (v instanceof Number || v instanceof Boolean) {
                return model.createTypedLiteral(v);
            }
            return model.createLiteral(String.valueOf(v));
        }

        // General case: textual substitution (list placeholder pinned to the current element)
        String text = substitute(p.objectTemplate, row, listName, element, p.iri, "object");
        if (text == null) {
            return null;
        }
        if (p.iri) {
            return model.createResource(text);
        }
        if (p.datatype != null) {
            return model.createTypedLiteral(text, TypeMapper.getInstance().getSafeTypeByName(p.datatype));
        }
        return model.createLiteral(text);
    }

    /**
     * Substitute {placeholders} (dotted paths allowed) with row values. Returns
     * null if any placeholder value is missing (caller skips the pattern/row).
     * Throws if a placeholder resolves to a list — lists may only fan out in the
     * object position, from a top-level row key.
     */
    private static String substitute(String template, Map<String, Object> row,
                                     String pinnedName, Object pinnedValue,
                                     boolean encode, String where) {
        Matcher m = PLACEHOLDER.matcher(template);
        StringBuilder sb = new StringBuilder();
        int last = 0;
        while (m.find()) {
            String path = m.group(1);
            Object v = resolve(path, row, pinnedName, pinnedValue, where);
            if (v == null) {
                return null;
            }
            if (asList(v) != null) {
                throw new RuntimeException("Placeholder {" + path + "} in " + where
                        + " is list-valued — lists may only fan out in the object position,"
                        + " from a top-level row key");
            }
            sb.append(template, last, m.start());
            String s = String.valueOf(v);
            sb.append(encode ? iriEncode(s) : s);
            last = m.end();
        }
        sb.append(template, last, template.length());
        return sb.toString();
    }

    /**
     * Resolve a dotted placeholder path against a row (with the fan-out element
     * optionally pinned to the list's key). Returns null when a segment is
     * missing. Throws when the path traverses a non-map, or terminates on a map.
     */
    private static Object resolve(String path, Map<String, Object> row,
                                  String pinnedName, Object pinnedValue, String where) {
        String[] segs = path.split("\\.");
        Object cur = segs[0].equals(pinnedName) ? pinnedValue : row.get(segs[0]);
        for (int i = 1; i < segs.length; i++) {
            if (cur == null) {
                return null;
            }
            if (!(cur instanceof Map<?, ?> m)) {
                throw new RuntimeException("Placeholder {" + path + "} in " + where + ": '"
                        + segs[i - 1] + "' is not a map — cannot access '" + segs[i] + "'");
            }
            cur = m.get(segs[i]);
        }
        if (cur instanceof Map) {
            throw new RuntimeException("Placeholder {" + path + "} in " + where
                    + " resolves to a map — add a key: {" + path + ".<key>}");
        }
        return cur;
    }

    private static boolean passesFilters(TriplePattern p, String element) {
        if (p.include != null && !p.include.contains(element)) {
            return false;
        }
        if (p.exclude != null && p.exclude.contains(element)) {
            return false;
        }
        return true;
    }

    // ── Helpers ─────────────────────────────────────────────────

    /** Normalize a value to a List if it is list-like (List or array), else null. */
    static List<?> asList(Object v) {
        if (v instanceof List<?> l) {
            return l;
        }
        if (v != null && v.getClass().isArray()) {
            int n = java.lang.reflect.Array.getLength(v);
            List<Object> out = new ArrayList<>(n);
            for (int i = 0; i < n; i++) {
                out.add(java.lang.reflect.Array.get(v, i));
            }
            return out;
        }
        return null;
    }

    /**
     * Percent-encode a placeholder value for use inside an IRI template (R2RML
     * "IRI-safe"): RFC 3986 unreserved characters and non-ASCII pass through.
     */
    static String iriEncode(String s) {
        StringBuilder sb = new StringBuilder(s.length());
        int i = 0;
        while (i < s.length()) {
            int cp = s.codePointAt(i);
            if (cp > 127 || isUnreserved(cp)) {
                sb.appendCodePoint(cp);
            } else {
                for (byte b : new String(Character.toChars(cp)).getBytes(StandardCharsets.UTF_8)) {
                    sb.append('%').append(String.format("%02X", b));
                }
            }
            i += Character.charCount(cp);
        }
        return sb.toString();
    }

    private static boolean isUnreserved(int cp) {
        return (cp >= 'A' && cp <= 'Z') || (cp >= 'a' && cp <= 'z')
                || (cp >= '0' && cp <= '9') || cp == '-' || cp == '.' || cp == '_' || cp == '~';
    }

    private static String requireString(JsonObject o, String key) {
        JsonValue v = o.get(key);
        if (v == null || !v.isString()) {
            throw new RuntimeException("Template must have a string '" + key + "'");
        }
        return v.getAsString().value();
    }

    private static String optionalString(JsonObject o, String key, String def) {
        JsonValue v = o.get(key);
        if (v == null) {
            return def;
        }
        if (!v.isString()) {
            throw new RuntimeException("'" + key + "' must be a string");
        }
        return v.getAsString().value();
    }

    private static Set<String> optionalStringSet(JsonObject o, String key) {
        JsonValue v = o.get(key);
        if (v == null) {
            return null;
        }
        if (!v.isArray()) {
            throw new RuntimeException("'" + key + "' must be an array of strings");
        }
        Set<String> out = new LinkedHashSet<>();
        for (JsonValue e : v.getAsArray()) {
            if (!e.isString()) {
                throw new RuntimeException("'" + key + "' must contain only strings");
            }
            out.add(e.getAsString().value());
        }
        return out;
    }
}
