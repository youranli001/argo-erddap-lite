# Sample Argo data

Drop float NetCDF files in this directory using the naming convention used by the
Argo Global Data Assembly Centres:

```
data/
  5906551_prof.nc
  5906551_meta.nc
  7900833_prof.nc
  7900833_meta.nc
```

Only `*_prof.nc` is required for `/datasets`, `/info/{floatId}`, and `/data/{floatId}.{format}`
to work. `*_meta.nc` adds platform type, project name, PI, and data centre to the
`/info` payload.

## Quick download via Python

If you already have `argo_monitor.py` set up, you can reuse its FTP downloader to
pull a couple of floats into this directory:

```python
from argo_monitor import find_dac_ftp, download_float_files

class _Bar:
    def progress(self, *_): pass

for wmo in ("5906551", "7900833"):
    dac = find_dac_ftp(wmo)
    if dac:
        download_float_files(wmo, dac, "data", _Bar())
```

## Or via curl

```bash
WMO=5906551
DAC=aoml
BASE="https://data-argo.ifremer.fr/dac/$DAC/$WMO"
curl -o "data/${WMO}_prof.nc" "$BASE/${WMO}_prof.nc"
curl -o "data/${WMO}_meta.nc" "$BASE/${WMO}_meta.nc"
```

The files are excluded from git via `.gitignore` — they are large binaries and
should never be committed.
