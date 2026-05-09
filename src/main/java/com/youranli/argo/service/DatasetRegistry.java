package com.youranli.argo.service;

import com.youranli.argo.model.DatasetSummary;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Discovers Argo NetCDF files on disk at startup and exposes a lookup table from
 * float ID (WMO number) to the corresponding {@code prof.nc} and {@code meta.nc}
 * paths.
 *
 * <p>The scan is shallow: it expects files named {@code {WMO}_prof.nc} and optionally
 * {@code {WMO}_meta.nc} sitting directly inside {@code dataDir}. This matches the
 * layout produced by {@code argo_monitor.py}'s FTP downloader.
 *
 * <p>This class is intentionally not thread-safe for writes: {@link #scan()} is
 * meant to be called once at startup. Reads after the scan completes are
 * safe because the maps are wrapped in unmodifiable views.
 */
public final class DatasetRegistry {

    private static final Logger log = LoggerFactory.getLogger(DatasetRegistry.class);
    private static final Pattern PROF_FILE = Pattern.compile("(\\d+)_prof\\.nc$");

    private final Path dataDir;
    private Map<String, Entry> entries = Collections.emptyMap();

    public DatasetRegistry(Path dataDir) {
        this.dataDir = dataDir;
    }

    /** Scan {@code dataDir} for {WMO}_prof.nc files and build the entry map. */
    public void scan() {
        if (!Files.isDirectory(dataDir)) {
            log.warn("Data directory {} does not exist or is not a directory; registry will be empty",
                    dataDir.toAbsolutePath());
            this.entries = Collections.emptyMap();
            return;
        }

        Map<String, Entry> found = new LinkedHashMap<>();
        try (DirectoryStream<Path> stream = Files.newDirectoryStream(dataDir, "*_prof.nc")) {
            List<Path> profFiles = new ArrayList<>();
            for (Path p : stream) profFiles.add(p);
            profFiles.sort(Path::compareTo);

            for (Path profPath : profFiles) {
                Matcher m = PROF_FILE.matcher(profPath.getFileName().toString());
                if (!m.find()) continue;
                String floatId = m.group(1);
                Path metaPath = dataDir.resolve(floatId + "_meta.nc");
                if (!Files.exists(metaPath)) metaPath = null;

                found.put(floatId, new Entry(floatId, profPath, metaPath));
            }
        } catch (IOException e) {
            log.error("Failed to scan {}", dataDir, e);
        }

        this.entries = Collections.unmodifiableMap(found);
    }

    public int size() {
        return entries.size();
    }

    /** @return summary entries for every known float, sorted by WMO. */
    public List<DatasetSummary> listSummaries() {
        List<DatasetSummary> out = new ArrayList<>(entries.size());
        for (Entry e : entries.values()) {
            out.add(buildSummary(e));
        }
        return out;
    }

    /** Look up an entry by float ID, throwing 404-style on miss. */
    public Entry require(String floatId) {
        Entry e = entries.get(floatId);
        if (e == null) {
            throw new NoSuchElementException("no dataset for float '" + floatId + "'");
        }
        return e;
    }

    public Optional<Entry> find(String floatId) {
        return Optional.ofNullable(entries.get(floatId));
    }

    private DatasetSummary buildSummary(Entry e) {
        String platformType = "";
        int nCycles = 0;

        // Cheap probe: open prof.nc, count cycles. Open meta.nc for platform type if present.
        try (ArgoFileReader prof = new ArgoFileReader(e.profPath)) {
            nCycles = prof.readCycleNumbers().length;
        } catch (Exception ex) {
            log.warn("Failed to probe {}: {}", e.profPath, ex.getMessage());
        }

        if (e.metaPath != null) {
            try (ArgoFileReader meta = new ArgoFileReader(e.metaPath)) {
                platformType = meta.readCharVariable("PLATFORM_TYPE");
            } catch (Exception ex) {
                log.debug("Failed to read meta for {}: {}", e.floatId, ex.getMessage());
            }
        }

        // DAC inference would require either a directory layout that includes the DAC
        // (e.g. data/aoml/5906551/) or a lookup against a manifest. We leave it blank
        // for now — extending this is one of the natural follow-ups.
        return new DatasetSummary(e.floatId, "", platformType, nCycles);
    }

    /** A located pair of files for one float. */
    public record Entry(String floatId, Path profPath, Path metaPath) {
        public boolean hasMeta() {
            return metaPath != null;
        }
    }
}
