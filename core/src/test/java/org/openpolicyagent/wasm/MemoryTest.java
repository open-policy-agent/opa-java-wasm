package org.openpolicyagent.wasm;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.nio.file.Path;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

public class MemoryTest {
    static Path wasmFile;

    @BeforeAll
    public static void beforeAll() throws Exception {
        wasmFile = OpaCli.compile("memory", "test/allow").resolve("policy.wasm");
    }

    @Test
    public void inputExceedsMemoryHostFailsToGrowIt() {
        var policy =
                OpaPolicy.builder()
                        .withInitialMemory(2)
                        .withMaxMemory(2)
                        .withPolicy(wasmFile)
                        .build();
        var input = new String(new char[2 * 65536]).replace("\0", "a");
        var exception = assertThrows(RuntimeException.class, () -> policy.evaluate(input));
        assertEquals("Maximum memory size exceeded", exception.getMessage());
    }

    @Test
    public void parsingInputExceedsMemory() {
        var policy =
                OpaPolicy.builder()
                        .withInitialMemory(3)
                        .withMaxMemory(4)
                        .withPolicy(wasmFile)
                        .build();
        var input = new String(new char[2 * 65536]).replace("\0", "a");
        var exception = assertThrows(OpaAbortException.class, () -> policy.input(input));
        Assertions.assertEquals("opa_abort - opa_malloc: failed", exception.getMessage());
    }

    @Test
    public void largeInputHostAndGuestGrowSuccessfully() {
        var policy =
                OpaPolicy.builder()
                        .withInitialMemory(2)
                        .withMaxMemory(8)
                        .withPolicy(wasmFile)
                        .build();
        var input = new String(new char[2 * 65536]).replace("\0", "a");
        assertDoesNotThrow(() -> policy.evaluate(input));
    }

    @Test
    public void doesNotLeakMemoryEvaluatingTheSamePolicyMultipleTimes() {
        var policy =
                OpaPolicy.builder()
                        .withInitialMemory(2)
                        .withMaxMemory(8)
                        .withPolicy(wasmFile)
                        .build();
        var input = new String(new char[2 * 65536]).replace("\0", "a");
        for (int i = 0; i < 100000; i++) {
            assertDoesNotThrow(() -> policy.evaluate(input));
        }
    }
}
