package com.youranli.argo.service;

import com.youranli.argo.model.Constraint;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;

/**
 * Parses the {@code cycles} and {@code parameters} query-string values from a {@code /data}
 * URL into a {@link Constraint}.
 *
 * <p>Cycles syntax follows a small DSL inspired by the {@code -p} flag in {@code lp},
 * but accepts only positive integers:
 * <ul>
 *   <li>{@code "1-10"}    — range, inclusive on both ends</li>
 *   <li>{@code "1,3,5"}   — explicit list</li>
 *   <li>{@code "1-3,7,9-11"} — combinations</li>
 *   <li>{@code "all"} or {@code null} — keep all cycles</li>
 * </ul>
 *
 * <p>Parameters syntax is a comma-separated list of Argo variable names, e.g.
 * {@code "TEMP,PSAL,DOXY"}. Names are upper-cased to match Argo conventions.
 */
public final class ConstraintParser {

    private static final List<String> DEFAULT_PARAMETERS = List.of("TEMP", "PSAL", "PRES");

    private ConstraintParser() {}

    public static Constraint parse(String cyclesParam, String parametersParam) {
        return new Constraint(parseCycles(cyclesParam), parseParameters(parametersParam));
    }

    /** @return null if all cycles should be kept, otherwise a sorted unique list. */
    static List<Integer> parseCycles(String raw) {
        if (raw == null || raw.isBlank() || raw.equalsIgnoreCase("all")) {
            return null;
        }

        LinkedHashSet<Integer> out = new LinkedHashSet<>();
        for (String token : raw.split(",")) {
            String t = token.trim();
            if (t.isEmpty()) continue;

            if (t.contains("-")) {
                String[] parts = t.split("-", 2);
                int start = parseNonNegative(parts[0], t);
                int end = parseNonNegative(parts[1], t);
                if (end < start) {
                    throw new IllegalArgumentException(
                            "cycles range '" + t + "' has end < start");
                }
                for (int i = start; i <= end; i++) out.add(i);
            } else {
                out.add(parseNonNegative(t, t));
            }
        }

        List<Integer> sorted = new ArrayList<>(out);
        sorted.sort(Integer::compareTo);
        return sorted;
    }

    static List<String> parseParameters(String raw) {
        if (raw == null || raw.isBlank()) {
            return DEFAULT_PARAMETERS;
        }
        // Preserve order as the caller specified it, but dedupe.
        LinkedHashSet<String> out = new LinkedHashSet<>();
        Arrays.stream(raw.split(","))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .map(s -> s.toUpperCase(Locale.ROOT))
                .forEach(out::add);
        if (out.isEmpty()) return DEFAULT_PARAMETERS;
        return new ArrayList<>(out);
    }

    private static int parseNonNegative(String s, String context) {
        try {
            int n = Integer.parseInt(s.trim());
            if (n < 0) {
                throw new IllegalArgumentException(
                        "cycle number must be >= 0 in '" + context + "'");
            }
            return n;
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException(
                    "cycles segment '" + context + "' is not a valid integer or range");
        }
    }
}
