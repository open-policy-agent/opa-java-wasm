package org.openpolicyagent.wasm.testcases;

import java.nio.file.Path;

public class TestCaseData {
    private final Path policy;
    private final Case caze;

    public TestCaseData(Path policy, Case c) {
        this.caze = c;
        this.policy = policy;
    }

    public Case getCase() {
        return caze;
    }

    public Path getPolicy() {
        return policy;
    }

    @Override
    public String toString() {
        return "TestCaseData{"
                + "policy="
                + policy.getParent().getFileName()
                + ", case="
                + caze
                + '}';
    }
}
