package org.openpolicyagent.wasm.it;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;
import org.openpolicyagent.wasm.OpaPolicyFactory;

public class BuildTimeCompiledPolicyTest {

    @Test
    public void evaluatesBuildTimeCompiledPolicy() {
        var factory =
                OpaPolicyFactory.builder()
                        .withPolicy(DemoPolicy.load())
                        .withMachineFactory(DemoPolicy::create)
                        .build();

        var policy = factory.newPolicy().data("{\"role\": {\"alice\": \"admin\"}}");

        assertEquals("[{\"result\":true}]", policy.evaluate("{\"user\": \"alice\"}"));
        assertEquals("[{\"result\":false}]", policy.evaluate("{\"user\": \"bob\"}"));
    }
}
