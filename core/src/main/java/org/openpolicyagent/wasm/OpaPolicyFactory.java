package org.openpolicyagent.wasm;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.function.Function;
import run.endive.compiler.MachineFactoryCompiler;
import run.endive.runtime.ByteArrayMemory;
import run.endive.runtime.Instance;
import run.endive.runtime.Machine;
import run.endive.wasm.Parser;
import run.endive.wasm.WasmModule;
import run.endive.wasm.types.MemoryLimits;

/**
 * Compiles an OPA Wasm module once and creates multiple isolated {@link OpaPolicy} instances
 * from it.
 *
 * <p>Parsing and bytecode compilation happen a single time, in {@link Builder#build()}. Each
 * {@link #newPolicy()} call only performs runtime instantiation: a fresh linear memory and an
 * independent evaluation state (heap pointers, data, input, entrypoint). This removes the
 * per-instance parse and compilation cost paid when every policy is built with {@link
 * OpaPolicy#builder()}.
 *
 * <p>The factory is immutable and thread-safe; {@link #newPolicy()} may be called
 * concurrently. {@link OpaPolicy} instances remain mutable and are <b>not</b> safe for
 * concurrent evaluation.
 *
 * <pre>{@code
 * OpaPolicyFactory factory = OpaPolicyFactory.builder()
 *         .withPolicy(wasmBytes)
 *         .build(); // parse and compile once
 *
 * OpaPolicy policy = factory.newPolicy();
 * // compose with the pool: one compilation for the whole pool
 * OpaPolicyPool pool = OpaPolicyPool.create(factory::newPolicy, 4);
 * }</pre>
 */
public final class OpaPolicyFactory {

    private final WasmModule module;
    private final Function<Instance, Machine> machineFactory;
    private final ObjectMapper jsonMapper;
    private final ObjectMapper yamlMapper;
    private final int initialMemory;
    private final int maxMemory;
    private final boolean defaultBuiltins;
    private final OpaBuiltin.Builtin[] builtins;

    private OpaPolicyFactory(Builder b) {
        module = Parser.parse(b.policy);
        machineFactory = b.enableCompiler ? b.compiler.apply(module) : null;
        jsonMapper = b.jsonMapper != null ? b.jsonMapper : DefaultMappers.jsonMapper;
        yamlMapper = b.yamlMapper != null ? b.yamlMapper : DefaultMappers.yamlMapper;
        initialMemory = b.initialMemory;
        maxMemory = b.maxMemory;
        defaultBuiltins = b.defaultBuiltins;
        builtins = b.builtins.toArray(OpaBuiltin.Builtin[]::new);
    }

    /**
     * Creates a new {@link OpaPolicy} from the shared parsed module and compiled machine
     * factory, with fresh memory and evaluation state.
     */
    public OpaPolicy newPolicy() {
        var memory = new ByteArrayMemory(new MemoryLimits(initialMemory, maxMemory));
        var wasm =
                new OpaWasm(
                        module,
                        machineFactory,
                        jsonMapper,
                        yamlMapper,
                        memory,
                        defaultBuiltins,
                        builtins.clone());
        return new OpaPolicy(wasm);
    }

    public static Builder builder() {
        return new Builder();
    }

    public static final class Builder {

        private InputStream policy;
        private ObjectMapper jsonMapper;
        private ObjectMapper yamlMapper;
        private int initialMemory = 10;
        private int maxMemory = MemoryLimits.MAX_PAGES;
        private boolean defaultBuiltins = true;
        private boolean enableCompiler = true;
        private final List<OpaBuiltin.Builtin> builtins = new ArrayList<>();
        private Function<WasmModule, Function<Instance, Machine>> compiler =
                MachineFactoryCompiler::compile;

        private Builder() {}

        public Builder withPolicy(byte[] bytes) {
            policy = new ByteArrayInputStream(bytes.clone());
            return this;
        }

        public Builder withPolicy(InputStream input) {
            policy = input;
            return this;
        }

        public Builder withJsonMapper(ObjectMapper value) {
            jsonMapper = value;
            return this;
        }

        public Builder withYamlMapper(ObjectMapper value) {
            yamlMapper = value;
            return this;
        }

        public Builder withInitialMemory(int value) {
            initialMemory = value;
            return this;
        }

        public Builder withMaxMemory(int value) {
            maxMemory = value;
            return this;
        }

        public Builder withDefaultBuiltins(boolean value) {
            defaultBuiltins = value;
            return this;
        }

        public Builder addBuiltins(OpaBuiltin.Builtin... values) {
            builtins.addAll(List.of(values));
            return this;
        }

        public Builder disableCompiler() {
            enableCompiler = false;
            return this;
        }

        // Package-private test hook; not part of the public API.
        Builder withCompiler(Function<WasmModule, Function<Instance, Machine>> value) {
            compiler = Objects.requireNonNull(value);
            return this;
        }

        /** Parses and compiles the policy. Invalid modules fail here, before any evaluation. */
        public OpaPolicyFactory build() {
            Objects.requireNonNull(policy, "policy");
            return new OpaPolicyFactory(this);
        }
    }
}
