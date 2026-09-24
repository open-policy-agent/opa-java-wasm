package org.openpolicyagent.wasm.builtins;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.BooleanNode;
import org.openpolicyagent.wasm.OpaBuiltin;
import org.openpolicyagent.wasm.OpaWasm;

public class Json {

    private static JsonNode isValidImpl(OpaWasm instance, JsonNode boxedJson) {
        if (!boxedJson.isTextual()) {
            return BooleanNode.getFalse();
        } else {
            try {
                instance.jsonMapper().readTree(boxedJson.asText());
                return BooleanNode.getTrue();
            } catch (JsonProcessingException e) {
                return BooleanNode.getFalse();
            }
        }
    }

    public static final OpaBuiltin.Builtin isValid =
            OpaBuiltin.from("json.is_valid", Json::isValidImpl);

    public static OpaBuiltin.Builtin[] all() {
        return new OpaBuiltin.Builtin[] {isValid};
    }
}
