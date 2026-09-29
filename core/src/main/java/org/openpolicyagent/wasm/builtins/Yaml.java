package org.openpolicyagent.wasm.builtins;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.BooleanNode;
import com.fasterxml.jackson.databind.node.TextNode;
import org.openpolicyagent.wasm.OpaBuiltin;
import org.openpolicyagent.wasm.OpaWasm;

public class Yaml {

    private static JsonNode isValidImpl(OpaWasm instance, JsonNode boxedYaml) {
        if (!boxedYaml.isTextual()) {
            return BooleanNode.getFalse();
        } else {
            try {
                instance.yamlMapper().readTree(boxedYaml.asText());
                return BooleanNode.getTrue();
            } catch (JsonProcessingException e) {
                return BooleanNode.getFalse();
            }
        }
    }

    public static final OpaBuiltin.Builtin isValid =
            OpaBuiltin.from("yaml.is_valid", Yaml::isValidImpl);

    private static JsonNode unmarshalImpl(OpaWasm instance, JsonNode boxedYaml) {
        if (!boxedYaml.isTextual()) {
            throw new RuntimeException("yaml is not correctly boxed in a Json string");
        } else {
            try {
                return instance.yamlMapper().readTree(boxedYaml.asText());
            } catch (JsonProcessingException e) {
                // should ignore errors here ...
                return BooleanNode.getFalse();
            }
        }
    }

    public static final OpaBuiltin.Builtin unmarshal =
            OpaBuiltin.from("yaml.unmarshal", Yaml::unmarshalImpl);

    public static JsonNode marshalImpl(OpaWasm instance, JsonNode json) {
        try {
            return TextNode.valueOf(instance.yamlMapper().writeValueAsString(json));
        } catch (JsonProcessingException e) {
            throw new RuntimeException(e);
        }
    }

    public static final OpaBuiltin.Builtin marshal =
            OpaBuiltin.from("yaml.marshal", Yaml::marshalImpl);

    public static OpaBuiltin.Builtin[] all() {
        return new OpaBuiltin.Builtin[] {isValid, marshal, unmarshal};
    }
}
