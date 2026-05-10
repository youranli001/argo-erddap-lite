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
 *   <li>GET /                              — HTML landing page with clickable examples</li>
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

        app.get("/", ctx -> {
            String html = """
                    <!DOCTYPE html>
                    <html lang="en">
                    <head>
                    <meta charset="UTF-8">
                    <title>argo-erddap-lite</title>
                    <style>
                    body { font-family: ui-monospace, SFMono-Regular, Menlo, monospace;
                           max-width: 720px; margin: 2em auto; padding: 0 1em;
                           color: #222; line-height: 1.55; font-size: 14px; }
                    h1 { font-size: 1.4em; margin-bottom: 0.2em; }
                    p  { margin: 0.5em 0; }
                    ul { padding-left: 0; list-style: none; }
                    li { margin: 0.7em 0; }
                    .desc { color: #666; font-size: 0.92em; margin-left: 1.5em; }
                    a { color: #0a66c2; }
                    </style>
                    </head>
                    <body>
                    <h1>argo-erddap-lite</h1>
                    <p>Java HTTP service serving Argo NetCDF data through a REST API.</p>

                    <p>Click any example below to try it in your browser:</p>
                    <ul>
                      <li><a href="/datasets">/datasets</a>
                          <div class="desc">list of WMO float IDs loaded into the service</div></li>

                      <li><a href="/info/5906551">/info/5906551</a>
                          <div class="desc">metadata for float 5906551 (PI, project, sensors) &mdash; from meta.nc</div></li>

                      <li><a href="/data/5906551.json?cycles=1-3&amp;parameters=TEMP,PSAL,PRES">/data/5906551.json?cycles=1-3&amp;parameters=TEMP,PSAL,PRES</a>
                          <div class="desc">profile data as JSON: cycles 1-3, three parameters &mdash; from prof.nc</div></li>

                      <li><a href="/data/5906551.csv?cycles=1&amp;parameters=TEMP">/data/5906551.csv?cycles=1&amp;parameters=TEMP</a>
                          <div class="desc">same as above but CSV</div></li>
                    </ul>

                    <p style="margin-top:2em; color:#888;">
                      Source: <a href="https://github.com/youranli001/argo-erddap-lite">github.com/youranli001/argo-erddap-lite</a>
                    </p>
                    </body>
                    </html>
                    """;
            ctx.html(html);
        });

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

    public record ErrorResponse(String error, String message) {}
}