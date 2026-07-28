package org.mediaecosystem.experimental.platformproof.model;

public enum FormatDisposition {
    PASSED("passed"),
    FAILED("failed"),
    INCONCLUSIVE("inconclusive"),
    NOT_RUN("not run");

    private final String wireValue;

    FormatDisposition(String wireValue) {
        this.wireValue = wireValue;
    }

    public String wireValue() {
        return wireValue;
    }
}
