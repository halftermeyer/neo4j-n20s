package n20s.core;

import org.apache.jena.datatypes.TypeMapper;
import org.apache.jena.rdf.model.*;

/**
 * Utility methods for parsing RDF nodes from string conventions
 * and converting Jena literals to Java types.
 */
public final class TripleParser {

    private TripleParser() {}

    /**
     * Parse a subject string into a Jena Resource.
     * Handles blank nodes (prefix "_:") and URI resources.
     */
    public static Resource parseSubject(Model model, String s) {
        if (s.startsWith("_:")) {
            return model.createResource(new AnonId(s.substring(2)));
        }
        return model.createResource(s);
    }

    /**
     * Parse an object string into a Jena RDFNode.
     * Handles:
     *   "value"              — plain literal
     *   "value"@en           — language-tagged
     *   "value"^^&lt;datatype&gt;  — typed literal
     *   _:id                 — blank node
     *   URI                  — resource
     */
    public static RDFNode parseObject(Model model, String o) {
        if (o.startsWith("\"")) {
            return parseLiteral(model, o);
        } else if (o.startsWith("_:")) {
            return model.createResource(new AnonId(o.substring(2)));
        }
        return model.createResource(o);
    }

    /**
     * Parse a literal from the (s,p,o) string convention.
     * Input formats:
     *   "value"              — plain literal
     *   "value"@en           — language-tagged
     *   "value"^^&lt;datatype&gt;  — typed literal
     */
    public static RDFNode parseLiteral(Model model, String o) {
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

        // Fallback
        return model.createLiteral(rest);
    }

    /**
     * Convert a Jena Literal to a Java type suitable for JSON/Neo4j.
     * Returns: Long, Double, Boolean, or String.
     */
    public static Object toLiteralValue(Literal lit) {
        Object val = lit.getValue();
        if (val instanceof Integer || val instanceof Long) {
            return ((Number) val).longValue();
        } else if (val instanceof Float || val instanceof Double) {
            return ((Number) val).doubleValue();
        } else if (val instanceof java.math.BigDecimal) {
            return ((java.math.BigDecimal) val).doubleValue();
        } else if (val instanceof java.math.BigInteger) {
            return ((java.math.BigInteger) val).longValue();
        } else if (val instanceof Boolean) {
            return val;
        } else if (val instanceof Number) {
            return ((Number) val).doubleValue();
        }
        return val.toString();
    }

    /**
     * Format a Jena Statement's object as a string following the (s,p,o) convention.
     */
    public static String formatObject(Statement stmt) {
        if (stmt.getObject().isLiteral()) {
            Literal lit = stmt.getObject().asLiteral();
            if (lit.getLanguage() != null && !lit.getLanguage().isEmpty()) {
                return "\"" + lit.getString() + "\"@" + lit.getLanguage();
            } else if (lit.getDatatypeURI() != null) {
                return "\"" + lit.getString() + "\"^^<" + lit.getDatatypeURI() + ">";
            } else {
                return "\"" + lit.getString() + "\"";
            }
        }
        return stmt.getObject().toString();
    }
}
