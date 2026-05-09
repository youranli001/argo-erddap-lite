package com.youranli.argo.format;

import com.youranli.argo.model.CycleRecord;

import java.util.List;
import java.util.Map;

/**
 * Renders a list of {@link CycleRecord}s as a long-format CSV string.
 *
 * <p>Long format is one row per (cycle × level), columns:
 * {@code cycle,date,latitude,longitude,pressure,parameter,value}.
 *
 * <p>Long format is preferred over wide because Argo profiles have variable lengths
 * across cycles — a wide format would force ragged padding with empty cells, while
 * long format keeps the file dense and trivially loadable into pandas with
 * {@code pd.read_csv()}.
 */
public final class CsvFormatter {

    private CsvFormatter() {}

    public static String format(List<CycleRecord> records, String pressureParam) {
        StringBuilder sb = new StringBuilder();
        sb.append("cycle,date,latitude,longitude,pressure,parameter,value\n");

        for (CycleRecord rec : records) {
            Map<String, Float[]> params = rec.parameters();
            Float[] pres = params.get(pressureParam);
            if (pres == null) continue;  // need pressure as the vertical axis

            int n = pres.length;
            for (var entry : params.entrySet()) {
                String paramName = entry.getKey();
                if (paramName.equals(pressureParam)) continue;  // pressure is the axis, not a value

                Float[] values = entry.getValue();
                int len = Math.min(n, values.length);

                for (int i = 0; i < len; i++) {
                    if (pres[i] == null || values[i] == null) continue;

                    sb.append(rec.cycle()).append(',')
                      .append(safe(rec.date())).append(',')
                      .append(rec.latitude()).append(',')
                      .append(rec.longitude()).append(',')
                      .append(pres[i]).append(',')
                      .append(paramName).append(',')
                      .append(values[i]).append('\n');
                }
            }
        }
        return sb.toString();
    }

    private static String safe(String s) {
        return s == null ? "" : s;
    }
}
