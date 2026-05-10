# argo-erddap-lite

A small Java HTTP service that serves Argo float NetCDF data through a REST API. Drop float `prof.nc` and `meta.nc` files in a folder, start the service, and view float metadata and profile measurements from a browser by URL.

URL patterns follow [ERDDAP](https://coastwatch.pfeg.noaa.gov/erddap/index.html). ERDDAP and THREDDS are Java services running on `edu.ucar:cdm-core`. Building this in Java with the same library exercises the same read paths those servers use.

## Endpoints

| Path | Returns |
|---|---|
| `GET /datasets` | List of loaded floats |
| `GET /info/{floatId}` | Metadata for one float |
| `GET /data/{floatId}.{format}` | Profile data as JSON or CSV |

Filter syntax for `/data`:
- `cycles=1-10`, `cycles=1,3,5`, or `cycles=all`
- `parameters=TEMP,PSAL,DOXY`

## Running locally

### Check Java and Maven are installed

In a terminal:

```bash
java -version
mvn -version
```

Java should be 17 or newer. 
### Build and run

```bash
# Put 1-2 Argo floats in data/ (see data/README.md)
mvn clean package
java -jar target/argo-erddap-lite.jar
```

Defaults: data dir is `./data`, port is 7000. Override with `ARGO_DATA_DIR` and `PORT`.

## Tests

```bash
mvn test
```

## Examples

With the service running, open these URLs in a browser.

**List loaded floats**

```
http://localhost:7000/datasets
```

Returns a JSON list of WMO IDs the service has loaded, e.g. `["5906551", "5906552"]`.

**View float metadata** (from `meta.nc`)

```
http://localhost:7000/info/5906551
```

Returns float metadata as JSON: principal investigator, project name, deployment date, sensors, parameter list. The same information you'd otherwise pull out of `meta.nc` with xarray.

**View profile data** (from `prof.nc`)

```
http://localhost:7000/data/5906551.json?cycles=1-3&parameters=TEMP,PSAL,PRES
```

Returns the requested cycles as JSON: pressure, temperature, and salinity at each depth level. Same content as slicing `prof.nc` in Python, served straight from the URL.

For CSV instead of JSON:

```
http://localhost:7000/data/5906551.csv?cycles=1&parameters=TEMP
```

## Project layout

```
src/main/java/com/youranli/argo/
├── App.java                       # Javalin entry, route registration
├── controller/                    # /datasets, /info, /data handlers
├── service/                       # NetCDF reader, query parser, file registry
├── model/                         # Records used as JSON shapes
└── format/                        # Output format enum + CSV writer
```
