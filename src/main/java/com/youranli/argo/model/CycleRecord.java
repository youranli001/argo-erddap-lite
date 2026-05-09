package com.youranli.argo.model;

import java.util.Map;

/**
 * One profile cycle's data, returned as an element of the {@code /data} response array.
 *
 * <p>The {@code parameters} map keys are variable names (TEMP, PSAL, PRES, ...) and the
 * values are arrays aligned with PRES. Levels filled with the NetCDF fill value are
 * replaced with {@code null} so JSON consumers see {@code null} instead of {@code 99999.0}.
 *
 * @param cycle     CYCLE_NUMBER for this profile.
 * @param latitude  LATITUDE in degrees north.
 * @param longitude LONGITUDE in degrees east.
 * @param juld      JULD (Julian days since 1950-01-01).
 * @param date      Human-readable ISO-8601 date derived from juld.
 * @param parameters Map of parameter name → per-level values (Float, with null for fills).
 */
public record CycleRecord(
        int cycle,
        Double latitude,
        Double longitude,
        Double juld,
        String date,
        Map<String, Float[]> parameters
) {}
