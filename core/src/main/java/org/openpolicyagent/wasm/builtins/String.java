package org.openpolicyagent.wasm.builtins;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.TextNode;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.List;
import org.openpolicyagent.wasm.OpaBuiltin;
import org.openpolicyagent.wasm.OpaWasm;

public class String {

    private static JsonNode sprintfImpl(OpaWasm instance, JsonNode operand0, JsonNode operand1) {
        // Implementation from:
        // https://github.com/open-policy-agent/opa/blob/269a118ad9f1b5673c68e97803d722fd4e28c0a1/v1/topdown/strings.go#L525
        // Validate the first operand as a string
        if (!operand0.isTextual()) {
            throw new IllegalArgumentException("Operand 1 must be a string.");
        }
        var format = operand0.asText();

        // Validate the second operand as an array
        if (!operand1.isArray()) {
            throw new IllegalArgumentException("Operand 2 must be an array.");
        }
        var arr = new ArrayList<JsonNode>();
        var iter = operand1.elements();
        while (iter.hasNext()) {
            arr.add(iter.next());
        }

        // Optimized path for "to_string" for a single integer, e.g., sprintf("%d", [x])
        if ("%d".equals(format) && arr.size() == 1) {
            JsonNode firstElement = arr.get(0);
            if (firstElement.isNumber()) {
                try {
                    return instance.jsonMapper().readTree(firstElement.numberValue().toString());
                } catch (JsonProcessingException e) {
                    throw new RuntimeException(e);
                }
            }
        }

        // Convert array elements into arguments
        List<Object> args = new ArrayList<>();
        for (JsonNode element : arr) {
            if (element.isInt()) {
                args.add(element.asInt());
            } else if (element.isBigInteger()) {
                args.add(new BigInteger(element.asText()));
            } else if (element.isDouble()) {
                args.add(element.asDouble());
            } else if (element.isTextual()) {
                args.add(element.asText());
            } else {
                args.add(element.toString());
            }
        }

        // Apply formatting using String.format
        try {
            // %v partial support
            if (format.contains("%v")) {
                format = format.replaceAll("%v", "%s");
            }
            // %q wraps the string in double quotes (Go/Rego semantics)
            if (format.contains("%q")) {
                format = format.replace("%q", "\"%s\"");
            }

            var result = java.lang.String.format(format, args.toArray());
            return TextNode.valueOf(result);
        } catch (IllegalArgumentException e) {
            throw new RuntimeException("Formatting error: " + e.getMessage());
        }
    }

    public static final OpaBuiltin.Builtin sprintf =
            OpaBuiltin.from("sprintf", String::sprintfImpl);

    public static OpaBuiltin.Builtin[] all() {
        return new OpaBuiltin.Builtin[] {sprintf};
    }
}
