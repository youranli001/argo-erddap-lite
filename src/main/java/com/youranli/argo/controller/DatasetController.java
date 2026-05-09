package com.youranli.argo.controller;

import com.youranli.argo.model.FloatMetadata;
import com.youranli.argo.service.ArgoFileReader;
import com.youranli.argo.service.DatasetRegistry;
import io.javalin.http.Context;

import java.io.IOException;
import java.util.LinkedHashSet;
import java.util.List;

/**
 * HTTP handlers for the dataset discovery endpoints.
 *
 * <p>{@code GET /datasets} returns the full list of known floats with light summary info.
 *
 * <p>{@code GET /info/{floatId}} returns metadata for one float, including the available
 * profile-data variables. Throws {@link java.util.NoSuchElementException} if the float
 * is unknown — the global handler in {@code App} translates that to HTTP 404.
 */
public final class DatasetController {

    private final DatasetRegistry registry;

    public DatasetController(DatasetRegistry registry) {
        this.registry = registry;
    }

    public void list(Context ctx) {
        ctx.json(registry.listSummaries());
    }

    public void info(Context ctx) throws IOException {
        String floatId = ctx.pathParam("floatId");
        DatasetRegistry.Entry entry = registry.require(floatId);

        // Pull what we need from prof.nc + meta.nc, then close both before responding.
        int nCycles;
        String firstDate = null;
        String lastDate = null;
        List<String> dataVars;
        try (ArgoFileReader prof = new ArgoFileReader(entry.profPath())) {
            int[] cycles = prof.readCycleNumbers();
            nCycles = cycles.length;
            double[] juld = prof.readJuld();
            if (juld.length > 0) {
                firstDate = ArgoFileReader.juldToIsoDate(juld[0]);
                lastDate = ArgoFileReader.juldToIsoDate(juld[juld.length - 1]);
            }

            // Use the variables actually present in prof.nc as the source of truth for
            // "available parameters" — STATION_PARAMETERS is the formal answer, but the
            // physical layout is what callers can actually request.
            dataVars = prof.listProfileDataVariables();
            // De-dupe with STATION_PARAMETERS where the file declares them, in case
            // there are derived ones we filtered.
            try {
                List<String> declared = prof.readUniqueStrings("STATION_PARAMETERS");
                LinkedHashSet<String> merged = new LinkedHashSet<>(dataVars);
                merged.addAll(declared);
                dataVars = List.copyOf(merged);
            } catch (Exception ignored) {
                // STATION_PARAMETERS is optional — fall back to what listProfileDataVariables found.
            }
        }

        String platformType = "";
        String projectName = "";
        String piName = "";
        String dataCentre = "";
        if (entry.hasMeta()) {
            try (ArgoFileReader meta = new ArgoFileReader(entry.metaPath())) {
                platformType = meta.readCharVariable("PLATFORM_TYPE");
                projectName = meta.readCharVariable("PROJECT_NAME");
                piName = meta.readCharVariable("PI_NAME");
                dataCentre = meta.readCharVariable("DATA_CENTRE");
            }
        }

        FloatMetadata payload = new FloatMetadata(
                floatId,
                "",  // dac — same caveat as in DatasetRegistry.buildSummary
                platformType,
                projectName,
                piName,
                dataCentre,
                dataVars,
                nCycles,
                firstDate,
                lastDate
        );
        ctx.json(payload);
    }
}
