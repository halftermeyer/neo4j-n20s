package n20s.core.model;

public class TurtleExportResult {
    public String graphName;
    public long tripleCount;
    public String turtle;

    public TurtleExportResult(String name, long count, String turtle) {
        this.graphName = name;
        this.tripleCount = count;
        this.turtle = turtle;
    }
}
