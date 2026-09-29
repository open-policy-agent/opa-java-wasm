package org.openpolicyagent.wasm;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

public class OpaStringifiedSupportTest {
    static Path wasmFile;
    static String data;

    @BeforeAll
    public static void beforeAll() throws Exception {
        var targetFolder =
                OpaCli.compile(
                        "stringified-support",
                        "stringified/support",
                        "stringified/support/plainInputBoolean",
                        "stringified/support/plainInputNumber",
                        "stringified/support/plainInputString");
        wasmFile = targetFolder.resolve("policy.wasm");
        data =
                new String(
                        Files.readAllBytes(
                                OpaCli.baseSourceFolder
                                        .resolve("stringified-support")
                                        .resolve("stringified-support-data.json")));
    }

    @Test
    public void shouldAcceptStringifiedData() {
        var policy = OpaPolicy.builder().withPolicy(wasmFile).build();
        policy.data(data);

        var positiveResult = Utils.getResult(policy.evaluate("{ \"secret\" : \"secret\" }"));
        assertTrue(positiveResult.findValue("hasPermission").asBoolean());

        var negativeResult = Utils.getResult(policy.evaluate("{ \"secret\" : \"wrong\" }"));
        assertFalse(negativeResult.findValue("hasPermission").asBoolean());
    }

    @Test
    public void shouldAcceptStringifiedInputObject() {
        var policy = OpaPolicy.builder().withPolicy(wasmFile).build();
        policy.data(data);

        var positiveResult =
                Utils.getResult(
                        policy.evaluate(
                                Utils.jsonPrettyPrint(
                                        "{ \"permissions\" : [\"view:account-billing\"] }")));
        assertTrue(positiveResult.findValue("hasPermission").asBoolean());

        var negativeResult =
                Utils.getResult(
                        policy.evaluate(Utils.jsonPrettyPrint("{ \"secret\" : \"wrong\" }")));
        assertFalse(negativeResult.findValue("hasPermission").asBoolean());
    }

    @Test
    public void shouldAcceptStringifiedInputPlainBoolean() {
        var policy = OpaPolicy.builder().withPolicy(wasmFile).build();
        policy.entrypoint("stringified/support/plainInputBoolean");

        var positiveResult = Utils.getResult(policy.evaluate(Utils.jsonPrettyPrint("true")));
        assertTrue(positiveResult.asBoolean());

        var negativeResult = Utils.getResult(policy.evaluate(Utils.jsonPrettyPrint("false")));
        assertFalse(negativeResult.asBoolean());
    }

    @Test
    public void shouldAcceptStringifiedInputPlainNumber() {
        var policy = OpaPolicy.builder().withPolicy(wasmFile).build();
        policy.entrypoint("stringified/support/plainInputNumber");

        var positiveResult = Utils.getResult(policy.evaluate(Utils.jsonPrettyPrint("5")));
        assertTrue(positiveResult.asBoolean());

        var negativeResult = Utils.getResult(policy.evaluate(Utils.jsonPrettyPrint("6")));
        assertFalse(negativeResult.asBoolean());
    }

    @Test
    public void shouldAcceptStringifiedInputPlainString() {
        var policy = OpaPolicy.builder().withPolicy(wasmFile).build();
        policy.entrypoint("stringified/support/plainInputString");

        var positiveResult = Utils.getResult(policy.evaluate(Utils.jsonPrettyPrint("\"test\"")));
        assertTrue(positiveResult.asBoolean());

        var negativeResult = Utils.getResult(policy.evaluate(Utils.jsonPrettyPrint("\"invalid\"")));
        assertFalse(negativeResult.asBoolean());
    }
}
