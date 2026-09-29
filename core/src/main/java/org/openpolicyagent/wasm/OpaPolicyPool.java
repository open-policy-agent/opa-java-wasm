package org.openpolicyagent.wasm;

import java.util.concurrent.ConcurrentLinkedDeque;
import java.util.concurrent.Semaphore;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Supplier;

/**
 * Thread-safe pool of {@link OpaPolicy} instances.
 *
 * <p>Each {@link #borrow()} returns a {@link Loan} that must be {@linkplain Loan#close() closed}
 * (ideally via try-with-resources) to return the policy to the pool. The pool caps the number of
 * live instances and blocks callers when the limit is reached.
 *
 * <p>Uses only lock-free data structures and {@link Semaphore} internally — no {@code synchronized}
 * blocks — so it is safe to use with virtual threads (no carrier-thread pinning).
 *
 * <pre>{@code
 * var pool = OpaPolicyPool.create(
 *         () -> OpaPolicy.builder().withPolicy(policyBytes).build(),
 *         4);
 *
 * try (var loan = pool.borrow()) {
 *     loan.policy()
 *         .data("{\"role\":{\"alice\":\"admin\"}}")
 *         .input("{\"user\":\"alice\"}");
 *     String result = loan.policy().evaluate();
 * }
 *
 * pool.close();
 * }</pre>
 */
public final class OpaPolicyPool implements AutoCloseable {

    private final ConcurrentLinkedDeque<OpaPolicy> idle;
    private final Semaphore permits;
    private final Supplier<OpaPolicy> factory;
    private final AtomicBoolean closed = new AtomicBoolean();

    private OpaPolicyPool(Supplier<OpaPolicy> factory, int maxSize) {
        this.factory = factory;
        this.idle = new ConcurrentLinkedDeque<>();
        this.permits = new Semaphore(maxSize);
    }

    /**
     * Creates a pool that allows at most {@code maxSize} concurrent policy instances.
     *
     * @param factory supplies new {@link OpaPolicy} instances when the pool is empty; called
     *     outside any lock so it may safely do expensive work (WASM parsing, compilation)
     * @param maxSize maximum number of concurrent instances
     */
    public static OpaPolicyPool create(Supplier<OpaPolicy> factory, int maxSize) {
        if (maxSize <= 0) {
            throw new IllegalArgumentException("maxSize must be positive, got: " + maxSize);
        }
        return new OpaPolicyPool(factory, maxSize);
    }

    /**
     * Borrows a policy from the pool, blocking if the pool is at capacity.
     *
     * <p>The returned {@link Loan} <b>must</b> be closed when done (preferably via
     * try-with-resources) to return the policy to the pool.
     *
     * @throws InterruptedException if the calling thread is interrupted while waiting for an
     *     available permit
     * @throws IllegalStateException if the pool has been closed
     */
    public Loan borrow() throws InterruptedException {
        if (closed.get()) {
            throw new IllegalStateException("Pool is closed");
        }
        permits.acquire();
        try {
            OpaPolicy policy = idle.pollFirst();
            if (policy == null) {
                policy = factory.get();
            }
            return new Loan(this, policy);
        } catch (RuntimeException t) {
            permits.release();
            throw t;
        }
    }

    private void release(OpaPolicy policy) {
        try {
            policy.reset();
            if (!closed.get()) {
                idle.offerFirst(policy);
            }
        } finally {
            permits.release();
        }
    }

    private void discard() {
        permits.release();
    }

    /**
     * Closes the pool. Outstanding {@link Loan}s are not forcibly closed — they will be cleaned up
     * as they are returned.
     */
    @Override
    public void close() {
        if (closed.compareAndSet(false, true)) {
            idle.clear();
        }
    }

    /**
     * A loan of an {@link OpaPolicy} from the pool.
     *
     * <p>Must be closed to return the policy to the pool. If a processing error leaves the policy
     * in a bad state, call {@link #discard()} instead of {@link #close()} to destroy the instance
     * rather than returning it.
     */
    public static final class Loan implements AutoCloseable {
        private final OpaPolicyPool pool;
        private OpaPolicy policy;

        Loan(OpaPolicyPool pool, OpaPolicy policy) {
            this.pool = pool;
            this.policy = policy;
        }

        /**
         * Returns the borrowed policy for configuration and evaluation.
         *
         * @throws IllegalStateException if the loan has already been closed or discarded
         */
        public OpaPolicy policy() {
            if (policy == null) {
                throw new IllegalStateException("Loan already returned");
            }
            return policy;
        }

        /** Returns the policy to the pool for reuse. */
        @Override
        public void close() {
            if (policy != null) {
                pool.release(policy);
                policy = null;
            }
        }

        /**
         * Destroys the policy instead of returning it to the pool. Use this when a processing error
         * may have left the policy in a corrupt state.
         */
        public void discard() {
            if (policy != null) {
                pool.discard();
                policy = null;
            }
        }
    }
}
