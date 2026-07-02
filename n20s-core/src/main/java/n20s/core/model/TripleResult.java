package n20s.core.model;

public class TripleResult {
    public String subject;
    public String predicate;
    public String object;

    public TripleResult(String s, String p, String o) {
        this.subject = s;
        this.predicate = p;
        this.object = o;
    }
}
