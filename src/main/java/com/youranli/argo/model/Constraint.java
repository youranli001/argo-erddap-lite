package com.youranli.argo.model;

import java.util.List;

/**
 * Parsed query constraints from a {@code /data} URL.
 *
 * <p>{@code cycleFilter} of {@code null} means "all cycles". Otherwise it is the
 * explicit, sorted, deduplicated list of cycles to keep.
 *
 * <p>{@code parameters} is the explicit, sorted list of variables to include.
 * It is never empty — the parser substitutes a default ({@code TEMP, PSAL, PRES})
 * when the caller omits the parameter.
 */
public record Constraint(
        List<Integer> cycleFilter,
        List<String> parameters
) {
    public boolean keepsAllCycles() {
        return cycleFilter == null;
    }
}
