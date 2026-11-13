#!/usr/bin/env python3
"""Reproducibly bundle 28 OFL Cyrillic font families for offline labels.

Source: google/fonts at one pinned Git commit. Every downloaded font is
verified by size and Git blob SHA1, with its corresponding OFL license.
No font file or asset is copied to a user-visible chat attachment.
"""
from __future__ import annotations
import hashlib
import json
import os
from pathlib import Path
import tempfile
from urllib.parse import quote
from urllib.request import urlopen

ROOT = Path(__file__).resolve().parents[1]
CONFIG = json.loads((ROOT / "scripts/label_fonts_manifest.json").read_text("utf-8"))
DEST = ROOT / "app/build/generated/font-assets"
BASE = "https://raw.githubusercontent.com/{}/{}".format(
    CONFIG["source"], CONFIG["pinned_commit"]
)


def url(path: str) -> str:
    return BASE + "/" + quote(path, safe="/")


def fetch_verified(record: dict) -> bytes:
    path = DEST / "fonts" / record["asset"]
    if path.is_file():
        data = path.read_bytes()
        if len(data) == record["size"] and (
            hashlib.sha1(b"blob " + str(len(data)).encode() + b"\0" + data).hexdigest()
            == record["git_blob_sha"]
        ):
            return data
    with urlopen(url(record["path"]), timeout=70) as response:
        data = response.read(record["size"] + 1)
    if len(data) != record["size"]:
        raise RuntimeError(f"Unexpected font size for {record['id']}: {len(data)}")
    sha = hashlib.sha1(b"blob " + str(len(data)).encode() + b"\0" + data).hexdigest()
    if sha != record["git_blob_sha"]:
        raise RuntimeError(f"Font source changed for {record['id']}; refuse unsafe build")
    path.parent.mkdir(parents=True, exist_ok=True)
    with tempfile.NamedTemporaryFile(dir=path.parent, delete=False) as tmp:
        tmp.write(data)
        temp_name = tmp.name
    os.replace(temp_name, path)
    return data


def main() -> None:
    assets = CONFIG["fonts"]
    assert len(assets) >= 20
    assert len({a["id"] for a in assets}) == len(assets)
    total = 0
    for index, record in enumerate(assets, 1):
        data = fetch_verified(record)
        total += len(data)
        license_file = DEST / "licenses" / (record["id"] + "-OFL.txt")
        if not license_file.is_file():
            with urlopen(url(record["license"]), timeout=40) as response:
                license_text = response.read(20000).decode("utf-8")
            if "SIL OPEN FONT LICENSE" not in license_text.upper():
                raise RuntimeError(f"Missing valid OFL terms for {record['id']}")
            license_file.parent.mkdir(parents=True, exist_ok=True)
            license_file.write_text(license_text, encoding="utf-8")
        print(f"Font {index}/{len(assets)}: {record['id']} ({len(data)} bytes, verified)")
    (DEST / "fonts" / "MANIFEST.json").write_text(
        json.dumps({
            "source": CONFIG["source"],
            "commit": CONFIG["pinned_commit"],
            "font_count": len(assets),
            "families": [a["id"] for a in assets],
        }, ensure_ascii=False, indent=2), encoding="utf-8")
    print(f"Verified {len(assets)} distinct fonts, {total} bytes. Offline APK assets ready.")


if __name__ == "__main__":
    main()
