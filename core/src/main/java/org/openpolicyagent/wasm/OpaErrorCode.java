package org.openpolicyagent.wasm;

public enum OpaErrorCode {
    OPA_ERR_OK, // OPA_ERR_OK	No error.
    OPA_ERR_INTERNAL, // Unrecoverable internal error.
    OPA_ERR_INVALID_TYPE, // Invalid value type was encountered.
    OPA_ERR_INVALID_PATH; // Invalid object path reference.

    static OpaErrorCode fromValue(int i) {
        switch (i) {
            case 0:
                return OPA_ERR_OK;
            case 1:
                return OPA_ERR_INTERNAL;
            case 2:
                return OPA_ERR_INVALID_TYPE;
            case 3:
                return OPA_ERR_INVALID_PATH;
            default:
                throw new IllegalArgumentException("Invalid Opa Error code " + i);
        }
    }
}
