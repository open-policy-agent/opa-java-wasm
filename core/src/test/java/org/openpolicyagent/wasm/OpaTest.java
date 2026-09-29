package org.openpolicyagent.wasm;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.fasterxml.jackson.core.JsonProcessingException;
import java.io.FileInputStream;
import java.nio.file.Path;
import org.instancio.Instancio;
import org.instancio.junit.Given;
import org.instancio.junit.GivenProvider;
import org.instancio.junit.InstancioExtension;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.RepeatedTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import run.endive.runtime.ByteBufferMemory;
import run.endive.wasm.types.MemoryLimits;

@ExtendWith(InstancioExtension.class)
public class OpaTest {
    static Path wasmFile;
    static Path issue69WasmFile;
    static OpaPolicy issue107Policy;

    @BeforeAll
    public static void beforeAll() throws Exception {
        wasmFile = OpaCli.compile("base", "opa/wasm/test/allowed").resolve("policy.wasm");
        issue69WasmFile = OpaCli.compile("issue69", "authz/allow").resolve("policy.wasm");
        issue107Policy =
                OpaPolicy.builder()
                        .withPolicy(OpaCli.compile("issue107", "test").resolve("policy.wasm"))
                        .build();
    }

    @Test
    public void lowLevelAPI() throws Exception {
        var opa =
                OpaWasm.builder()
                        .withJsonMapper(DefaultMappers.jsonMapper)
                        .withMemory(new ByteBufferMemory(new MemoryLimits(2, 2)))
                        .withInputStream(new FileInputStream(wasmFile.toFile()))
                        .build();
        var opaExports = opa.exports();

        assertEquals(opaExports.opaWasmAbiVersion().getValue(), 1L);
        assertEquals(opaExports.opaWasmAbiMinorVersion().getValue(), 3L);

        var builtinsAddr = opaExports.builtins();
        var builtinsStringAddr = opaExports.opaJsonDump(builtinsAddr);
        assertEquals("{}", opaExports.memory().readCString(builtinsStringAddr));

        // Following the instructions here:
        // https://www.openpolicyagent.org/docs/latest/wasm/#evaluation
        var ctxAddr = opaExports.opaEvalCtxNew();

        var input = "{\"user\": \"alice\"}";
        var inputStrAddr = opaExports.opaMalloc(input.length());
        opaExports.memory().writeCString(inputStrAddr, input);
        var inputAddr = opaExports.opaJsonParse(inputStrAddr, input.length());
        opaExports.opaFree(inputStrAddr);
        opaExports.opaEvalCtxSetInput(ctxAddr, inputAddr);

        var data = "{ \"role\" : { \"alice\" : \"admin\", \"bob\" : \"user\" } }";
        var dataStrAddr = opaExports.opaMalloc(data.length());
        opaExports.memory().writeCString(dataStrAddr, data);
        var dataAddr = opaExports.opaJsonParse(dataStrAddr, data.length());
        opaExports.opaFree(dataStrAddr);
        opaExports.opaEvalCtxSetData(ctxAddr, dataAddr);

        var evalResult = OpaErrorCode.fromValue(opaExports.eval(ctxAddr));
        assertEquals(OpaErrorCode.OPA_ERR_OK, evalResult);

        int resultAddr = opaExports.opaEvalCtxGetResult(ctxAddr);
        int resultStrAddr = opaExports.opaJsonDump(resultAddr);
        var resultStr = opaExports.memory().readCString(resultStrAddr);
        opaExports.opaFree(resultStrAddr);
        assertEquals("[{\"result\":true}]", resultStr);

        // final cleanup of resources for demo purposes
        opaExports.opaValueFree(inputAddr);
        opaExports.opaValueFree(dataAddr);
        opaExports.opaValueFree(resultAddr);
    }

    @Test
    public void highLevelAPI() throws Exception {
        var policy = OpaPolicy.builder().withPolicy(wasmFile).build();
        policy.data("{ \"role\" : { \"alice\" : \"admin\", \"bob\" : \"user\" } }");

        // evaluate the admin
        policy.input("{\"user\": \"alice\"}");
        Assertions.assertTrue(Utils.getResult(policy.evaluate()).asBoolean());

        // evaluate a user
        policy.input("{\"user\": \"bob\"}");
        Assertions.assertFalse(Utils.getResult(policy.evaluate()).asBoolean());

        // change the data of the policy
        policy.data("{ \"role\" : { \"bob\" : \"admin\", \"alice\" : \"user\" } }");

        // evaluate the admin
        policy.input("{\"user\": \"bob\"}");
        Assertions.assertTrue(Utils.getResult(policy.evaluate()).asBoolean());

        // evaluate a user
        policy.input("{\"user\": \"alice\"}");
        Assertions.assertFalse(Utils.getResult(policy.evaluate()).asBoolean());
    }

    @Test
    public void issue69() throws Exception {
        var policy = OpaPolicy.builder().withPolicy(issue69WasmFile).build();

        policy.input("{\"method\":\"GET\"}");
        Assertions.assertTrue(Utils.getResult(policy.evaluate()).asBoolean());

        policy.input("{\"method\":\"POST\"}");
        Assertions.assertFalse(Utils.getResult(policy.evaluate()).asBoolean());
    }

    @Test
    public void issue107() {
        var policy = issue107Policy;

        policy.input("{\"role\": \"admin\",\"name\": \"Doe\"}");
        var result = policy.evaluate();
        Assertions.assertTrue(Utils.getResult(result).get("allow").asBoolean());

        var input = "{\"role\": \"admin\",\"name\": \"Štěpán\"}";

        policy.input(input);
        result = policy.evaluate();
        Assertions.assertTrue(Utils.getResult(result).get("allow").asBoolean());
    }

    static class InputStringProvider implements GivenProvider {
        @Override
        public Object provide(ElementContext context) {
            String randomName = Instancio.gen().string().unicode().mixedCase().get();
            try {
                String input = "{\"role\": \"admin\",\"name\": \"" + randomName + "\"}";
                DefaultMappers.jsonMapper.readTree(input);
                return randomName;
            } catch (JsonProcessingException e) {
                // the generated input name breaks the json escaping
                return provide(context);
            }
        }
    }

    @RepeatedTest(1000)
    public void issue107Parametrized(@Given(InputStringProvider.class) String name) {
        var policy = issue107Policy;

        policy.input("{\"role\": \"admin\",\"name\": \"" + name + "\"}");
        var result = policy.evaluate();
        Assertions.assertTrue(Utils.getResult(result).get("allow").asBoolean());

        policy.input("{\"role\": \"notadmin\",\"name\": \"" + name + "\"}");

        result = policy.evaluate();
        Assertions.assertFalse(Utils.getResult(result).get("allow").asBoolean());
    }

    @Test
    public void issue78Sprintf() throws Exception {
        var policyWasm =
                OpaCli.compile("issue78-sprintf", "armo_builtins/deny").resolve("policy.wasm");
        var policy = OpaPolicy.builder().withPolicy(policyWasm).build();
        var pod =
                "apiVersion: v1\n"
                        + "kind: Pod\n"
                        + "metadata:\n"
                        + "  name: static-web\n"
                        + "  labels:\n"
                        + "    role: myrole\n"
                        + "spec:\n"
                        + "  securityContext:\n"
                        + "    runAsNonRoot: false\n"
                        + "  containers:\n"
                        + "    - name: web\n"
                        + "      image: nginx\n"
                        + "      ports:\n"
                        + "        - name: web\n"
                        + "          containerPort: 80\n"
                        + "          protocol: TCP\n"
                        + "      securityContext:\n"
                        + "        runAsGroup: 1000\n";

        // should be an array of Pods
        var rawInput = DefaultMappers.jsonMapper.createArrayNode();
        rawInput.add(DefaultMappers.yamlMapper.readTree(pod));

        var input = DefaultMappers.jsonMapper.writeValueAsString(rawInput);

        var result = policy.evaluate(input);

        var resultNode = Utils.getResult(result).get(0);
        var alertMessage = resultNode.get("alertMessage").asText();
        var alertScore = resultNode.get("alertScore").asInt();

        assertEquals("container: web in pod: static-web  may run as root", alertMessage);
        assertEquals(7, alertScore);
    }

    @Test
    public void issue78Updated() throws Exception {
        var policyWasm =
                OpaCli.compile("issue78-updated", "armo_builtins/deny").resolve("policy.wasm");
        var policy = OpaPolicy.builder().withPolicy(policyWasm).build();
        var pod =
                "apiVersion: v1\n"
                        + "kind: Pod\n"
                        + "metadata:\n"
                        + "  name: static-web\n"
                        + "  labels:\n"
                        + "    role: myrole\n"
                        + "spec:\n"
                        + "  securityContext:\n"
                        + "    runAsNonRoot: false\n"
                        + "  containers:\n"
                        + "    - name: web\n"
                        + "      image: nginx\n"
                        + "      ports:\n"
                        + "        - name: web\n"
                        + "          containerPort: 80\n"
                        + "          protocol: TCP\n"
                        + "      securityContext:\n"
                        + "        runAsGroup: 1000";

        // should be an array of Pods
        var rawInput = DefaultMappers.jsonMapper.createArrayNode();
        rawInput.add(DefaultMappers.yamlMapper.readTree(pod));

        var input = DefaultMappers.jsonMapper.writeValueAsString(rawInput);

        var result = policy.evaluate(input);

        var resultNode = Utils.getResult(result).get(0);
        var alertMessage = resultNode.get("alertMessage").asText();
        var alertScore = resultNode.get("alertScore").asInt();

        assertEquals("container: web in pod: static-web may run as root", alertMessage);
        assertEquals(7, alertScore);
    }
}
