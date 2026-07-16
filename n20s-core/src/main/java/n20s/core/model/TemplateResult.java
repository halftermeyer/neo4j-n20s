package n20s.core.model;

public class TemplateResult {
    public String graphName;
    public long rows;
    public long tripleCount;
    public String status;

    public TemplateResult(String graphName, long rows, long tripleCount, String status) {
        this.graphName = graphName;
        this.rows = rows;
        this.tripleCount = tripleCount;
        this.status = status;
    }
}
