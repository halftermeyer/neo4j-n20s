package n20s.core.model;

public class ExplainResult {
    public long step;
    public long depth;
    public String subject;
    public String predicate;
    public String object;
    public String kind;   // "derived" | "asserted" | "axiom"
    public String rule;   // rule name for "derived", else null

    public ExplainResult(long step, long depth, String subject, String predicate,
                         String object, String kind, String rule) {
        this.step = step;
        this.depth = depth;
        this.subject = subject;
        this.predicate = predicate;
        this.object = object;
        this.kind = kind;
        this.rule = rule;
    }
}
