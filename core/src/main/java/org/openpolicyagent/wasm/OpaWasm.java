package org.openpolicyagent.wasm;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.openpolicyagent.wasm.builtins.Provided;
import run.endive.annotations.WasmModuleInterface;
import run.endive.compiler.MachineFactoryCompiler;
import run.endive.runtime.Instance;
import run.endive.runtime.Memory;
import run.endive.wasm.Parser;

// Low level bindings to OPA
@WasmModuleInterface("demo-policy.wasm")
public class OpaWasm implements OpaWasm_ModuleImports, OpaWasm_Env {
    private final Instance instance;
    private final Memory memory;
    private final OpaWasm_ModuleExports exports;

    private final ObjectMapper jsonMapper;
    private final ObjectMapper yamlMapper;
    private final OpaBuiltin.Builtin[] builtins;

    public static Builder builder() {
        return new Builder();
    }

    private OpaWasm(
            InputStream is,
            ObjectMapper jsonMapper,
            ObjectMapper yamlMapper,
            Memory memory,
            boolean defaultBuiltins,
            boolean enableCompiler,
            OpaBuiltin.Builtin[] builtins) {
        this.jsonMapper = jsonMapper;
        this.yamlMapper = yamlMapper;
        this.memory = memory;
        var instanceBuilder =
                Instance.builder(Parser.parse(is))
                        .withImportValues(toImportValues())
                        .withMemoryFactory(limits -> memory);

        if (enableCompiler) {
            instanceBuilder.withMachineFactory(MachineFactoryCompiler::compile);
        }

        this.instance = instanceBuilder.build();
        this.exports = new OpaWasm_ModuleExports(instance);
        this.builtins = initializeBuiltins(defaultBuiltins, builtins);
    }

    public OpaBuiltin.Builtin[] initializeBuiltins(
            boolean defaultBuiltins, OpaBuiltin.Builtin[] builtins) {
        var mappings = new HashMap<String, Integer>();
        int builtinsAddr = exports.builtins();
        var builtinsStrAddr = exports.opaJsonDump(builtinsAddr);
        var builtinsStr = memory().readCString(builtinsStrAddr);
        exports.opaFree(builtinsStrAddr);
        exports.opaFree(builtinsAddr);
        try {
            var fields = jsonMapper().readTree(builtinsStr).fields();
            while (fields.hasNext()) {
                var field = fields.next();
                mappings.put(field.getKey(), field.getValue().intValue());
            }
        } catch (JsonProcessingException e) {
            throw new RuntimeException(e);
        }

        Map<Integer, OpaBuiltin.Builtin> result = new HashMap<>();
        // Default initialization to have proper error messages
        for (var m : mappings.entrySet()) {
            result.put(m.getValue(), () -> m.getKey());
        }
        for (var builtin : builtins) {
            if (mappings.containsKey(builtin.name())) {
                result.put(mappings.get(builtin.name()), builtin);
            }
        }
        if (defaultBuiltins) {
            // provided builtins override anything with clashing name
            var all = Provided.all();
            for (var builtin : all) {
                if (mappings.containsKey(builtin.name())) {
                    result.put(mappings.get(builtin.name()), builtin);
                }
            }
        }

        int size = result.keySet().stream().mapToInt(i -> i + 1).max().orElse(0);
        var finalResult = new OpaBuiltin.Builtin[size];
        for (int i = 0; i < size; i++) {
            if (result.containsKey(i)) {
                finalResult[i] = result.get(i);
            } else {
                finalResult[i] = null;
            }
        }

        return finalResult;
    }

    public static class Builder {
        private OpaWasm_Env imports;
        private InputStream is;
        private ObjectMapper jsonMapper;
        private ObjectMapper yamlMapper;
        private Memory memory;
        private List<OpaBuiltin.Builtin> builtins = new ArrayList<>();
        protected boolean defaultBuiltins = true;
        private boolean enableCompiler = true;

        private Builder() {}

        public Builder withInputStream(InputStream is) {
            this.is = is;
            return this;
        }

        public Builder withJsonMapper(ObjectMapper jsonMapper) {
            this.jsonMapper = jsonMapper;
            return this;
        }

        public Builder withYamlMapper(ObjectMapper yamlMapper) {
            this.yamlMapper = yamlMapper;
            return this;
        }

        public Builder withDefaultBuiltins(boolean db) {
            this.defaultBuiltins = db;
            return this;
        }

        public Builder addBuiltins(OpaBuiltin.Builtin... builtins) {
            for (var builtin : builtins) {
                this.builtins.add(builtin);
            }
            return this;
        }

        public Builder withMemory(Memory memory) {
            this.memory = memory;
            return this;
        }

        public Builder disableCompiler() {
            this.enableCompiler = false;
            return this;
        }

        public OpaWasm build() {
            return new OpaWasm(
                    is,
                    jsonMapper,
                    yamlMapper,
                    memory,
                    defaultBuiltins,
                    enableCompiler,
                    builtins.toArray(OpaBuiltin.Builtin[]::new));
        }
    }

    public OpaWasm_Env env() {
        return this;
    }

    public ObjectMapper jsonMapper() {
        return jsonMapper;
    }

    public ObjectMapper yamlMapper() {
        return yamlMapper;
    }

    public Instance instance() {
        return this.instance;
    }

    public OpaWasm_ModuleExports exports() {
        return this.exports;
    }

    // helper functions - can be written by the end user
    public String readString(int addr) {
        int resultAddr = exports.opaJsonDump(addr);
        var resultStr = memory().readCString(resultAddr);
        exports.opaFree(resultAddr);
        return resultStr;
    }

    public int writeResult(String result) {
        var bytes = result.getBytes(StandardCharsets.UTF_8);
        var resultStrAddr = exports.opaMalloc(bytes.length);
        memory().write(resultStrAddr, bytes);
        var resultAddr = exports.opaJsonParse(resultStrAddr, bytes.length);
        exports.opaFree(resultStrAddr);
        return resultAddr;
    }

    @Override
    public void opaAbort(int ptr) {
        var errorMessage = instance().memory().readCString(ptr);
        throw new OpaAbortException("opa_abort - " + errorMessage);
    }

    @Override
    public Memory memory() {
        return memory;
    }

    @Override
    public int opaBuiltin0(int builtinId, int ctx) {
        return builtins[builtinId].asBuiltin0(this);
    }

    @Override
    public int opaBuiltin1(int builtinId, int ctx, int _1) {
        return builtins[builtinId].asBuiltin1(this, _1);
    }

    @Override
    public int opaBuiltin2(int builtinId, int ctx, int _1, int _2) {
        return builtins[builtinId].asBuiltin2(this, _1, _2);
    }

    @Override
    public int opaBuiltin3(int builtinId, int ctx, int _1, int _2, int _3) {
        return builtins[builtinId].asBuiltin3(this, _1, _2, _3);
    }

    @Override
    public int opaBuiltin4(int builtinId, int ctx, int _1, int _2, int _3, int _4) {
        return builtins[builtinId].asBuiltin4(this, _1, _2, _3, _4);
    }
}
