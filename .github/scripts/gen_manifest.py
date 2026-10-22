#!/usr/bin/env python3
"""Generate a machine-readable release manifest for the updater app.

Usage: gen_manifest.py <build-dir> <output-file> <release-tag> [repo]

The script reads every APK in the build directory with aapt2 and records the
package name, versions, SDK bounds, ABIs, size, SHA-256 and signing certificate.
The updater app uses this file to grey out builds that cannot run on the device.
"""

from __future__ import annotations

import hashlib
import json
import os
import re
import subprocess
import sys
import zipfile
from datetime import datetime, timezone
from pathlib import Path


def find_build_tool(name: str) -> str:
    sdk = os.environ.get("ANDROID_HOME") or os.environ.get("ANDROID_SDK_ROOT")
    if sdk:
        build_tools = Path(sdk) / "build-tools"
        if build_tools.is_dir():
            for candidate in sorted(build_tools.iterdir(), reverse=True):
                tool = candidate / name
                if tool.is_file():
                    return str(tool)
    return name


def sha256(path: Path) -> str:
    digest = hashlib.sha256()
    with path.open("rb") as handle:
        for chunk in iter(lambda: handle.read(1024 * 1024), b""):
            digest.update(chunk)
    return digest.hexdigest()


def badging(aapt2: str, apk: Path) -> str:
    result = subprocess.run(
        [aapt2, "dump", "badging", str(apk)],
        check=False,
        capture_output=True,
        text=True,
    )
    if result.returncode != 0:
        raise RuntimeError(result.stderr.strip() or "aapt2 failed")
    return result.stdout


def extract_icon(apk: Path, build_dir: Path, package: str, text: str) -> str | None:
    candidates = re.findall(r"application-icon-(\d+):'([^']+)'", text)
    images = [
        (int(density), path)
        for density, path in candidates
        if path.lower().endswith((".png", ".webp", ".jpg", ".jpeg"))
    ]
    if not images:
        single = re.search(r"application-icon:'([^']+)'", text)
        if single and single.group(1).lower().endswith((".png", ".webp", ".jpg", ".jpeg")):
            images = [(0, single.group(1))]
    if not images:
        return None

    _, resource = max(images, key=lambda item: item[0])
    extension = os.path.splitext(resource)[1].lower() or ".png"
    icon_name = f"{package}-icon{extension}"
    try:
        with zipfile.ZipFile(apk) as archive:
            data = archive.read(resource)
        (build_dir / icon_name).write_bytes(data)
        return icon_name
    except Exception as error:  # noqa: BLE001 - icons are optional
        print(f"warning: could not extract icon for {package}: {error}", file=sys.stderr)
        return None


def signer_digest(apksigner: str, apk: Path) -> str | None:
    result = subprocess.run(
        [apksigner, "verify", "--print-certs", str(apk)],
        check=False,
        capture_output=True,
        text=True,
    )
    output = result.stdout + "\n" + result.stderr
    match = re.search(r"SHA-256 digest:\s*([0-9a-fA-F]+)", output)
    return match.group(1).lower() if match else None


def parse_badging(text: str) -> dict:
    info: dict = {}
    package = re.search(
        r"package: name='([^']+)' versionCode='([^']*)' versionName='([^']*)'",
        text,
    )
    if not package:
        raise RuntimeError("package information not found")
    info["package"] = package.group(1)
    info["versionCode"] = int(package.group(2) or 0)
    info["versionName"] = package.group(3)

    sdk = re.search(r"(?:sdkVersion|minSdkVersion):'(\d+)'", text)
    target = re.search(r"targetSdkVersion:'(\d+)'", text)
    info["minSdk"] = int(sdk.group(1)) if sdk else 0
    info["targetSdk"] = int(target.group(1)) if target else 0

    label = re.search(r"application-label:'([^']*)'", text)
    info["label"] = label.group(1) if label else info["package"]

    native = re.search(r"native-code:(.*)", text)
    abis: list[str] = []
    if native:
        abis = re.findall(r"'([^']+)'", native.group(1))
    info["abis"] = sorted(set(abis))
    return info


def main() -> int:
    if len(sys.argv) < 4:
        print(__doc__)
        return 2

    build_dir = Path(sys.argv[1])
    output = Path(sys.argv[2])
    tag = sys.argv[3]
    repo = sys.argv[4] if len(sys.argv) > 4 else os.environ.get("GITHUB_REPOSITORY", "")

    aapt2 = find_build_tool("aapt2")
    apksigner = find_build_tool("apksigner")

    assets = []
    for apk in sorted(build_dir.glob("*.apk")):
        try:
            text = badging(aapt2, apk)
            info = parse_badging(text)
        except Exception as error:  # noqa: BLE001 - keep the release build alive
            print(f"warning: skipping {apk.name}: {error}", file=sys.stderr)
            continue

        icon_name = extract_icon(apk, build_dir, info["package"], text)

        assets.append(
            {
                "file": apk.name,
                "icon": icon_name,
                "package": info["package"],
                "label": info["label"],
                "versionName": info["versionName"],
                "versionCode": info["versionCode"],
                "minSdk": info["minSdk"],
                "targetSdk": info["targetSdk"],
                "abis": info["abis"],
                "size": apk.stat().st_size,
                "sha256": sha256(apk),
                "signerSha256": signer_digest(apksigner, apk),
            }
        )

    manifest = {
        "schema": 1,
        "repo": repo,
        "release": tag,
        "generated_at": datetime.now(timezone.utc).isoformat(),
        "assets": assets,
    }

    output.parent.mkdir(parents=True, exist_ok=True)
    output.write_text(json.dumps(manifest, indent=2) + "\n", encoding="utf-8")
    print(f"wrote {output} with {len(assets)} asset(s)")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
