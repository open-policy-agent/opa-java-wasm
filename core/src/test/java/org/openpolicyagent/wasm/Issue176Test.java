package org.openpolicyagent.wasm;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.nio.file.Path;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

public class Issue176Test {
    static Path wasmFile;

    @BeforeAll
    public static void beforeAll() throws Exception {
        wasmFile =
                OpaCli.compile("issue176", "issue176/emdash_sprintf", "issue176/emdash_literal")
                        .resolve("policy.wasm");
    }

    @Test
    public void emdashSprintf() {
        var opa = OpaPolicy.builder().withPolicy(wasmFile).build();

        var result = Utils.getResult(opa.entrypoint("issue176/emdash_sprintf").evaluate());

        assertEquals(
                "requested String value is invalid — please use one of the allowed values",
                result.asText());
    }

    @Test
    public void emdashLiteral() {
        var opa = OpaPolicy.builder().withPolicy(wasmFile).build();

        var result = Utils.getResult(opa.entrypoint("issue176/emdash_literal").evaluate());

        assertEquals("This contains an em-dash — in a string literal", result.asText());
    }
}
