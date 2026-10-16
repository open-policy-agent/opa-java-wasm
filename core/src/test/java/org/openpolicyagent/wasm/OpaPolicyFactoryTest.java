package org.openpolicyagent.wasm;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.node.TextNode;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.BrokenBarrierException;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import run.endive.compiler.MachineFactoryCompiler;

public class OpaPolicyFactoryTest {

    static Path wasmFile;
    static Path customBuiltinsWasmFile;

    @BeforeAll
    public static void beforeAll() throws Exception {
        wasmFile = OpaCli.compile("base", "opa/wasm/test/allowed").resolve("policy.wasm");
        customBuiltinsWasmFile =
                OpaCli.compile("custom-builtins", true, "custom_builtins/zero_arg")
                        .resolve("policy.wasm");
    }

    @Test
    public void compilesOnceForMultiplePolicies() throws Exception {
        var compilations = new AtomicInteger();
        var factory =
                OpaPolicyFactory.builder()
                        .withPolicy(Files.readAllBytes(wasmFile))
                        .withCompiler(
                                module -> {
                                    compilations.incrementAndGet();
                                    return MachineFactoryCompiler.compile(module);
                                })
                        .build();

        var first = factory.newPolicy();
        var second = factory.newPolicy();

        assertEquals(1, compilations.get());
        assertTrue(allows(first, "alice", "{ \"role\" : { \"alice\" : \"admin\" } }"));
        assertFalse(allows(second, "bob", "{ \"role\" : { \"alice\" : \"admin\" } }"));
    }

    @Test
    public void instancesHaveIndependentMutableState() throws Exception {
        var factory = OpaPolicyFactory.builder().withPolicy(Files.readAllBytes(wasmFile)).build();
        var first = factory.newPolicy();
        var second = factory.newPolicy();

        first.data("{ \"role\" : { \"alice\" : \"admin\" } }");
        second.data("{ \"role\" : { \"bob\" : \"admin\" } }");

        assertTrue(Utils.getResult(first.evaluate("{\"user\": \"alice\"}")).asBoolean());
        assertFalse(Utils.getResult(second.evaluate("{\"user\": \"alice\"}")).asBoolean());
        assertFalse(Utils.getResult(first.evaluate("{\"user\": \"bob\"}")).asBoolean());
        assertTrue(Utils.getResult(second.evaluate("{\"user\": \"bob\"}")).asBoolean());
    }

    @Test
    public void concurrentNewPolicyAndEvaluationAreIsolated() throws Exception {
        var compilations = new AtomicInteger();
        var factory =
                OpaPolicyFactory.builder()
                        .withPolicy(Files.readAllBytes(wasmFile))
                        .withCompiler(
                                module -> {
                                    compilations.incrementAndGet();
                                    return MachineFactoryCompiler.compile(module);
                                })
                        .build();

        int tasks = 16;
        var barrier = new CyclicBarrier(tasks);
        List<String> errors = Collections.synchronizedList(new ArrayList<>());
        ExecutorService executor = Executors.newFixedThreadPool(tasks);
        List<Future<?>> futures = new ArrayList<>();

        for (int i = 0; i < tasks; i++) {
            final int idx = i;
            futures.add(
                    executor.submit(
                            () -> {
                                try {
                                    barrier.await(5, TimeUnit.SECONDS);
                                    var policy = factory.newPolicy();
                                    if (!allows(
                                            policy,
                                            "user" + idx,
                                            "{ \"role\" : { \"user" + idx + "\" : \"admin\" } }")) {
                                        errors.add("task " + idx + ": expected true");
                                    }
                                    if (allows(
                                            policy,
                                            "user" + (idx + 1),
                                            "{ \"role\" : { \"user" + idx + "\" : \"admin\" } }")) {
                                        errors.add("task " + idx + ": expected false");
                                    }
                                } catch (InterruptedException e) {
                                    Thread.currentThread().interrupt();
                                    errors.add("task " + idx + ": " + e.getMessage());
                                } catch (BrokenBarrierException
                                        | TimeoutException
                                        | RuntimeException e) {
                                    errors.add("task " + idx + ": " + e.getMessage());
                                }
                            }));
        }

        for (Future<?> f : futures) {
            f.get(30, TimeUnit.SECONDS);
        }
        executor.shutdown();

        assertEquals(List.of(), errors);
        assertEquals(1, compilations.get());
    }

    @Test
    public void invalidWasmFailsAtBuild() {
        var builder = OpaPolicyFactory.builder().withPolicy(new byte[] {1, 2, 3, 4});
        assertThrows(RuntimeException.class, builder::build);
    }

    @Test
    public void disableCompilerSkipsCompilation() throws Exception {
        var compilations = new AtomicInteger();
        var factory =
                OpaPolicyFactory.builder()
                        .withPolicy(Files.readAllBytes(wasmFile))
                        .disableCompiler()
                        .withCompiler(
                                module -> {
                                    compilations.incrementAndGet();
                                    return MachineFactoryCompiler.compile(module);
                                })
                        .build();

        var policy = factory.newPolicy();

        assertEquals(0, compilations.get());
        assertTrue(allows(policy, "alice", "{ \"role\" : { \"alice\" : \"admin\" } }"));
    }

    @Test
    public void customBuiltinsArePreserved() throws Exception {
        var factory =
                OpaPolicyFactory.builder()
                        .withPolicy(Files.readAllBytes(customBuiltinsWasmFile))
                        .addBuiltins(
                                OpaBuiltin.from(
                                        "custom.zeroArgBuiltin",
                                        instance -> TextNode.valueOf("hello")))
                        .build();

        var first = factory.newPolicy();
        var second = factory.newPolicy();

        assertEquals("hello", Utils.getResult(first.evaluate()).asText());
        assertEquals("hello", Utils.getResult(second.evaluate()).asText());
    }

    @Test
    public void memoryLimitsArePreserved() throws Exception {
        var factory =
                OpaPolicyFactory.builder()
                        .withPolicy(Files.readAllBytes(wasmFile))
                        .withInitialMemory(2)
                        .withMaxMemory(50)
                        .build();

        var policy = factory.newPolicy();

        assertTrue(allows(policy, "alice", "{ \"role\" : { \"alice\" : \"admin\" } }"));
    }

    private static boolean allows(OpaPolicy policy, String user, String data) {
        policy.data(data);
        return Utils.getResult(policy.evaluate("{\"user\": \"" + user + "\"}")).asBoolean();
    }
}
