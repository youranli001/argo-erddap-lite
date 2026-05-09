package com.youranli.argo.format;

import java.util.Locale;

/**
 * Output formats supported by the {@code /data} endpoint.
 *
 * <p>Mirrors ERDDAP's "{datasetID}.{format}" URL pattern, but with a tiny subset:
 * just {@code json} and {@code csv}.
 */
public enum ResponseFormat {
    JSON("application/json"),
    CSV("text/csv");

    private final String mimeType;

    ResponseFormat(String mimeType) {
        this.mimeType = mimeType;
    }

    public String mimeType() {
        return mimeType;
    }

    /** Parse a URL suffix like "json" or "CSV" into a format. Throws on unknown values. */
    public static ResponseFormat fromString(String s) {
        if (s == null) {
            throw new IllegalArgumentException("format suffix is required (e.g. .json, .csv)");
        }
        return switch (s.toLowerCase(Locale.ROOT)) {
            case "json" -> JSON;
            case "csv" -> CSV;
            default -> throw new IllegalArgumentException(
                    "unsupported format '" + s + "', must be one of: json, csv");
        };
    }
}
