package org.openpolicyagent.wasm.testcases;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.JsonNode;
import java.util.Arrays;

public class Case {
    @JsonProperty("note")
    private String note;

    @JsonProperty("query")
    private String query;

    @JsonProperty("input")
    private JsonNode input;

    @JsonProperty("modules")
    private JsonNode modules;

    @JsonProperty("want_defined")
    private boolean wantDefined;

    @JsonProperty("want_error")
    private String wantError;

    @JsonProperty("data")
    private JsonNode data;

    @JsonProperty("want_result")
    private JsonNode[] wantResult;

    @JsonProperty("wasm")
    private String wasm;

    @JsonProperty("ignore_generated_vars")
    private boolean ignoreGeneratedVars;

    Case() {}

    public String note() {
        return note;
    }

    public String query() {
        return query;
    }

    public JsonNode input() {
        return input;
    }

    public JsonNode modules() {
        return modules;
    }

    public boolean wantDefined() {
        return wantDefined;
    }

    public String wantError() {
        return wantError;
    }

    public JsonNode data() {
        return data;
    }

    public JsonNode[] wantResult() {
        return wantResult;
    }

    public String wasm() {
        return wasm;
    }

    public boolean ignoreGeneratedVars() {
        return ignoreGeneratedVars;
    }

    @Override
    public String toString() {
        return "Case{"
                + "note='"
                + note
                + '\''
                + ", query='"
                + query
                + '\''
                + ", input="
                + input
                + ", modules="
                + modules
                + ", wantDefined="
                + wantDefined
                + ", wantError='"
                + wantError
                + '\''
                + ", data="
                + data
                + ", wantResult="
                + Arrays.toString(wantResult)
                + '}';
    }
}
