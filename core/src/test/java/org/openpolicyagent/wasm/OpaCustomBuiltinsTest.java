package org.openpolicyagent.wasm;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.node.TextNode;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

public class OpaCustomBuiltinsTest {
    static OpaPolicy policy;

    static OpaBuiltin.Builtin[] customBuiltins =
            new OpaBuiltin.Builtin[] {
                OpaBuiltin.from("custom.zeroArgBuiltin", (instance) -> TextNode.valueOf("hello")),
                OpaBuiltin.from(
                        "custom.oneArgBuiltin",
                        (instance, arg0) -> TextNode.valueOf("hello " + arg0.asText())),
                OpaBuiltin.from(
                        "custom.twoArgBuiltin",
                        (instance, arg0, arg1) ->
                                TextNode.valueOf("hello " + arg0.asText() + ", " + arg1.asText())),
                OpaBuiltin.from(
                        "custom.threeArgBuiltin",
                        (instance, arg0, arg1, arg2) ->
                                TextNode.valueOf(
                                        "hello "
                                                + arg0.asText()
                                                + ", "
                                                + arg1.asText()
                                                + ", "
                                                + arg2.asText())),
                OpaBuiltin.from(
                        "custom.fourArgBuiltin",
                        (instance, arg0, arg1, arg2, arg3) ->
                                TextNode.valueOf(
                                        "hello "
                                                + arg0.asText()
                                                + ", "
                                                + arg1.asText()
                                                + ", "
                                                + arg2.asText()
                                                + ", "
                                                + arg3.asText())),
                OpaBuiltin.from(
                        "json.is_valid",
                        (instance, arg0) -> {
                            throw new RuntimeException("should never happen");
                        })
            };

    @BeforeAll
    public static void beforeAll() throws Exception {
        policy =
                OpaPolicy.builder()
                        .addBuiltins(customBuiltins)
                        .withPolicy(
                                OpaCli.compile(
                                                "custom-builtins",
                                                true,
                                                "custom_builtins/zero_arg",
                                                "custom_builtins/one_arg",
                                                "custom_builtins/two_arg",
                                                "custom_builtins/three_arg",
                                                "custom_builtins/four_arg",
                                                "custom_builtins/valid_json")
                                        .resolve("policy.wasm"))
                        .build();
    }

    @Test
    public void shouldCallZeroArgBuiltin() {
        var result = Utils.getResult(policy.entrypoint("custom_builtins/zero_arg").evaluate());
        assertEquals("hello", result.asText());
    }

    @Test
    public void shouldCallACustomOneArgBuiltin() {
        var result =
                Utils.getResult(
                        policy.entrypoint("custom_builtins/one_arg")
                                .evaluate("{ \"args\": [\"arg0\"] }"));
        assertEquals("hello arg0", result.asText());
    }

    @Test
    public void shouldCallACustomTwoArgBuiltin() {
        var result =
                Utils.getResult(
                        policy.entrypoint("custom_builtins/two_arg")
                                .evaluate("{ \"args\": [\"arg0\", \"arg1\"] }"));
        assertEquals("hello arg0, arg1", result.asText());
    }

    @Test
    public void shouldCallACustomThreeArgBuiltin() {
        var result =
                Utils.getResult(
                        policy.entrypoint("custom_builtins/three_arg")
                                .evaluate("{ \"args\": [\"arg0\", \"arg1\", \"arg2\"] }"));
        assertEquals("hello arg0, arg1, arg2", result.asText());
    }

    @Test
    public void shouldCallACustomFourArgBuiltin() {
        var result =
                Utils.getResult(
                        policy.entrypoint("custom_builtins/four_arg")
                                .evaluate(
                                        "{ \"args\": [\"arg0\", \"arg1\", \"arg2\", \"arg3\"] }"));
        assertEquals("hello arg0, arg1, arg2, arg3", result.asText());
    }

    @Test
    public void shouldCallAProvidedBuiltinOverACustomBuiltin() {
        var result =
                Utils.getResult(policy.entrypoint("custom_builtins/valid_json").evaluate("{}"));
        assertTrue(result.asBoolean());
    }
}
