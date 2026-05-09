package com.youranli.argo.service;

import ucar.ma2.Array;
import ucar.ma2.ArrayChar;
import ucar.ma2.IndexIterator;
import ucar.nc2.Attribute;
import ucar.nc2.NetcdfFile;
import ucar.nc2.NetcdfFiles;
import ucar.nc2.Variable;

import java.io.IOException;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Thin wrapper around NetCDF-Java for Argo profile and metadata files.
 *
 * <p>The class is {@link AutoCloseable} so callers can use it inside
 * try-with-resources. It opens an Argo file once and exposes typed accessors
 * for the variables this project cares about.
 *
 * <p>This is the same {@code edu.ucar:cdm-core} library that ERDDAP and THREDDS
 * use to read NetCDF — using it here keeps the project's read path on the same
 * code paths PMEL's existing infrastructure relies on.
 */
public final class ArgoFileReader implements AutoCloseable {

    /** Argo's reference epoch: JULD is days since this instant in UTC. */
    private static final LocalDateTime ARGO_EPOCH =
            LocalDate.of(1950, 1, 1).atStartOfDay();

    /** Sentinel used by Argo for missing float values. */
    private static final float DEFAULT_FLOAT_FILL = 99999.0f;
    /** Sentinel used by Argo for missing double values. */
    private static final double DEFAULT_DOUBLE_FILL = 99999.0;

    private final NetcdfFile nc;
    private final Path path;

    public ArgoFileReader(Path path) throws IOException {
        this.path = path;
        this.nc = NetcdfFiles.open(path.toString());
    }

    @Override
    public void close() throws IOException {
        nc.close();
    }

    public Path path() {
        return path;
    }

    // ──────────────────────────────────────────────────────────────────────────
    // Per-profile 1D coordinates
    // ──────────────────────────────────────────────────────────────────────────

    /** Read CYCLE_NUMBER as int[N_PROF]. Returns empty array if not present. */
    public int[] readCycleNumbers() throws IOException {
        Variable v = nc.findVariable("CYCLE_NUMBER");
        if (v == null) return new int[0];
        Array arr = v.read();
        return (int[]) arr.copyTo1DJavaArray();
    }

    /** Read JULD as double[N_PROF]. Fill values become NaN. */
    public double[] readJuld() throws IOException {
        Variable v = nc.findVariable("JULD");
        if (v == null) return new double[0];
        double fill = doubleFill(v);
        double[] raw = (double[]) v.read().copyTo1DJavaArray();
        for (int i = 0; i < raw.length; i++) {
            if (raw[i] == fill) raw[i] = Double.NaN;
        }
        return raw;
    }

    public double[] readLatitudes() throws IOException {
        return read1DDouble("LATITUDE");
    }

    public double[] readLongitudes() throws IOException {
        return read1DDouble("LONGITUDE");
    }

    private double[] read1DDouble(String name) throws IOException {
        Variable v = nc.findVariable(name);
        if (v == null) return new double[0];
        double fill = doubleFill(v);
        double[] raw = (double[]) v.read().copyTo1DJavaArray();
        for (int i = 0; i < raw.length; i++) {
            if (raw[i] == fill) raw[i] = Double.NaN;
        }
        return raw;
    }

    // ──────────────────────────────────────────────────────────────────────────
    // 2D profile data
    // ──────────────────────────────────────────────────────────────────────────

    /**
     * Read a profile-data variable as float[N_PROF][N_LEVELS]. Fill values become NaN.
     * @return null if the variable is absent.
     */
    public float[][] read2DFloat(String varName) throws IOException {
        Variable v = nc.findVariable(varName);
        if (v == null) return null;
        if (v.getRank() != 2) {
            throw new IllegalStateException(
                    "Expected rank-2 variable for '" + varName + "', got rank " + v.getRank());
        }

        float fill = floatFill(v);
        float[][] data = (float[][]) v.read().copyToNDJavaArray();
        for (float[] row : data) {
            for (int j = 0; j < row.length; j++) {
                if (row[j] == fill) row[j] = Float.NaN;
            }
        }
        return data;
    }

    /**
     * Returns the variable names that look like Argo profile data — i.e. rank-2 numeric
     * variables whose first dimension is N_PROF and second dimension is N_LEVELS.
     *
     * <p>This excludes QC variables (which are char) and bookkeeping variables.
     */
    public List<String> listProfileDataVariables() {
        List<String> out = new ArrayList<>();
        for (Variable v : nc.getVariables()) {
            if (v.getRank() != 2) continue;
            if (!isFloatingPoint(v)) continue;
            String d0 = v.getDimension(0).getShortName();
            String d1 = v.getDimension(1).getShortName();
            if ("N_PROF".equals(d0) && "N_LEVELS".equals(d1)) {
                String name = v.getShortName();
                // Skip ADJUSTED, ADJUSTED_ERROR variants from the headline list — those
                // are derived from the base parameters and would clutter the response.
                if (name.endsWith("_ADJUSTED") || name.endsWith("_ADJUSTED_ERROR")) continue;
                out.add(name);
            }
        }
        return out;
    }

    // ──────────────────────────────────────────────────────────────────────────
    // String / char metadata
    // ──────────────────────────────────────────────────────────────────────────

    /**
     * Read a single platform number from prof.nc — Argo guarantees all rows in PLATFORM_NUMBER
     * within one prof.nc file refer to the same float, so we read the first row.
     */
    public String readPlatformNumber() throws IOException {
        Variable v = nc.findVariable("PLATFORM_NUMBER");
        if (v == null) return "";
        ArrayChar arr = (ArrayChar) v.read();
        if (arr.getRank() == 0) return "";
        if (arr.getRank() == 1) return arr.getString().trim();
        return arr.getString(0).trim();
    }

    /**
     * Read a global or per-profile char attribute commonly stored in Argo metadata files.
     * For char[N_PROF, STRING_LEN] the first row is returned; for char[STRING_LEN] the
     * whole string is returned.
     */
    public String readCharVariable(String name) throws IOException {
        Variable v = nc.findVariable(name);
        if (v == null) return "";
        Array arr = v.read();
        if (!(arr instanceof ArrayChar ac)) return "";
        if (ac.getRank() == 0) return "";
        if (ac.getRank() == 1) return ac.getString().trim();
        return ac.getString(0).trim();
    }

    /**
     * For a variable like {@code STATION_PARAMETERS} (char(N_PROF, N_PARAM, STRING16)) or
     * {@code PARAMETER} (char(N_PROF, N_CALIB, N_PARAM, STRING16)), read every embedded
     * string and return the unique non-empty parameter names.
     */
    public List<String> readUniqueStrings(String name) throws IOException {
        Variable v = nc.findVariable(name);
        if (v == null) return List.of();
        Array arr = v.read();
        if (!(arr instanceof ArrayChar ac)) return List.of();

        Set<String> seen = new LinkedHashSet<>();
        int[] shape = ac.getShape();
        if (shape.length < 2) return List.of();

        int strLen = shape[shape.length - 1];
        IndexIterator it = ac.getIndexIterator();
        StringBuilder buf = new StringBuilder(strLen);
        int charsRead = 0;
        while (it.hasNext()) {
            char c = it.getCharNext();
            buf.append(c);
            charsRead++;
            if (charsRead == strLen) {
                String s = buf.toString().trim();
                if (!s.isEmpty()) seen.add(s);
                buf.setLength(0);
                charsRead = 0;
            }
        }
        return new ArrayList<>(seen);
    }

    // ──────────────────────────────────────────────────────────────────────────
    // Date helpers
    // ──────────────────────────────────────────────────────────────────────────

    /** Convert a JULD value (days since 1950-01-01) to an ISO-8601 string, or null if NaN. */
    public static String juldToIsoDate(double juld) {
        if (Double.isNaN(juld)) return null;
        long seconds = (long) (juld * 86400.0);
        LocalDateTime dt = ARGO_EPOCH.plusSeconds(seconds);
        return dt.atOffset(ZoneOffset.UTC).format(DateTimeFormatter.ISO_OFFSET_DATE_TIME);
    }

    // ──────────────────────────────────────────────────────────────────────────
    // Internal helpers
    // ──────────────────────────────────────────────────────────────────────────

    private static float floatFill(Variable v) {
        Attribute a = v.findAttribute("_FillValue");
        if (a == null) return DEFAULT_FLOAT_FILL;
        Number n = a.getNumericValue();
        return n == null ? DEFAULT_FLOAT_FILL : n.floatValue();
    }

    private static double doubleFill(Variable v) {
        Attribute a = v.findAttribute("_FillValue");
        if (a == null) return DEFAULT_DOUBLE_FILL;
        Number n = a.getNumericValue();
        return n == null ? DEFAULT_DOUBLE_FILL : n.doubleValue();
    }

    private static boolean isFloatingPoint(Variable v) {
        return switch (v.getDataType()) {
            case FLOAT, DOUBLE -> true;
            default -> false;
        };
    }
}
