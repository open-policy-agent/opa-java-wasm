package org.openpolicyagent.wasm;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Path;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

public class MultipleEntrypointsTest {
    static Path wasmFile;

    @BeforeAll
    public static void beforeAll() throws Exception {
        wasmFile =
                OpaCli.compile("multiple-entrypoints", "example", "example/one", "example/two")
                        .resolve("policy.wasm");
    }

    @Test
    public void shouldRunWithDefaultEntrypoint() throws Exception {
        var policy = OpaPolicy.builder().withPolicy(wasmFile).build();
        var result = Utils.getResult(policy.evaluate());

        assertTrue(result.size() > 0);
        assertTrue(result.has("one"));
        assertTrue(result.has("two"));
    }

    @Test
    public void shouldRunWithNumberedEntrypointSpecified() throws Exception {
        var policy = OpaPolicy.builder().withPolicy(wasmFile).build();
        policy.input("{}").entrypoint("example/one");

        var result = Utils.getResult(policy.evaluate());

        assertTrue(result.size() > 0);
        assertFalse(result.findValue("myRule").asBoolean());
        assertFalse(result.findValue("myOtherRule").asBoolean());
    }

    @Test
    public void shouldRunWithSecondEntrypointSpecified() throws Exception {
        var policy = OpaPolicy.builder().withPolicy(wasmFile).build();
        policy.input("{}").entrypoint("example/two");

        var result = Utils.getResult(policy.evaluate());

        assertTrue(result.size() > 0);
        assertFalse(result.findValue("ourRule").asBoolean());
        assertFalse(result.findValue("theirRule").asBoolean());
    }

    @Test
    public void shouldNotRunIfEntrypointStringDoesNotExist() throws Exception {
        var policy = OpaPolicy.builder().withPolicy(wasmFile).build();
        var exception =
                assertThrows(
                        IllegalArgumentException.class,
                        () -> policy.entrypoint("not/a/real/entrypoint"));
        assertTrue(exception.getMessage().contains("not/a/real/entrypoint"));
        assertTrue(exception.getMessage().contains("is not defined"));
    }
}
