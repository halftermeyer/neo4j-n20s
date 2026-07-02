package n20s.core.model;

import java.util.Map;

public class QueryResult {
    public Map<String, Object> row;

    public QueryResult(Map<String, Object> row) {
        this.row = row;
    }
}
