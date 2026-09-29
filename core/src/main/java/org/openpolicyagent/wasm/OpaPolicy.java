package org.openpolicyagent.wasm;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import run.endive.runtime.ByteArrayMemory;
import run.endive.runtime.Memory;
import run.endive.wasm.types.MemoryLimits;

// final user API
// directly porting:
// https://github.com/open-policy-agent/npm-opa-wasm/blob/main/README.md
public class OpaPolicy {
    private final OpaWasm wasm;
    private int baseHeapPtr = -1;
    private int dataHeapPtr = -1;
    private int dataAddr = -1;
    private int inputAddr = -1;
    private int entrypoint;

    private OpaPolicy(OpaWasm wasm) {
        this.wasm = wasm;

        if (!(wasm.exports().opaWasmAbiVersion().getValue() == 1L
                && wasm.exports().opaWasmAbiMinorVersion().getValue() == 3L)) {
            throw new IllegalArgumentException(
                    "Invalid version, supported 1.3, detected "
                            + wasm.exports().opaWasmAbiVersion()
                            + "."
                            + wasm.exports().opaWasmAbiMinorVersion());
        }

        this.baseHeapPtr = wasm.exports().opaHeapPtrGet();
        this.dataHeapPtr = this.baseHeapPtr;
        this.dataAddr = -1;
        wasm.exports().opaHeapPtrSet(this.dataHeapPtr);
    }

    public OpaPolicy entrypoint(int entrypoint) {
        this.entrypoint = entrypoint;
        return this;
    }

    public OpaPolicy entrypoint(String entrypoint) {
        this.entrypoint = findEntrypoint(entrypoint);
        return this;
    }

    private int loadJson(String data) {
        var dataBytes = data.getBytes(StandardCharsets.UTF_8);
        var dataStrAddr = wasm.exports().opaMalloc(dataBytes.length);
        wasm.memory().write(dataStrAddr, dataBytes);
        var dstAddr = wasm.exports().opaJsonParse(dataStrAddr, dataBytes.length);
        wasm.exports().opaFree(dataStrAddr);
        return dstAddr;
    }

    private String dumpJson(int addr) {
        int resultStrAddr = wasm.exports().opaJsonDump(addr);
        var result = wasm.memory().readCString(resultStrAddr);
        wasm.exports().opaFree(resultStrAddr);
        return result;
    }

    // data MUST be a serializable object or ArrayBuffer, which assumed to be a well-formed
    // stringified JSON
    public OpaPolicy data(String data) {
        if (this.dataAddr != -1) {
            wasm.exports().opaValueFree(this.dataAddr);
        }

        wasm.exports().opaHeapPtrSet(this.baseHeapPtr);
        this.dataAddr = loadJson(data);
        this.dataHeapPtr = wasm.exports().opaHeapPtrGet();
        return this;
    }

    public OpaPolicy data(JsonNode data) {
        try {
            return data(wasm.jsonMapper().writeValueAsString(data));
        } catch (JsonProcessingException e) {
            throw new RuntimeException("Failed to serialize the provided data to Json: " + data, e);
        }
    }

    public OpaPolicy input(String input) {
        if (this.dataAddr == -1) {
            // to keep the ordering: data - input - evaluate
            data("");
        } else {
            // Reset the heap pointer before each evaluation
            wasm.exports().opaHeapPtrSet(this.dataHeapPtr);
        }

        var inputLen = input.getBytes().length;
        var delta = this.dataHeapPtr + inputLen - (wasm.memory().pages() * Memory.PAGE_SIZE);
        if (delta > 0) {
            // TODO: similar logic might go into Endive itself?
            var pageSize = (int) Math.ceil(delta / Memory.PAGE_SIZE);
            var grown = wasm.memory().grow(pageSize);
            if (grown == -1) {
                throw new RuntimeException("Maximum memory size exceeded");
            }
        }

        this.inputAddr = loadJson(input);
        return this;
    }

    public Map<String, Integer> entrypoints() {
        try {
            var json = dumpJson(wasm.exports().entrypoints());
            var entrypoints =
                    wasm.jsonMapper()
                            .readValue(json, new TypeReference<HashMap<String, Integer>>() {});
            return entrypoints;
        } catch (JsonProcessingException e) {
            throw new RuntimeException("Failed to parse the response from \"entrypoints()\"", e);
        }
    }

    public int findEntrypoint(String name) {
        var entrypoints = entrypoints();
        if (!entrypoints.containsKey(name)) {
            throw new IllegalArgumentException(
                    "Entrypoint " + name + " is not defined in this policy");
        }
        return entrypoints.get(name);
    }

    public String evaluate() {
        var ctxAddr = wasm.exports().opaEvalCtxNew();
        if (this.dataAddr == -1) {
            data("");
        }
        wasm.exports().opaEvalCtxSetData(ctxAddr, this.dataAddr);
        if (this.inputAddr == -1) {
            input("");
        }
        wasm.exports().opaEvalCtxSetInput(ctxAddr, this.inputAddr);
        wasm.exports().opaEvalCtxSetEntrypoint(ctxAddr, this.entrypoint);

        var evalResult = OpaErrorCode.fromValue(wasm.exports().eval(ctxAddr));
        if (evalResult != OpaErrorCode.OPA_ERR_OK) {
            throw new RuntimeException(
                    "Error evaluating the Opa Policy, returned code is: " + evalResult);
        }

        var resultAddr = wasm.exports().opaEvalCtxGetResult(ctxAddr);
        var result = dumpJson(resultAddr);
        wasm.exports().opaFree(resultAddr);
        return result;
    }

    public String evaluate(String input) {
        input(input);
        return evaluate();
    }

    public String evaluate(JsonNode input) {
        try {
            input(wasm.jsonMapper().writeValueAsString(input));
        } catch (JsonProcessingException e) {
            throw new RuntimeException(e);
        }
        return evaluate();
    }

    /** Package-private: used by {@link OpaPolicyPool} on return. */
    void reset() {
        if (dataAddr != -1) {
            wasm.exports().opaValueFree(dataAddr);
        }
        wasm.exports().opaHeapPtrSet(baseHeapPtr);
        dataHeapPtr = baseHeapPtr;
        dataAddr = -1;
        inputAddr = -1;
        entrypoint = 0;
    }

    public static Builder builder() {
        return new Builder();
    }

    // TODO: review the default min, max limits
    private static final int DEFAULT_MEMORY_INITIAL = 10;
    private static final int DEFAULT_MEMORY_MAX = MemoryLimits.MAX_PAGES;

    public static class Builder {
        private InputStream is;
        private ObjectMapper jsonMapper;
        private ObjectMapper yamlMapper;

        private int initialMemory = DEFAULT_MEMORY_INITIAL;
        private int maxMemory = DEFAULT_MEMORY_MAX;
        private List<OpaBuiltin.Builtin> builtins = new ArrayList<>();
        protected boolean defaultBuiltins = true;
        private boolean enableCompiler = true;

        private Builder() {}

        public Builder withPolicy(InputStream is) {
            this.is = is;
            return this;
        }

        public Builder withPolicy(ByteBuffer buffer) {
            return withPolicy(buffer.array());
        }

        public Builder withPolicy(byte[] buffer) {
            return withPolicy(new ByteArrayInputStream(buffer));
        }

        public Builder withPolicy(Path path) {
            return withPolicy(path.toFile());
        }

        public Builder withPolicy(File file) {
            try {
                return withPolicy(new FileInputStream(file));
            } catch (FileNotFoundException e) {
                throw new IllegalArgumentException("File not found at path: " + file.getPath(), e);
            }
        }

        public Builder withJsonMapper(ObjectMapper jsonMapper) {
            this.jsonMapper = jsonMapper;
            return this;
        }

        public Builder withYamlMapper(ObjectMapper yamlMapper) {
            this.yamlMapper = yamlMapper;
            return this;
        }

        public Builder withInitialMemory(int initial) {
            this.initialMemory = initial;
            return this;
        }

        public Builder withMaxMemory(int max) {
            this.maxMemory = max;
            return this;
        }

        public Builder withDefaultBuiltins(boolean defaultBuiltins) {
            this.defaultBuiltins = defaultBuiltins;
            return this;
        }

        public Builder addBuiltins(OpaBuiltin.Builtin... builtins) {
            for (var builtin : builtins) {
                this.builtins.add(builtin);
            }
            return this;
        }

        public Builder disableCompiler() {
            this.enableCompiler = false;
            return this;
        }

        public OpaPolicy build() {
            // Default management
            if (jsonMapper == null) {
                jsonMapper = DefaultMappers.jsonMapper;
            }
            if (yamlMapper == null) {
                yamlMapper = DefaultMappers.yamlMapper;
            }
            Objects.requireNonNull(is);

            var wasmBuilder =
                    OpaWasm.builder()
                            .withInputStream(is)
                            .withJsonMapper(jsonMapper)
                            .withYamlMapper(yamlMapper)
                            .withMemory(
                                    new ByteArrayMemory(new MemoryLimits(initialMemory, maxMemory)))
                            .withDefaultBuiltins(defaultBuiltins)
                            .addBuiltins(builtins.toArray(OpaBuiltin.Builtin[]::new));

            if (!enableCompiler) {
                wasmBuilder.disableCompiler();
            }

            return new OpaPolicy(wasmBuilder.build());
        }
    }
}
