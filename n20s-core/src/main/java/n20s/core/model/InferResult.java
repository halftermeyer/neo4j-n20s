package n20s.core.model;

public class InferResult {
    public String graphName;
    public long triplesBefore;
    public long triplesAfter;
    public long newTriples;
    public String profile;

    public InferResult(String name, long before, long after, String profile) {
        this.graphName = name;
        this.triplesBefore = before;
        this.triplesAfter = after;
        this.newTriples = after - before;
        this.profile = profile;
    }
}
