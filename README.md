<div align="center">

# Morphe-Builds

**Automated, always-fresh patched APKs for YouTube, YouTube Music, Reddit & more — powered by [Morphe](https://morphe.software), the community successor to ReVanced.**

[![Powered by Morphe](https://img.shields.io/badge/Powered%20by-Morphe-8A2BE2?style=for-the-badge)](https://morphe.software)
[![Root not required](https://img.shields.io/badge/Root-Not%20Required-success?style=for-the-badge)](#installation)
[![License: GPL-3.0](https://img.shields.io/badge/License-GPL--3.0-blue?style=for-the-badge)](LICENSE)

**→ [Download the latest release](https://github.com/DARKBOYJYOTI/Morphe-Builds/releases/latest)**

</div>

---

## What is this?

**Morphe-Builds** is a fully automated builder that patches popular Android apps with the
[Morphe](https://morphe.software) patch set and publishes ready-to-install **non-root APKs**.
No rooting, no Magisk, no manual patching — just download, install, and enjoy.

Morphe is the community successor to **ReVanced** and **RVX**, built by former ReVanced
maintainers after the original project was discontinued. This repository is a personal build
pipeline that keeps your patched apps up to date with the newest app versions and patches,
**every single day**.

## Features

- **Non-root only** — plain APKs that install like any normal app
- **Ad-free YouTube & more** — SponsorBlock, background playback, Dislike, and hundreds of patches
- **Updated daily** — GitHub Actions rebuilds automatically when new patches land
- **Size-optimised** — unused native libraries stripped, only your target architecture kept
- **Configurable** — choose apps, patches, and versions with a simple `config.toml`
- **MicroG ready** — pairs with [MicroG-RE](https://github.com/MorpheApp/MicroG-RE) for account support

## Supported apps

| App | Patch set |
| --- | --- |
| YouTube | Morphe patches |
| YouTube Music | Morphe patches |
| Reddit | Morphe patches |
| Google Photos | De-Vanced patches |
| Twitter / X | Piko (NewX) patches |

Want more? Add any app supported by the Morphe patcher — see [`CONFIG.md`](./CONFIG.md).

## Installation

1. Download the APK you want from the [latest release](https://github.com/DARKBOYJYOTI/Morphe-Builds/releases/latest).
2. For **YouTube / YouTube Music**, install [MicroG-RE](https://github.com/MorpheApp/MicroG-RE) first (required for sign-in).
3. Uninstall any existing Play Store version of the app, then install the downloaded APK.
4. Open the app and go to **Settings → Morphe** to tune everything.

> **Why uninstall the original?** Patched builds are signed with a different key, so Android treats them as a different app.

## Build it yourself

### GitHub Actions (recommended)

1. Use this repository as a template (or fork it) and rename it to whatever you like.
2. Edit [`config.toml`](./config.toml) to pick your apps and patches.
3. Run the [**Build APKs**](https://github.com/DARKBOYJYOTI/Morphe-Builds/actions/workflows/build.yml) workflow.
4. Grab your fresh APKs from [Releases](https://github.com/DARKBOYJYOTI/Morphe-Builds/releases).

The scheduled workflow rebuilds automatically whenever the Morphe patches are updated.

### On Termux (Android)

```console
bash <(curl -sSf https://raw.githubusercontent.com/DARKBOYJYOTI/Morphe-Builds/main/build-termux.sh)
```

Set `REPO_URL` inside `build-termux.sh` (or export `MORPHE_BUILDS_REPO`) to point at your own repository.

### On Linux

```console
$ git clone https://github.com/DARKBOYJYOTI/Morphe-Builds --depth 1
$ cd Morphe-Builds
$ ./build.sh
```

## Configuration

Full option reference: [`CONFIG.md`](./CONFIG.md).

```toml
[YouTube-Morphe]
app-name = "YouTube"
patches-source = "MorpheApp/morphe-patches"
cli-source = "MorpheApp/morphe-cli"
rv-brand = "Morphe"
apkmirror-dlurl = "https://www.apkmirror.com/apk/google-inc/youtube"
archive-dlurl = "https://archive.org/download/jhc-apks/apks/com.google.android.youtube"
```

## Credits

This project would not exist without the work of others:

- **[j-hc](https://github.com/j-hc)** — the original ReVanced Magisk Module builder this project is derived from.
- **[Morphe](https://morphe.software)** — the patching framework, patches, and CLI.
- **[ReVanced](https://revanced.app)** — the original patching framework that started it all.
- **[MicroG-RE](https://github.com/MorpheApp/MicroG-RE)** — MicroG fork used by the patched apps.

## License

Licensed under the **GNU General Public License v3.0** — see [`LICENSE`](./LICENSE).

This project is a derivative of [j-hc/revanced-magisk-module](https://github.com/j-hc/revanced-magisk-module)
and therefore remains under the same license. If you fork it, please keep the credits above.

## Disclaimer

This project is unofficial and not affiliated with Google, Morphe, ReVanced, or j-hc. It is provided
for personal use and educational purposes only. All trademarks belong to their respective owners.
