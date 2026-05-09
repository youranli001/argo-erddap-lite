package com.youranli.argo.model;

import java.util.List;

/**
 * Metadata for a single float, returned by the {@code /info/{floatId}} endpoint.
 *
 * <p>The fields are a deliberately small slice of the Argo metadata surface — enough
 * to identify the float and tell a client what variables are available, but not the
 * full meta.nc contents.
 *
 * @param floatId            WMO platform number.
 * @param dac                Data Assembly Centre code.
 * @param platformType       Platform model (APEX, NAVIS, etc).
 * @param projectName        Project / PI affiliation.
 * @param piName             Principal investigator.
 * @param dataCentre         Code of the data centre processing this float.
 * @param availableParameters Profile data variables present in prof.nc (TEMP, PSAL, ...).
 * @param nCycles            Number of profiles.
 * @param firstCycleDate     ISO-8601 date of first profile, or null.
 * @param lastCycleDate      ISO-8601 date of last profile, or null.
 */
public record FloatMetadata(
        String floatId,
        String dac,
        String platformType,
        String projectName,
        String piName,
        String dataCentre,
        List<String> availableParameters,
        int nCycles,
        String firstCycleDate,
        String lastCycleDate
) {}
