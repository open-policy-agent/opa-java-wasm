package org.openpolicyagent.wasm;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.nio.file.Path;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

public class OpaStringBuiltinsTest {
    static Path wasmFile;

    @BeforeAll
    public static void beforeAll() throws Exception {
        wasmFile =
                OpaCli.compile(
                                "string-builtins",
                                "string_builtins/invoke_sprintf",
                                "string_builtins/integer_fastpath",
                                "string_builtins/string_example",
                                "string_builtins/quoted")
                        .resolve("policy.wasm");
    }

    @Test
    public void sprintf() {
        var opa = OpaPolicy.builder().withPolicy(wasmFile).build();

        var result = Utils.getResult(opa.entrypoint("string_builtins/invoke_sprintf").evaluate());

        assertEquals("hello user your number is 321!", result.get("printed").asText());
    }

    @Test
    public void integerFastPath() {
        var opa = OpaPolicy.builder().withPolicy(wasmFile).build();

        var result = Utils.getResult(opa.entrypoint("string_builtins/integer_fastpath").evaluate());

        assertEquals(123, result.get("printed").asInt());
    }

    @Test
    public void stringExample() {
        var opa = OpaPolicy.builder().withPolicy(wasmFile).build();

        var result = Utils.getResult(opa.entrypoint("string_builtins/string_example").evaluate());

        assertEquals("my string", result.get("printed").asText());
    }

    @Test
    public void quoted() {
        var opa = OpaPolicy.builder().withPolicy(wasmFile).build();

        var result =
                Utils.getResult(
                        opa.entrypoint("string_builtins/quoted")
                                .evaluate("{\"value\": \"String\"}"));

        assertEquals("requested \"String\" is invalid", result.get("printed").asText());
    }
}
