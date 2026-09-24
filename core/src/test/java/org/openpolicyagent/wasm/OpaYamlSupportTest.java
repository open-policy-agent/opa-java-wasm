package org.openpolicyagent.wasm;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

public class OpaYamlSupportTest {
    static OpaPolicy policy;

    @BeforeAll
    public static void beforeAll() throws Exception {
        policy =
                OpaPolicy.builder()
                        .withPolicy(
                                OpaCli.compile(
                                                "yaml-support",
                                                "yaml/support/canParseYAML",
                                                "yaml/support/hasSyntaxError",
                                                "yaml/support/hasSemanticError",
                                                "yaml/support/hasReferenceError",
                                                "yaml/support/hasYAMLWarning",
                                                "yaml/support/canMarshalYAML",
                                                "yaml/support/isValidYAML")
                                        .resolve("policy.wasm"))
                        .build();
    }

    @Test
    public void shouldUnmarshallYamlStrings() {
        var result = Utils.getResult(policy.entrypoint("yaml/support/canParseYAML").evaluate("{}"));
        assertTrue(result.asBoolean());
    }

    @Test
    public void shouldIgnoreYamlSyntaxErrors() throws Exception {
        var result =
                Utils.objectMapper.readTree(
                        policy.entrypoint("yaml/support/hasSyntaxError").evaluate());
        assertTrue(result.isArray());
        assertFalse(result.elements().hasNext());
    }

    @Test
    public void shouldIgnoreYamlSemanticErrors() throws Exception {
        var result =
                Utils.objectMapper.readTree(
                        policy.entrypoint("yaml/support/hasSemanticError").evaluate());
        assertTrue(result.isArray());
        assertFalse(result.elements().hasNext());
    }

    @Test
    public void shouldIgnoreYamlReferenceErrors() throws Exception {
        var result =
                Utils.objectMapper.readTree(
                        policy.entrypoint("yaml/support/hasReferenceError").evaluate());
        assertTrue(result.isArray());
        assertFalse(result.elements().hasNext());
    }

    @Test
    public void shouldIgnoreYamlWarnings() throws Exception {
        var result =
                Utils.objectMapper.readTree(
                        policy.entrypoint("yaml/support/hasYAMLWarning").evaluate());
        assertTrue(result.isArray());
        assertFalse(result.elements().hasNext());
    }

    @Test
    public void shouldMarshalYaml() {
        var result =
                Utils.getResult(
                        policy.entrypoint("yaml/support/canMarshalYAML")
                                .evaluate("[{ \"foo\": [1, 2, 3] }]"));
        var array = result.elements().next();
        var foo = array.findValue("foo").elements();
        assertEquals(1, foo.next().asInt());
        assertEquals(2, foo.next().asInt());
        assertEquals(3, foo.next().asInt());
    }

    @Test
    public void shouldValidateYaml() {
        var result = Utils.getResult(policy.entrypoint("yaml/support/isValidYAML").evaluate("{}"));
        assertTrue(result.asBoolean());
    }
}
