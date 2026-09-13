package com.create.productionline.line.mapper;

import com.create.productionline.line.scheme.LineScheme;

/**
 * Result of a recipe mapping attempt.
 *
 * @param ok      true when a usable scheme was produced
 * @param scheme  the produced plan (may be empty when {@code ok == false})
 * @param message human readable outcome; used by the computer GUI ("无法映射" cases, TC-01)
 */
public record MappingResult(boolean ok, LineScheme scheme, String message) {

    public static MappingResult success(LineScheme scheme) {
        return new MappingResult(true, scheme, "");
    }

    public static MappingResult failure(String message) {
        return new MappingResult(false, new LineScheme(), message);
    }

    public MappingResult {
        if (scheme == null) {
            scheme = new LineScheme();
        }
        if (message == null) {
            message = "";
        }
    }
}
