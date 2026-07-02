package n20s.core.model;

public class GraphInfo {
    public String graphName;
    public long tripleCount;

    public GraphInfo(String name, long count) {
        this.graphName = name;
        this.tripleCount = count;
    }
}
