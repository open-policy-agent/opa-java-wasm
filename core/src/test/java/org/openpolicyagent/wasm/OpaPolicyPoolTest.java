package org.openpolicyagent.wasm;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

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
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

public class OpaPolicyPoolTest {
    static Path wasmFile;

    @BeforeAll
    public static void beforeAll() throws Exception {
        wasmFile = OpaCli.compile("base", "opa/wasm/test/allowed").resolve("policy.wasm");
    }

    @Test
    public void basicBorrowAndReturn() throws InterruptedException {
        var pool = OpaPolicyPool.create(() -> OpaPolicy.builder().withPolicy(wasmFile).build(), 2);

        try (var loan = pool.borrow()) {
            loan.policy()
                    .data("{ \"role\" : { \"alice\" : \"admin\" } }")
                    .input("{\"user\": \"alice\"}");
            assertTrue(Utils.getResult(loan.policy().evaluate()).asBoolean());
        }

        pool.close();
    }

    @Test
    public void instanceIsReusedAcrossBorrows() throws InterruptedException {
        var pool = OpaPolicyPool.create(() -> OpaPolicy.builder().withPolicy(wasmFile).build(), 1);

        try (var loan = pool.borrow()) {
            loan.policy()
                    .data("{ \"role\" : { \"alice\" : \"admin\" } }")
                    .input("{\"user\": \"alice\"}");
            assertTrue(Utils.getResult(loan.policy().evaluate()).asBoolean());
        }

        // Second borrow should reuse the same instance; state was reset
        try (var loan = pool.borrow()) {
            loan.policy()
                    .data("{ \"role\" : { \"bob\" : \"admin\" } }")
                    .input("{\"user\": \"bob\"}");
            assertTrue(Utils.getResult(loan.policy().evaluate()).asBoolean());

            // Previous data should not be visible
            loan.policy().input("{\"user\": \"alice\"}");
            assertFalse(Utils.getResult(loan.policy().evaluate()).asBoolean());
        }

        pool.close();
    }

    @Test
    public void concurrentAccess() throws Exception {
        int poolSize = 4;
        int tasks = 16;
        var pool =
                OpaPolicyPool.create(
                        () -> OpaPolicy.builder().withPolicy(wasmFile).build(), poolSize);
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
                                    try (var loan = pool.borrow()) {
                                        loan.policy()
                                                .data(
                                                        "{ \"role\" : { \"user"
                                                                + idx
                                                                + "\" : \"admin\" } }")
                                                .input("{\"user\": \"user" + idx + "\"}");
                                        var result =
                                                Utils.getResult(loan.policy().evaluate())
                                                        .asBoolean();
                                        if (!result) {
                                            errors.add("task " + idx + ": expected true");
                                        }
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
        pool.close();

        assertEquals(List.of(), errors);
    }

    @Test
    public void discardAndRecreate() throws InterruptedException {
        var pool = OpaPolicyPool.create(() -> OpaPolicy.builder().withPolicy(wasmFile).build(), 1);

        try (var loan = pool.borrow()) {
            loan.policy()
                    .data("{ \"role\" : { \"alice\" : \"admin\" } }")
                    .input("{\"user\": \"alice\"}");
            assertTrue(Utils.getResult(loan.policy().evaluate()).asBoolean());
            loan.discard();
        }

        // After discard, a new policy is created for the next borrow
        try (var loan = pool.borrow()) {
            loan.policy()
                    .data("{ \"role\" : { \"bob\" : \"admin\" } }")
                    .input("{\"user\": \"bob\"}");
            assertTrue(Utils.getResult(loan.policy().evaluate()).asBoolean());
        }

        pool.close();
    }

    @Test
    public void borrowAfterCloseThrows() {
        var pool = OpaPolicyPool.create(() -> OpaPolicy.builder().withPolicy(wasmFile).build(), 2);
        pool.close();

        assertThrows(IllegalStateException.class, pool::borrow);
    }

    @Test
    public void invalidMaxSizeThrows() {
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        OpaPolicyPool.create(
                                () -> OpaPolicy.builder().withPolicy(wasmFile).build(), 0));
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        OpaPolicyPool.create(
                                () -> OpaPolicy.builder().withPolicy(wasmFile).build(), -1));
    }
}
