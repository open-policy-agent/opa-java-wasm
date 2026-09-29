package org.openpolicyagent.wasm.builtins;

import java.util.Arrays;
import org.openpolicyagent.wasm.OpaBuiltin;

public class Provided {

    private static OpaBuiltin.Builtin[] all(OpaBuiltin.Builtin[]... builtins) {
        var res = builtins[0];
        for (int i = 1; i < builtins.length; i++) {
            res = merge(res, builtins[i]);
        }
        return res;
    }

    private static OpaBuiltin.Builtin[] merge(
            OpaBuiltin.Builtin[] builtin1, OpaBuiltin.Builtin[] builtin2) {
        var res = Arrays.copyOf(builtin1, builtin1.length + builtin2.length);
        System.arraycopy(builtin2, 0, res, builtin1.length, builtin2.length);
        return res;
    }

    public static OpaBuiltin.Builtin[] all() {
        return all(String.all(), Json.all(), Yaml.all());
    }
}
