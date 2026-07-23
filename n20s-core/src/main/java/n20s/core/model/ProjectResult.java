package n20s.core.model;

public class ProjectResult {
    public String graphName;
    /** @deprecated legacy alias for {@link #emitted} — prefer emitted/added/triplesAfter */
    @Deprecated
    public long tripleCount;
    /** Triples emitted by this call (may exceed {@link #added}: RDF set semantics dedupe) */
    public long emitted;
    public long triplesBefore;
    public long triplesAfter;
    public long added;
    public String status;

    public ProjectResult(String graphName, long emitted,
                         long triplesBefore, long triplesAfter, String status) {
        this.graphName = graphName;
        this.emitted = emitted;
        this.tripleCount = emitted;
        this.triplesBefore = triplesBefore;
        this.triplesAfter = triplesAfter;
        this.added = triplesAfter - triplesBefore;
        this.status = status;
    }
}
