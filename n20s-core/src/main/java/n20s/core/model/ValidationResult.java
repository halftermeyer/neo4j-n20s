package n20s.core.model;

public class ValidationResult {
    public String focusNode;
    public String path;
    public String severity;
    public String message;
    public String value;
    public String sourceShape;

    public ValidationResult(String focusNode, String path, String severity,
                            String message, String value, String sourceShape) {
        this.focusNode = focusNode;
        this.path = path;
        this.severity = severity;
        this.message = message;
        this.value = value;
        this.sourceShape = sourceShape;
    }
}
