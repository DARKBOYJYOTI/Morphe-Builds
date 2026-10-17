# Configuration

`Morphe-Builds` builds **non-root patched APKs** only. Adding an app is as easy as this:

```toml
[Some-App]
apkmirror-dlurl = "https://www.apkmirror.com/apk/inc/app"
```

> [!WARNING]
> When a patch name itself contains a single quote, double it inside the string (e.g. 'Hide ''Get Music Premium''').

## Global options

These keys sit at the top of `config.toml` (before any `[App]` table) and apply to every app.

```toml
parallel-jobs = 1                    # cores to use for parallel patching. default: $(nproc)
remove-rv-integrations-checks = true # remove checks from the Morphe/ReVanced integrations
dpi = "nodpi anydpi 120-640dpi"      # dpi packages to be searched, in order. default: "nodpi anydpi"

patches-source = "MorpheApp/morphe-patches" # where to fetch the patches bundle from. default: "MorpheApp/morphe-patches"
cli-source = "MorpheApp/morphe-cli"         # where to fetch the CLI from. default: "MorpheApp/morphe-cli"
# keys like patches-source / cli-source can also be set per app
rv-brand = "Morphe"                         # rebrand from 'Morphe' to something different. default: "Morphe"

patches-version = "v2.160.0" # 'latest', 'dev', or a version number. default: "latest"
cli-version = "v5.0.0"       # 'latest', 'dev', or a version number. default: "latest"
```

## Per-app options

Every app lives in its own `[App]` table. **All keys are optional** except a download url.

```toml
[Some-App]
app-name = "SomeApp" # if set, the build name becomes SomeApp instead of Some-App. default: the table name ('Some-App' here)
enabled = true       # whether to build this app. default: true

# 'auto' gets the latest version supported by all the included patches
# 'latest' gets the latest stable without checking patch support. 'experimental' gets the latest beta/alpha
version = "auto"     # 'auto', 'experimental', 'latest' or a version number (e.g. '20.40.41'). default: auto

# optional args passed to the CLI, e.g. to set patch options. multiline strings are supported
patcher-args = """\
  -OdarkThemeBackgroundColor=#FF0F0F0F \
  -Oanother-option=value \
  """

# whitespace separated list of patches to exclude. default: ""
excluded-patches = """\
  'Some Patch' \
  'Some Other Patch' \
  """

included-patches = "'Some Patch'" # whitespace separated list of non-default patches to include. default: ""
exclusive-patches = false         # exclude all patches by default, only use included-patches. default: false

apkmirror-dlurl = "https://www.apkmirror.com/apk/inc/app"
# direct download url. the url must point to an apk file with the name format shown here
direct-dlurl = "https://website/com.google.android.youtube-20.40.45-all.apk"

dpi = "360-480dpi" # used to select the apk variant from apkmirror. default: nodpi
arch = "arm64-v8a" # 'arm64-v8a', 'arm-v7a', 'all', 'both'. 'both' builds both arm64-v8a and arm-v7a. default: all
```

> Download sources are tried in this order: `direct` → `archive` → `apkmirror`. The first one that resolves is used, so you can provide several as fallbacks.
