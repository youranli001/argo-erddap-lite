package com.youranli.argo;

import com.youranli.argo.controller.DataController;
import com.youranli.argo.controller.DatasetController;
import com.youranli.argo.service.DatasetRegistry;
import io.javalin.Javalin;
import io.javalin.http.HttpStatus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * Entry point for argo-erddap-lite.
 *
 * <p>Scans a data directory for Argo NetCDF files at startup, then exposes a
 * REST API that mimics the URL conventions of the ERDDAP data server. The
 * routes are intentionally a small subset of ERDDAP's surface area:
 *
 * <ul>
 *   <li>GET /datasets                       — list all known floats</li>
 *   <li>GET /info/{floatId}                 — metadata for one float</li>
 *   <li>GET /data/{floatId}.{format}?...    — profile data, JSON or CSV</li>
 * </ul>
 *
 * <p>Configuration is via two environment variables:
 * <ul>
 *   <li>{@code ARGO_DATA_DIR} — directory holding {wmo}_prof.nc files. Defaults to "./data".</li>
 *   <li>{@code PORT} — HTTP port. Defaults to 7000.</li>
 * </ul>
 */
public final class App {

    private static final Logger log = LoggerFactory.getLogger(App.class);

    public static void main(String[] args) {
        Path dataDir = Paths.get(envOr("ARGO_DATA_DIR", "data"));
        int port = Integer.parseInt(envOr("PORT", "7000"));

        DatasetRegistry registry = new DatasetRegistry(dataDir);
        registry.scan();
        log.info("Loaded {} dataset(s) from {}", registry.size(), dataDir.toAbsolutePath());

        DatasetController datasets = new DatasetController(registry);
        DataController data = new DataController(registry);

        Javalin app = Javalin.create(cfg -> {
            cfg.showJavalinBanner = false;
            cfg.http.defaultContentType = "application/json";
        });

        app.get("/", ctx -> ctx.json(new RootResponse()));
        app.get("/datasets", datasets::list);
        app.get("/info/{floatId}", datasets::info);
        app.get("/data/{floatId}.{format}", data::query);

        app.exception(IllegalArgumentException.class, (e, ctx) -> {
            log.warn("Bad request: {}", e.getMessage());
            ctx.status(HttpStatus.BAD_REQUEST).json(new ErrorResponse("bad_request", e.getMessage()));
        });
        app.exception(java.util.NoSuchElementException.class, (e, ctx) -> {
            ctx.status(HttpStatus.NOT_FOUND).json(new ErrorResponse("not_found", e.getMessage()));
        });
        app.exception(Exception.class, (e, ctx) -> {
            log.error("Unhandled error on {}", ctx.path(), e);
            ctx.status(HttpStatus.INTERNAL_SERVER_ERROR).json(new ErrorResponse("internal_error", e.getMessage()));
        });

        app.start(port);
        log.info("argo-erddap-lite listening on http://localhost:{}", port);
    }

    private static String envOr(String key, String fallback) {
        String v = System.getenv(key);
        return v == null || v.isBlank() ? fallback : v;
    }

    /** Tiny payload returned at the root path so users see something sensible. */
    public record RootResponse(String service, String version, String[] endpoints) {
        public RootResponse() {
            this("argo-erddap-lite", "0.1.0", new String[]{
                    "/datasets",
                    "/info/{floatId}",
                    "/data/{floatId}.{format}?cycles=...&parameters=..."
            });
        }
    }

    public record ErrorResponse(String error, String message) {}
}
