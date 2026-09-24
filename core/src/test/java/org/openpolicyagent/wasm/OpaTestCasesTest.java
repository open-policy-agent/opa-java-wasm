package org.openpolicyagent.wasm;

import static java.nio.file.StandardCopyOption.REPLACE_EXISTING;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.openpolicyagent.wasm.OpaCli.baseDestFolder;
import static org.openpolicyagent.wasm.OpaCli.testcasesDestFolder;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.node.IntNode;
import com.fasterxml.jackson.databind.node.TextNode;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Base64;
import java.util.stream.Stream;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.condition.DisabledIfEnvironmentVariable;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.openpolicyagent.wasm.testcases.TestCaseData;
import org.openpolicyagent.wasm.testcases.Testcases;

@DisabledIfEnvironmentVariable(named = "OPA_TESTSUITE", matches = "disabled")
public class OpaTestCasesTest {
    static ObjectMapper mapper = new ObjectMapper();

    static {
        // for reliable tests results fields order
        mapper.configure(SerializationFeature.ORDER_MAP_ENTRIES_BY_KEYS, true);
    }

    static boolean run = false;
    static OpaBuiltin.Builtin[] customBuiltins =
            new OpaBuiltin.Builtin[] {
                OpaBuiltin.from(
                        "custom_builtin_test", (instance, a) -> IntNode.valueOf(a.asInt() + 1)),
                OpaBuiltin.from(
                        "custom_builtin_test_impure", (instance) -> TextNode.valueOf("foo")),
                OpaBuiltin.from(
                        "custom_builtin_test_memoization",
                        (instance) -> {
                            if (run) {
                                throw new RuntimeException("should have been memoized");
                            }
                            run = true;
                            return IntNode.valueOf(100);
                        })
            };

    @BeforeAll
    public static void beforeAll() throws Exception {
        OpaCli.prepareTestcases();
    }

    private static Stream<Arguments> walkTestcasesFolder() throws IOException {
        var allTests = new ArrayList<TestCaseData>();
        var files = testcasesDestFolder.toFile().list((ignored, name) -> name.endsWith(".json"));

        for (var file : files) {
            var testName = file.split("\\.")[0];
            var testcases =
                    mapper.readValue(testcasesDestFolder.resolve(file).toFile(), Testcases.class);

            for (int i = 0; i < testcases.cases().length; i++) {
                var testcaseDestFolderName = testName + "_" + i;
                var testcaseDestFolder = baseDestFolder.resolve(testcaseDestFolderName);
                testcaseDestFolder.toFile().mkdirs();

                var policy = testcaseDestFolder.resolve("policy.wasm");
                Files.write(
                        policy, Base64.getDecoder().decode(testcases.cases()[i].wasm().getBytes()));
                Files.copy(
                        Path.of("src", "test", "resources", "capabilities.json"),
                        testcaseDestFolder.resolve("capabilities.json"),
                        REPLACE_EXISTING);

                allTests.add(new TestCaseData(policy, testcases.cases()[i]));
            }
        }
        return allTests.stream().map(f -> Arguments.of(f));
    }

    private boolean findResult(OpaPolicy policy, JsonNode input, JsonNode expected) {
        try {
            var sortedExpected =
                    mapper.writeValueAsString(mapper.treeToValue(expected, Object.class));

            String result = null;
            if (input == null) {
                // TODO: review the most sensitive default for the SDK itself
                result = policy.evaluate("");
            } else {
                result = policy.evaluate(input);
            }

            var results = mapper.readTree(result).elements();
            while (results.hasNext()) {
                var strResult =
                        mapper.writeValueAsString(mapper.treeToValue(results.next(), Object.class));

                if (sortedExpected.equals(strResult)) {
                    return true;
                }
            }
            return false;
        } catch (JsonProcessingException e) {
            throw new RuntimeException(e);
        }
    }

    private void assertSingleResult(OpaPolicy policy, JsonNode input, JsonNode expected) {
        try {
            var sortedExpected =
                    mapper.writeValueAsString(mapper.treeToValue(expected, Object.class));

            String result = null;
            if (input == null) {
                // TODO: review the most sensitive default for the SDK itself
                result = policy.evaluate("");
            } else {
                result = policy.evaluate(input);
            }

            var firstResult =
                    mapper.writeValueAsString(
                            mapper.treeToValue(
                                    mapper.readTree(result).elements().next(), Object.class));
            assertEquals(sortedExpected, firstResult);
        } catch (JsonProcessingException e) {
            throw new RuntimeException(e);
        }
    }

    @ParameterizedTest
    @MethodSource("walkTestcasesFolder")
    void externalTestcases(TestCaseData data) throws Exception {
        var policy =
                OpaPolicy.builder()
                        .addBuiltins(customBuiltins)
                        .withPolicy(data.getPolicy())
                        .disableCompiler()
                        .build();
        assertEquals(1, policy.entrypoints().size());

        if (data.getCase().data() == null) {
            // TODO: review the most sensitive default for the SDK itself
            policy.data("");
        } else {
            policy.data(data.getCase().data());
        }

        if (data.getCase().wantError() != null) {
            var exception =
                    assertThrows(
                            OpaAbortException.class, () -> policy.evaluate(data.getCase().input()));
            assertTrue(
                    exception.getMessage().contains(data.getCase().wantError()),
                    "The exception with message: "
                            + exception.getMessage()
                            + " doesn't contain the expected text: "
                            + data.getCase().wantError());
        } else if (data.getCase().wantResult() != null && data.getCase().wantResult().length > 0) {
            if (data.getCase().wantResult().length == 1) {
                assertSingleResult(policy, data.getCase().input(), data.getCase().wantResult()[0]);
            } else {
                int found = 0;
                for (var expected : data.getCase().wantResult()) {
                    if (findResult(policy, data.getCase().input(), expected)) {
                        found++;
                    }
                }

                assertEquals(data.getCase().wantResult().length, found);
            }
        } else if (data.getCase().wantDefined()) {
            var result = policy.evaluate(data.getCase().input());

            assertFalse(mapper.readTree(result).isNull());
        } else {
            var result = policy.evaluate(data.getCase().input());
            // TODO: is this the expected output in this case?
            assertEquals("[]", result);
        }
    }
}
