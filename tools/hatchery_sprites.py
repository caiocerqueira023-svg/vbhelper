"""Snapshot credited Color-device Dot animations and documented regional names.

Run explicitly to update tools/dot_sprite_supplement.json. Normal asset builds use
that checksum-pinned manifest and an external cache; the app never downloads art.
"""
from __future__ import annotations

import argparse
from concurrent.futures import ThreadPoolExecutor
from datetime import datetime, timezone
import hashlib
from html.parser import HTMLParser
import io
import json
from pathlib import Path
import re
from urllib.parse import urljoin, urlparse
from urllib.request import Request, urlopen

from PIL import Image

ROOT = Path(__file__).resolve().parents[1]
LOCK = ROOT / "tools/dot_sprite_supplement.json"
NAME_SOURCE = "https://wikimon.net/List_of_English_Dub_Names"
ROSTERS = {
    "dmc": "Digital Monster Color",
    "penc": "Digimon Pendulum Color",
    "dmxw": "Digital Monster Color Xros Wars",
    "dvc": "Digivice 25th Color Evolution",
    "d3c": "D-3 25th Color Evolution",
    "dac": "D-Ark 25th Color Evolution",
}


class RosterParser(HTMLParser):
    """Read named roster rows, excluding artwork, cut-ins, navigation and unrelated images."""
    def __init__(self):
        super().__init__()
        self.rows = []
        self.row = None
        self.cell = None
        self.text = []

    def handle_starttag(self, tag, attrs):
        attrs = dict(attrs)
        if tag == "tr":
            self.row = {}
        elif self.row is not None and tag == "td":
            self.cell = attrs.get("class", "")
            self.text = []
        elif self.row is not None and tag == "img":
            url = urljoin("https://humulos.com/", attrs.get("src", ""))
            parsed = urlparse(url)
            if parsed.hostname == "humulos.com" and re.fullmatch(
                    r"/digimon/images/dot/(?:dmc|penc|dmxw|dvc|d3c|dac)/[a-zA-Z0-9_-]+\.gif", parsed.path):
                self.row.setdefault("url", url)

    def handle_data(self, data):
        if self.cell is not None:
            self.text.append(data)

    def handle_endtag(self, tag):
        if tag == "td" and self.row is not None and self.cell is not None:
            self.row[self.cell] = "".join(self.text).strip()
            self.cell = None
        elif tag == "tr" and self.row is not None:
            if self.row.get("list_name") and self.row.get("url"):
                self.rows.append(self.row)
            self.row = None


class NameTableParser(HTMLParser):
    def __init__(self):
        super().__init__()
        self.rows = []
        self.row = None
        self.cell = None

    def handle_starttag(self, tag, attrs):
        if tag == "tr":
            self.row = []
        elif tag == "td" and self.row is not None:
            self.cell = []
        elif tag == "br" and self.cell is not None:
            self.cell.append("\n")

    def handle_data(self, data):
        if self.cell is not None:
            self.cell.append(data)

    def handle_endtag(self, tag):
        if tag == "td" and self.cell is not None:
            if self.row is not None:
                self.row.append("".join(self.cell).strip())
            self.cell = None
        elif tag == "tr" and self.row is not None:
            if len(self.row) == 3:
                self.rows.append(self.row)
            self.row = None


def regional_name_pairs(html):
    """Keep table evidence, including conflicts; the builder accepts only unambiguous known targets."""
    parser = NameTableParser()
    parser.feed(html)
    pairs = set()
    for primary, alternatives, japanese in parser.rows:
        if "→" in primary or not japanese or japanese.startswith("N/A"):
            continue
        targets = [name.strip() for name in japanese.splitlines() if name.strip()]
        aliases = [name.strip() for name in (primary + "\n" + alternatives).splitlines() if name.strip()]
        for alias in aliases:
            for target in targets:
                if alias != target and len(alias) <= 160 and len(target) <= 160:
                    pairs.add((alias, target))
    return [{"name": alias, "target": target} for alias, target in sorted(pairs)]


def idle_frames(data):
    """Only label observed idle poses. Missing attack/training poses use the existing idle fallback."""
    if len(data) > 2 * 1024 * 1024:
        raise ValueError("Idle animation exceeds its size limit")
    with Image.open(io.BytesIO(data)) as image:
        if image.format != "GIF" or not (1 <= image.width <= 256 and 1 <= image.height <= 256):
            raise ValueError("Unsupported Color-device idle animation")
        if not 1 <= image.n_frames <= 64:
            raise ValueError("Unexpected idle animation frame count")
        frames = []
        for index in range(image.n_frames):
            image.seek(index)
            frame = image.convert("RGBA")
            alpha = frame.getchannel("A")
            if alpha.getextrema()[1] == 0:
                continue
            if alpha.getextrema()[0] == 255:
                raise ValueError("Idle animation has no transparent background")
            raw = frame.tobytes()
            if any(previous.tobytes() == raw for previous in frames):
                continue
            frames.append(frame.copy())
            if len(frames) == 2:
                break
        if not frames:
            raise ValueError("Idle animation contains no visible artwork")
        return frames


def fetch(url, limit):
    with urlopen(Request(url, headers={"User-Agent": "VBHelper-offline-sprite-builder/1.0"}), timeout=60) as response:
        data = response.read(limit + 1)
    if len(data) > limit:
        raise ValueError(f"Source exceeds size limit: {url}")
    return data


def snapshot(cache_dir):
    from tools.build_dot_sprites import write_json

    cache_dir.mkdir(parents=True, exist_ok=True)
    cache = cache_dir / "hatchery"
    cache.mkdir(exist_ok=True)
    pages, entries = [], {}
    for device, title in ROSTERS.items():
        url = f"https://humulos.com/digimon/{device}/list/"
        raw = fetch(url, 2 * 1024 * 1024)
        parser = RosterParser()
        parser.feed(raw.decode("utf-8"))
        if not parser.rows:
            raise ValueError(f"No named Dot rows found: {url}")
        digest = hashlib.sha256(raw).hexdigest()
        (cache / f"{digest}.html").write_bytes(raw)
        pages.append({"url": url, "sha256": digest, "device": title, "rows": len(parser.rows)})
        for row in parser.rows:
            entries.setdefault(row["url"], {"name": row["list_name"], "url": row["url"],
                "device": title, "deviceStage": row.get("list_level", ""),
                "attribute": row.get("list_attribute", ""), "roster": url})
    names = fetch(NAME_SOURCE, 4 * 1024 * 1024)
    name_hash = hashlib.sha256(names).hexdigest()
    (cache / f"{name_hash}.html").write_bytes(names)
    rejected = []

    def download(entry):
        try:
            raw = fetch(entry["url"], 2 * 1024 * 1024)
            frames = idle_frames(raw)
            digest = hashlib.sha256(raw).hexdigest()
            (cache / f"{digest}.gif").write_bytes(raw)
            return entry | {"sha256": digest, "bytes": len(raw), "width": frames[0].width,
                            "height": frames[0].height, "idleFrames": len(frames)}
        except Exception as error:
            return {"name": entry["name"], "url": entry["url"], "reason": str(error)}

    with ThreadPoolExecutor(max_workers=4) as pool:
        downloaded = list(pool.map(download, sorted(entries.values(), key=lambda row: row["url"])))
    sprites = []
    for entry in downloaded:
        (rejected if "reason" in entry else sprites).append(entry)
    if not sprites:
        raise ValueError("The supplemental source produced no valid sprites")
    result = {"schemaVersion": 1, "capturedAt": datetime.now(timezone.utc).isoformat(),
              "provider": "digitama-hatchery", "source": "https://humulos.com/digimon/",
              "credits": "Bandai; humulos and the Digitama Hatchery community; Wikimon",
              "pages": pages, "nameSource": {"url": NAME_SOURCE, "sha256": name_hash},
              "namePairs": regional_name_pairs(names.decode("utf-8")), "sprites": sprites,
              "rejected": rejected}
    write_json(LOCK, result)
    print(f"Pinned {len(sprites)} Hatchery Dot animations from {len(pages)} Color-device rosters; "
          f"{len(result['namePairs'])} documented name pairs; {len(rejected)} rejected images")


if __name__ == "__main__":
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--cache-dir", type=Path, required=True)
    arguments = parser.parse_args()
    snapshot(arguments.cache_dir)
