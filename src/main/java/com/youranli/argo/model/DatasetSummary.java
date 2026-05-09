package com.youranli.argo.model;

/**
 * Summary entry returned by the {@code /datasets} endpoint.
 *
 * @param floatId      WMO platform number (e.g. "5906551").
 * @param dac          Data Assembly Centre (e.g. "aoml").
 * @param platformType Platform model string from meta.nc, may be empty.
 * @param nCycles      Number of profiles in the prof.nc file.
 */
public record DatasetSummary(
        String floatId,
        String dac,
        String platformType,
        int nCycles
) {}
