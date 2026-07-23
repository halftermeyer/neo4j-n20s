package n20s;

/**
 * Runtime coercion for data-carrying parameters declared as Object (ANY).
 *
 * Why not String: Neo4j 2026.05.0's semantic analysis mis-infers the element
 * type of {@code UNWIND collect(n.prop) + [m.prop] AS t} as Boolean and
 * rejects String-typed parameters at planning time ("Type mismatch: expected
 * String but was Boolean") — even though every value is a String. Declaring
 * ANY sidesteps the broken inference; actual type safety happens here, with
 * a clearer error than the planner's.
 */
final class Args {

    private Args() {}

    static String string(Object value, String param) {
        if (value == null) {
            return null;
        }
        if (value instanceof String s) {
            return s;
        }
        throw new IllegalArgumentException("Parameter '" + param + "' must be a String, got "
                + value.getClass().getSimpleName() + ": " + value);
    }
}
