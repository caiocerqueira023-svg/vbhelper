"""Pin recent Wikimon Color V-Pet Dot animations alongside the DMC and Hatchery libraries."""
from __future__ import annotations

import argparse
from concurrent.futures import ThreadPoolExecutor
from datetime import datetime, timezone
import hashlib
import json
from pathlib import Path
import re
from urllib.parse import urlencode, urlparse

from tools.hatchery_sprites import ROSTERS, fetch, idle_frames

ROOT = Path(__file__).resolve().parents[1]
LOCK = ROOT / "tools/wikimon_dot_sources.json"


def color_entry(page):
    """Only named Color-device sprite files; VB art and cut-in images are not Dot coverage."""
    match = re.fullmatch(r"File:(.+?) vpet (dmc|penc|dmxw|dvc|d3c|dac)(?: ([0-9]+))?\.gif", page["title"], re.I)
    images = page.get("imageinfo", [])
    if not match or not images:
        return None
    image = images[0]
    parsed = urlparse(image["url"])
    if parsed.scheme != "https" or parsed.hostname != "wikimon.net" or not parsed.path.startswith("/images/"):
        raise ValueError("Unexpected Wikimon sprite URL")
    if not (1 <= image["width"] <= 256 and 1 <= image["height"] <= 256 and image["size"] <= 2 * 1024 * 1024):
        return None
    return {"name": match[1], "url": image["url"], "device": ROSTERS[match[2].lower()],
            "deviceStage": "", "attribute": "Unknown", "uploader": image["user"],
            "uploadedAt": image["timestamp"], "page": image["descriptionurl"], "wikiSha1": image["sha1"]}


def snapshot(cache_dir):
    from tools.build_dot_sprites import write_json

    cache_dir.mkdir(parents=True, exist_ok=True)
    cache = cache_dir / "wikimon"
    cache.mkdir(exist_ok=True)
    base = {"action": "query", "format": "json", "generator": "categorymembers",
            "gcmtitle": "Category:V-Pet Sprites", "gcmtype": "file", "gcmlimit": 500,
            "gcmsort": "timestamp", "gcmdir": "desc", "prop": "imageinfo",
            "iiprop": "url|size|timestamp|user|sha1"}
    continuation, seen, pages, entries = {}, set(), [], {}
    for _ in range(100):
        url = "https://wikimon.net/api.php?" + urlencode(base | continuation)
        raw = fetch(url, 4 * 1024 * 1024)
        data = json.loads(raw)
        if "error" in data:
            raise ValueError(f"Wikimon source query failed: {data['error']}")
        digest = hashlib.sha256(raw).hexdigest()
        (cache / f"{digest}.json").write_bytes(raw)
        pages.append({"url": url, "sha256": digest})
        for page in data.get("query", {}).get("pages", {}).values():
            entry = color_entry(page)
            if entry:
                entries[entry["url"]] = entry
        continuation = data.get("continue", {})
        if not continuation:
            break
        token = continuation["gcmcontinue"]
        if token in seen:
            raise ValueError("Wikimon returned a repeated pagination token")
        seen.add(token)
    else:
        raise ValueError("Wikimon sprite category exceeded the pagination bound")

    def download(entry):
        try:
            raw = fetch(entry["url"], 2 * 1024 * 1024)
            if hashlib.sha1(raw).hexdigest() != entry["wikiSha1"]:
                raise ValueError("Wikimon image changed during its source snapshot")
            frames = idle_frames(raw)
            digest = hashlib.sha256(raw).hexdigest()
            (cache / f"{digest}.gif").write_bytes(raw)
            return entry | {"sha256": digest, "bytes": len(raw), "width": frames[0].width,
                            "height": frames[0].height, "idleFrames": len(frames)}
        except Exception as error:
            return {"name": entry["name"], "url": entry["url"], "reason": str(error)}

    with ThreadPoolExecutor(max_workers=4) as pool:
        downloaded = list(pool.map(download, sorted(entries.values(), key=lambda entry: entry["url"])))
    sprites = [entry for entry in downloaded if "reason" not in entry]
    rejected = [entry for entry in downloaded if "reason" in entry]
    if not sprites:
        raise ValueError("Wikimon produced no validated Color V-Pet animations")
    write_json(LOCK, {"schemaVersion": 1, "capturedAt": datetime.now(timezone.utc).isoformat(),
                     "provider": "wikimon", "source": "https://wikimon.net/Category:V-Pet_Sprites",
                     "credits": "Bandai; Wikimon contributors. Uploader names are source credits, not artist claims.",
                     "categoryPages": pages, "sprites": sprites, "rejected": rejected})
    print(f"Pinned {len(sprites)} Wikimon Color V-Pet animations; {len(rejected)} rejected images; "
          f"latest upload {max(entry['uploadedAt'] for entry in sprites)}")


if __name__ == "__main__":
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--cache-dir", type=Path, required=True)
    arguments = parser.parse_args()
    snapshot(arguments.cache_dir)
