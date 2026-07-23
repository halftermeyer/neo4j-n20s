package n20s.core.model;

public class TemplateResult {
    public String graphName;
    public long rows;
    /** @deprecated legacy alias for {@link #emitted} — prefer emitted/added/triplesAfter */
    @Deprecated
    public long tripleCount;
    /** Triples emitted by the template (may exceed {@link #added}: RDF set semantics dedupe) */
    public long emitted;
    public long triplesBefore;
    public long triplesAfter;
    public long added;
    public String status;

    public TemplateResult(String graphName, long rows, long emitted,
                          long triplesBefore, long triplesAfter, String status) {
        this.graphName = graphName;
        this.rows = rows;
        this.emitted = emitted;
        this.tripleCount = emitted;
        this.triplesBefore = triplesBefore;
        this.triplesAfter = triplesAfter;
        this.added = triplesAfter - triplesBefore;
        this.status = status;
    }
}
