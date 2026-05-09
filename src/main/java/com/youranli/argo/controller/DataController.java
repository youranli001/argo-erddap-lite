package com.youranli.argo.controller;

import com.youranli.argo.format.CsvFormatter;
import com.youranli.argo.format.ResponseFormat;
import com.youranli.argo.model.Constraint;
import com.youranli.argo.model.CycleRecord;
import com.youranli.argo.service.ArgoFileReader;
import com.youranli.argo.service.ConstraintParser;
import com.youranli.argo.service.DatasetRegistry;
import io.javalin.http.Context;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Handler for the {@code /data/{floatId}.{format}} endpoint, which mirrors the
 * ERDDAP "{datasetID}.{format}?{constraints}" URL convention.
 *
 * <p>Supported constraints:
 * <ul>
 *   <li>{@code cycles=1-10} or {@code cycles=1,3,5} or {@code cycles=all}</li>
 *   <li>{@code parameters=TEMP,PSAL,DOXY}</li>
 * </ul>
 *
 * <p>If a parameter is requested but not present in the file, it is silently dropped from
 * the response — same behaviour as ERDDAP's variable-subset interface.
 */
public final class DataController {

    /** PRES is treated as the vertical axis, included automatically and used as the CSV index. */
    private static final String PRESSURE_PARAM = "PRES";

    private final DatasetRegistry registry;

    public DataController(DatasetRegistry registry) {
        this.registry = registry;
    }

    public void query(Context ctx) throws IOException {
        String floatId = ctx.pathParam("floatId");
        ResponseFormat format = ResponseFormat.fromString(ctx.pathParam("format"));
        Constraint constraint = ConstraintParser.parse(
                ctx.queryParam("cycles"),
                ctx.queryParam("parameters")
        );

        DatasetRegistry.Entry entry = registry.require(floatId);

        List<CycleRecord> records;
        try (ArgoFileReader reader = new ArgoFileReader(entry.profPath())) {
            records = buildRecords(reader, constraint);
        }

        switch (format) {
            case JSON -> ctx.contentType(format.mimeType()).json(records);
            case CSV -> {
                String csv = CsvFormatter.format(records, PRESSURE_PARAM);
                ctx.contentType(format.mimeType()).result(csv);
            }
        }
    }

    /**
     * Read profile data from the file, apply cycle/parameter filtering, and produce
     * {@link CycleRecord}s suitable for either JSON or CSV output.
     */
    private List<CycleRecord> buildRecords(ArgoFileReader reader, Constraint constraint)
            throws IOException {
        int[] cycles = reader.readCycleNumbers();
        double[] lat = reader.readLatitudes();
        double[] lon = reader.readLongitudes();
        double[] juld = reader.readJuld();

        // Decide which cycle indices to keep.
        Set<Integer> keepCycles = constraint.keepsAllCycles() ? null : new HashSet<>(constraint.cycleFilter());

        // Always include PRES so JSON and CSV consumers have the vertical axis.
        List<String> requested = new ArrayList<>(constraint.parameters());
        if (!requested.contains(PRESSURE_PARAM)) {
            requested.add(0, PRESSURE_PARAM);
        }

        // Read each requested 2D variable once. Drop variables that don't exist in the file.
        Map<String, float[][]> data = new LinkedHashMap<>();
        for (String param : requested) {
            float[][] arr = reader.read2DFloat(param);
            if (arr != null) data.put(param, arr);
        }

        List<CycleRecord> out = new ArrayList<>();
        for (int i = 0; i < cycles.length; i++) {
            int cycle = cycles[i];
            if (keepCycles != null && !keepCycles.contains(cycle)) continue;

            Map<String, Float[]> perParam = new LinkedHashMap<>();
            int nLevels = -1;
            for (var entry : data.entrySet()) {
                float[][] arr = entry.getValue();
                if (i >= arr.length) continue;
                float[] row = arr[i];
                if (nLevels < 0) nLevels = row.length;
                Float[] boxed = new Float[row.length];
                for (int k = 0; k < row.length; k++) {
                    boxed[k] = Float.isNaN(row[k]) ? null : row[k];
                }
                perParam.put(entry.getKey(), boxed);
            }

            out.add(new CycleRecord(
                    cycle,
                    safeDouble(lat, i),
                    safeDouble(lon, i),
                    safeDouble(juld, i),
                    juld.length > i ? ArgoFileReader.juldToIsoDate(juld[i]) : null,
                    perParam
            ));
        }
        return out;
    }

    private static Double safeDouble(double[] arr, int i) {
        if (arr.length <= i) return null;
        double v = arr[i];
        return Double.isNaN(v) ? null : v;
    }
}
