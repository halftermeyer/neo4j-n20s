package n20s.core.model;

public class AddTurtleResult {
    public String graphName;
    public long triplesBefore;
    public long triplesAfter;
    public long added;

    public AddTurtleResult(String name, long before, long after) {
        this.graphName = name;
        this.triplesBefore = before;
        this.triplesAfter = after;
        this.added = after - before;
    }
}
