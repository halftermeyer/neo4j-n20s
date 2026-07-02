package n20s.core;

import org.apache.jena.rdf.model.Model;
import org.apache.jena.rdf.model.ModelFactory;

import java.util.Collections;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Singleton catalog of named in-memory RDF graphs.
 * Same pattern as GDS GraphStore — heap-resident, named, droppable.
 */
public final class GraphCatalog {

    private static final Map<String, Model> GRAPHS = new ConcurrentHashMap<>();

    private GraphCatalog() {}

    public static Model getOrCreate(String name) {
        return GRAPHS.computeIfAbsent(name, k -> ModelFactory.createDefaultModel());
    }

    public static Model get(String name) {
        Model m = GRAPHS.get(name);
        if (m == null) {
            throw new IllegalArgumentException("Graph '" + name + "' not found. Use n20s.graph.project() or addTurtle() first.");
        }
        return m;
    }

    public static boolean exists(String name) {
        return GRAPHS.containsKey(name);
    }

    public static void drop(String name) {
        Model m = GRAPHS.remove(name);
        if (m != null) {
            m.close();
        }
    }

    public static Set<String> list() {
        return Collections.unmodifiableSet(GRAPHS.keySet());
    }

    public static long tripleCount(String name) {
        Model m = GRAPHS.get(name);
        return m != null ? m.size() : 0;
    }

    public static void put(String name, Model model) {
        Model old = GRAPHS.put(name, model);
        if (old != null && old != model) {
            old.close();
        }
    }
}
