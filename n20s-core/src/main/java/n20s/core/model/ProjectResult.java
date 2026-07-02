package n20s.core.model;

public class ProjectResult {
    public String graphName;
    public long tripleCount;
    public String status;

    public ProjectResult(String graphName, long tripleCount, String status) {
        this.graphName = graphName;
        this.tripleCount = tripleCount;
        this.status = status;
    }
}
