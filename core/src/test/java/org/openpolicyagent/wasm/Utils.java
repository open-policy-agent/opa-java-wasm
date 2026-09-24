package org.openpolicyagent.wasm;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

public class Utils {
    static ObjectMapper objectMapper = new ObjectMapper();

    public static JsonNode getResult(String jsonStr) {
        JsonNode json;
        try {
            json = objectMapper.readTree(jsonStr);
        } catch (JsonProcessingException e) {
            throw new RuntimeException(e);
        }
        try {
            return json.elements().next().findValue("result");
        } catch (Exception e) {
            throw new RuntimeException("Unexpected result: " + jsonStr);
        }
    }

    public static String jsonPrettyPrint(String input) {
        try {
            return objectMapper
                    .writerWithDefaultPrettyPrinter()
                    .writeValueAsString(objectMapper.readTree(input));
        } catch (JsonProcessingException e) {
            throw new RuntimeException("Failed to read Json", e);
        }
    }
}
