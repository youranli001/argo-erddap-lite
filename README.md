# argo-erddap-lite

A small HTTP service that serves Argo profiling-float NetCDF data over a REST
API patterned after [ERDDAP](https://coastwatch.pfeg.noaa.gov/erddap/index.html).

The goal is to make a single float's `*_prof.nc` and `*_meta.nc` queryable
without writing any NetCDF-aware client code: callers ask for cycle ranges and
parameter subsets via URL, the server reads from disk, returns JSON or CSV.

Built with the same Java NetCDF library (`edu.ucar:cdm-core`) that ERDDAP and
THREDDS use, so the read path mirrors the existing infrastructure stack at
data centres like PMEL.

## Endpoints

| Method | Path | Returns |
|---|---|---|
| `GET` | `/datasets` | List of known floats (WMO ID, platform type, cycle count) |
| `GET` | `/info/{floatId}` | Metadata + available profile-data variables for one float |
| `GET` | `/data/{floatId}.{format}` | Profile data with optional `cycles` and `parameters` filters |

`{format}` is `json` or `csv`. Cycle filter syntax: `cycles=1-10`, `cycles=1,3,5`,
or `cycles=all`. Parameter filter: `parameters=TEMP,PSAL,DOXY`.

### Examples

```bash
curl http://localhost:7000/datasets
curl http://localhost:7000/info/5906551
curl 'http://localhost:7000/data/5906551.json?cycles=1-3&parameters=TEMP,PSAL,PRES'
curl 'http://localhost:7000/data/5906551.csv?cycles=all&parameters=TEMP'
```

## Running

### From source

```bash
# 1. Drop one or two Argo floats into data/  (see data/README.md).
# 2. Build the fat jar.
mvn -B clean package

# 3. Run.
java -jar target/argo-erddap-lite.jar

# Override defaults via environment variables.
ARGO_DATA_DIR=/path/to/argo/files PORT=8080 java -jar target/argo-erddap-lite.jar
```

### With Docker

```bash
docker build -t argo-erddap-lite .
docker run --rm -p 7000:7000 -v "$(pwd)/data:/data:ro" argo-erddap-lite
```

### Tests

```bash
mvn test
```

## Architecture

```
src/main/java/com/youranli/argo/
├── App.java                          # Javalin entry point + global error handlers
├── controller/
│   ├── DatasetController.java        # /datasets, /info/{id}
│   └── DataController.java           # /data/{id}.{format}
├── service/
│   ├── ArgoFileReader.java           # NetCDF-Java wrapper (typed accessors)
│   ├── ConstraintParser.java         # Parses cycles=1-10,3,5 and parameters=...
│   └── DatasetRegistry.java          # Scans data dir, maps WMO → file paths
├── model/                            # POJOs / records used in JSON responses
└── format/
    ├── ResponseFormat.java
    └── CsvFormatter.java             # Long-format CSV (one row per cycle×level)
```

The read flow is straightforward: incoming URL → `ConstraintParser` → look up
the file in `DatasetRegistry` → open with `ArgoFileReader` (which wraps
`NetcdfFile` from cdm-core) → filter by cycle and parameter → serialize.

## Design notes and known limitations

The MVP focuses on the read path. Several things deliberately stayed out of
scope:

- **Single file system, no DAC layout.** The registry expects flat
  `{WMO}_prof.nc` files under one directory. Real ERDDAP installations point
  at THREDDS catalogs or remote OPeNDAP; here the catalog is just a folder.
- **No QC filtering.** Output includes all levels regardless of QC flag. A
  real serving layer would want a `qc=1,2` filter against `PROFILE_*_QC` and
  `*_QC` variables.
- **No delayed-mode awareness.** The response uses the base parameter values
  (`TEMP`, not `TEMP_ADJUSTED`). The reader filters out `*_ADJUSTED` from
  `/info` results to avoid confusion, but the framework is in place to expose
  them when a client requests them explicitly.
- **No streaming.** Whole profiles are read into memory before serialization.
  ERDDAP streams chunks via OPeNDAP — the scaling story for large queries
  would require swapping the response writer to an output stream.
- **No auth, no rate limit.** Suitable for read-only public deployment, not
  for a multi-tenant production service.

## Why Java + NetCDF-Java rather than Python

Python with `xarray` reads Argo NetCDF cleanly — that is not the question. The
question is what an ocean data centre actually deploys. ERDDAP and THREDDS are
both Java HTTP servers. Extending or interoperating with them means writing
Java against `cdm-core`, which is what this project demonstrates end-to-end.
