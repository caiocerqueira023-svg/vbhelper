"""Build the offline Dot library from pinned source revisions.

Combines a pinned DMC archive and checksum-pinned Hatchery Color-device animations.
Builds use an external source cache. Requires Pillow.
Generated pixels use the app's little-endian RGB565 + green transparency format.
Run with --check for a fast, completely offline verification of shipped artifacts.
"""
from __future__ import annotations

import argparse
from collections import Counter, defaultdict
import hashlib
import io
import json
from pathlib import Path, PurePosixPath
import re
import struct
import unicodedata
from urllib.parse import unquote, urlparse
from urllib.request import Request, urlopen
import zipfile

from PIL import Image
if __package__:
    from .hatchery_sprites import LOCK as SUPPLEMENT_LOCK, idle_frames
    from .wikimon_sprites import LOCK as WIKIMON_LOCK
else:
    from hatchery_sprites import LOCK as SUPPLEMENT_LOCK, idle_frames
    # The second source lock has no dependency on executing the update command.
    WIKIMON_LOCK = Path(__file__).resolve().parent / "wikimon_dot_sources.json"

ROOT = Path(__file__).resolve().parents[1]
ASSETS = ROOT / "app/src/main/assets"
SPRITE_REVISION = "5d1a43a97762765a6be13852cfc21be647ff8287"
SPECIES_REVISION = "eeaaf16685cf7515453ac307606e7e272cddfa18"
STAGES = {"baby i": 0, "baby ii": 1, "child": 2, "rookie": 2,
          "adult": 3, "champion": 3, "perfect": 4, "ultimate": 5,
          "mega": 5, "super ultimate": 6, "ultra": 6}
COLORS = ("black", "white", "red", "blue", "green", "yellow", "gold", "silver", "orange", "violet", "purple", "clear")


def compact(value: str) -> str:
    return "".join(c for c in unicodedata.normalize("NFKD", value).lower()
                   if c.isalnum() and not unicodedata.combining(c))


def canonical_key(name: str, aliases: dict[str, str]) -> str:
    key = compact(name)
    visited = set()
    while key in aliases:
        if key in visited:
            raise ValueError(f"Cyclic species alias: {name}")
        visited.add(key)
        target = compact(aliases[key])
        if target == key:
            break
        key = target
    return key


def artwork_key(name, aliases):
    """Reviewed artwork equivalences never rewrite the persisted identity registry."""
    key = canonical_key(name, aliases)
    for suffix in ("xantibody", "x"):
        if key.endswith(suffix):
            return artwork_key(key[:-len(suffix)], aliases) + "xantibody"
    for color in COLORS:
        if key.endswith(color) and key[:-len(color)].endswith("mon"):
            return color + artwork_key(key[:-len(color)], aliases)
        if key.startswith(color) and len(key) > len(color) + 3:
            return color + artwork_key(key[len(color):], aliases)
    return key


def documented_aliases(groups, fixed, pairs):
    """Accept documented equivalents only for one known identity, never by edit distance."""
    owners = defaultdict(set)
    for group, entries in groups.items():
        for name in [group] + [entry["name"] for entry in entries]:
            owners[compact(name)].add(group)
    proposals = defaultdict(set)
    spellings = defaultdict(set)
    for pair in pairs:
        name, target = compact(pair["name"]), compact(pair["target"])
        targets = owners.get(target, set())
        if name in fixed or len(targets) != 1:
            continue
        owner = next(iter(targets))
        if name in owners and owners[name] != {owner}:
            continue
        proposals[name].add(owner)
        spellings[name].add(pair["name"])
    return {spelling: next(iter(targets)) for name, targets in proposals.items() if len(targets) == 1
            for spelling in spellings[name]}


def match_sets(name, level, candidates, aliases):
    key = artwork_key(name, aliases)
    stage = STAGES.get((level or "").lower())
    return [s for s in candidates if s.get("style") == "Dot"
            and s.get("type") == "character_sprite"
            and artwork_key(s.get("matchName", s["name"]), aliases) == key
            # Armor/Hybrid/Unknown are parallel classifications, not a different
            # form of an otherwise exact, reviewed species name.
            and (stage is None or str(s.get("stage")) in {str(stage), "7", "8", "9"}
                 or stage == 5 and str(s.get("stage")) == "6")]


def preferred_set(sets):
    if not sets:
        return None
    return min(sets, key=lambda s: (not any(role in s.get("frames", {}) for role in ("atk_1", "atk_2")),
                                  s["author"].casefold() != "bandai",
                                  "train_1" not in s.get("frames", {}),
                                  s["author"].casefold(), s["source"].casefold(), s["id"]))


def stable_sprite_id(set_id):
    # Imported Sprite rows have positive autoincrement IDs. Negative IDs reserve
    # a content-addressed namespace; runtime installation never replaces a row.
    value = int(hashlib.sha256(set_id.encode()).hexdigest()[:8], 16) & 0x7fffffff
    return -(value + 2) if value < 0x7ffffffe else -value


def source_path(url):
    parsed = urlparse(url)
    prefix = "/dmc-sprites/"
    if parsed.scheme != "https" or parsed.hostname != "tero0x.github.io" or not parsed.path.startswith(prefix):
        raise ValueError(f"Unexpected artwork URL: {url}")
    path = unquote(parsed.path[len(prefix):])
    if not path.startswith("images/") or any(p in ("..", ".") for p in path.split("/")) or "\\" in path:
        raise ValueError(f"Unsafe artwork path: {path}")
    return path


def rgb565_frame(png, width, height):
    if not (0 < width <= 256 and 0 < height <= 256) or len(png) > 2 * 1024 * 1024:
        raise ValueError("Artwork dimensions or compressed size exceed the supported bounds")
    try:
        with Image.open(io.BytesIO(png)) as image:
            if image.format != "PNG" or image.size != (width, height):
                raise ValueError(f"Expected {width}x{height} PNG, got {image.size}")
            pixels = list(image.convert("RGBA").getdata())
    except Exception as error:
        raise ValueError(f"Invalid PNG: {error}") from error
    output = bytearray()
    for r, g, b, a in pixels:
        pixel = 0x07e0 if a < 128 else ((r >> 3) << 11) | ((g >> 2) << 5) | (b >> 3)
        if a >= 128 and pixel == 0x07e0:
            pixel = 0x07e1  # Keep visible green visible in the native chroma-key decoder.
        output.extend(struct.pack("<H", pixel))
    return bytes(output)


def read_json(path):
    return json.loads(path.read_text(encoding="utf-8-sig"))


def write_json(path, data):
    path.write_text(json.dumps(data, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")


def cached_download(url, path, offline, limit):
    if path.exists():
        return path
    if offline:
        raise ValueError(f"Missing offline source cache: {path}")
    print(f"Fetching {url}", flush=True)
    temporary = path.with_suffix(path.suffix + ".part")
    try:
        with urlopen(Request(url, headers={"User-Agent": "VBHelper-asset-builder"}), timeout=120) as response, temporary.open("wb") as out:
            count = 0
            while chunk := response.read(1024 * 1024):
                count += len(chunk)
                if count > limit:
                    raise ValueError("Source download exceeds its size limit")
                out.write(chunk)
        temporary.replace(path)
    finally:
        temporary.unlink(missing_ok=True)
    return path


def build(cache_dir, offline):
    cache_dir.mkdir(parents=True, exist_ok=True)
    sprite_url = f"https://raw.githubusercontent.com/tero0x/dmc-sprites/{SPRITE_REVISION}/sprites.json"
    species_url = f"https://raw.githubusercontent.com/TamerCaio/vbhelper-species-db/{SPECIES_REVISION}/species.json"
    sprite_input = cached_download(sprite_url, cache_dir / f"sprites-{SPRITE_REVISION}.json", offline, 16 * 1024 * 1024)
    species_input = cached_download(species_url, cache_dir / f"species-{SPECIES_REVISION}.json", offline, 16 * 1024 * 1024)
    archive = cached_download(f"https://codeload.github.com/tero0x/dmc-sprites/zip/{SPRITE_REVISION}",
                              cache_dir / f"sprites-{SPRITE_REVISION}.zip", offline, 512 * 1024 * 1024)
    upstream = read_json(sprite_input)
    source_species = read_json(species_input)
    overrides = read_json(ROOT / "tools/dot_sprite_overrides.json")
    supplement = read_json(SUPPLEMENT_LOCK)
    wikimon = read_json(WIKIMON_LOCK)
    supplemental_sources = [supplement, wikimon]
    aliases = {compact(k): compact(v) for k, v in overrides["aliases"].items()}
    artwork_alias_names = overrides["aliases"] | overrides.get("artworkAliases", {})
    kinds = {canonical_key(k, aliases): v for k, v in overrides["kinds"].items()}
    identity_file = ROOT / "tools/species_identity_registry.json"
    identities = read_json(identity_file) if identity_file.exists() else {}
    groups = defaultdict(list)
    bindings = defaultdict(list)
    for card, slots in source_species["species"].items():
        for slot, entry in slots.items():
            key = canonical_key(entry["name"], aliases)
            groups[key].append(entry)
            if card != "0":  # Group zero is the reference catalog, not a physical card.
                bindings[key].append(f"{card}:{slot}")
    learned = documented_aliases(groups, {compact(k): v for k, v in artwork_alias_names.items()}, supplement["namePairs"])
    artwork_alias_names = learned | artwork_alias_names
    artwork_aliases = {compact(k): compact(v) for k, v in artwork_alias_names.items()}
    dot = [s for s in upstream["sprites"] if s.get("style") == "Dot" and s.get("type") == "character_sprite"]
    # These two English names are ambiguous across species. Source stage evidence
    # disambiguates the artwork; it never changes the saved catalog identity.
    staged_names = {"kumamon": {"2": "Bearmon", "8": "Chackmon"},
                    "mercurymon": {"5": "Mercurymon", "8": "Mercuremon"}}
    dot = [s | {"matchName": staged_names.get(compact(s["name"]), {}).get(str(s.get("stage")), s["name"])} for s in dot]
    attribute_ids = {"Free": "0", "Virus": "1", "Data": "2", "Vaccine": "3", "Variable": "4", "Unknown": "6"}
    for supplemental_source in supplemental_sources:
        provider = supplemental_source["provider"]
        (cache_dir / ("hatchery" if provider == "digitama-hatchery" else "wikimon")).mkdir(exist_ok=True)
        for entry in supplemental_source["sprites"]:
            title = "Digitama Hatchery" if provider == "digitama-hatchery" else "Wikimon"
            dot.append({"name": entry["name"], "style": "Dot", "type": "character_sprite",
                        "provider": provider, "stage": "9", "attribute": attribute_ids.get(entry["attribute"], "6"),
                        "author": "Bandai" if provider == "digitama-hatchery" else f"Wikimon · {entry['uploader']} (uploader)",
                        "source": f"{title} · {entry['device']} · idle animation",
                        "size": f"{entry['width']}x{entry['height']}", "format": 100, "supplement": entry})
    candidates = defaultdict(list)
    for s in dot:
        candidates[artwork_key(s.get("matchName", s["name"]), artwork_aliases)].append(s)
    # Keep every published species ID, including legacy duplicate spellings.
    # Only one identity owns a new alias; a known name always keeps its owner.
    known_names = {compact(e["name"]): key for key, entries in groups.items() for e in entries}
    artwork_groups = defaultdict(list)
    for key, entries in groups.items():
        artwork_groups[artwork_key(entries[0]["name"], artwork_aliases)].append(key)
    alias_owners = {key: min(members, key=lambda member: (member != key, member))
                    for key, members in artwork_groups.items()}
    exported_aliases = defaultdict(set)
    for name in artwork_alias_names:
        exported_aliases[artwork_key(name, artwork_aliases)].add(name)
    for s in dot:
        if s.get("matchName", s["name"]) == s["name"]:
            exported_aliases[artwork_key(s["name"], artwork_aliases)].add(s["name"])
    frames = {}
    converted = {}
    sets = {}
    set_ids = {}
    rejected = []
    records = []
    archive_prefix = f"dmc-sprites-{SPRITE_REVISION}/"
    with zipfile.ZipFile(archive) as source:
        for key in sorted(groups):
            entries = groups[key]
            preferred = next((e for e in entries if compact(e["name"]) == key), entries[0])
            kind = kinds.get(key, "DIGIMON")
            identities.setdefault(key, ("digimon." if kind == "DIGIMON" else "reference.") + key)
            levels = Counter(e.get("level") for e in entries if e.get("level"))
            level = levels.most_common(1)[0][0] if levels else None
            artwork = artwork_key(preferred["name"], artwork_aliases)
            matching = match_sets(preferred["name"], level, candidates[artwork], artwork_aliases) if kind == "DIGIMON" else []
            linked = []
            for candidate in matching:
                try:
                    width, height = map(int, candidate["size"].split("x"))
                    supplemental = candidate.get("supplement")
                    if supplemental:
                        folder = "hatchery" if candidate["provider"] == "digitama-hatchery" else "wikimon"
                        gif = cached_download(supplemental["url"], cache_dir / folder / f"{supplemental['sha256']}.gif",
                                              offline, 2 * 1024 * 1024).read_bytes()
                        if len(gif) != supplemental["bytes"] or hashlib.sha256(gif).hexdigest() != supplemental["sha256"]:
                            raise ValueError("Supplemental artwork checksum mismatch")
                        poses = idle_frames(gif)
                        if len(poses) != supplemental["idleFrames"] or any(pose.size != (width, height) for pose in poses):
                            raise ValueError("Supplemental idle animation differs from its snapshot")
                        roles = [f"idle_{index + 1}" for index in range(len(poses))]
                        paths = [supplemental["url"] + f"#frame={index}" for index in range(len(poses))]
                        images = paths
                        for path, pose in zip(paths, poses):
                            png = io.BytesIO()
                            pose.save(png, format="PNG")
                            converted[path] = (width, height, rgb565_frame(png.getvalue(), width, height), supplemental["sha256"])
                    else:
                        roles = upstream["formats"][candidate["format"]]
                        if len(roles) != len(candidate["images"]) or "idle_1" not in roles:
                            raise ValueError("Declared format and image count disagree")
                        paths = list(map(source_path, candidate["images"]))
                        images = candidate["images"]
                    role_frames, hashes = {}, {}
                    pending_frames = {}
                    for role, path in zip(roles, paths):
                        if path not in converted:
                            png = source.read(archive_prefix + path)
                            raw = rgb565_frame(png, width, height)
                            converted[path] = (width, height, raw, hashlib.sha256(png).hexdigest())
                        cached_width, cached_height, raw, png_hash = converted[path]
                        if (cached_width, cached_height) != (width, height):
                            raise ValueError("One image has conflicting declared dimensions")
                        frame_path = f"frames/{hashlib.sha256(raw).hexdigest()}.bin"
                        role_frames[role] = frame_path
                        hashes[role] = png_hash
                        pending_frames[frame_path] = raw
                    # Content + source paths, never array position, establish artwork identity.
                    fingerprint = json.dumps([candidate["author"], candidate.get("source", ""), paths, hashes], sort_keys=True)
                    set_id = "dot-" + hashlib.sha256(fingerprint.encode()).hexdigest()[:24]
                    sprite_id = stable_sprite_id(set_id)
                    if sprite_id in set_ids and set_ids[sprite_id] != set_id:
                        raise ValueError("Reserved Sprite ID collision; add a reviewed ID override")
                    set_ids[sprite_id] = set_id
                    frames.update(pending_frames)
                    record = {"id": set_id, "spriteId": sprite_id, "name": candidate["name"],
                              "author": candidate["author"], "source": candidate.get("source", ""),
                              "donationUrl": candidate.get("donation_link") or None,
                              "format": candidate["format"], "width": width, "height": height,
                               "stage": str(candidate.get("stage", "")), "attribute": str(candidate.get("attribute", "")),
                               "provider": candidate.get("provider", "dmc-sprites"),
                               "frames": role_frames, "sourceImages": dict(zip(roles, images)),
                               "sourceHashes": hashes}
                    sets[set_id] = record
                    linked.append(record)
                except (ValueError, KeyError, zipfile.BadZipFile) as error:
                    rejected.append({"species": preferred["name"], "spriteName": candidate["name"],
                                     "author": candidate.get("author", ""), "reason": str(error)})
            linked = list({s["id"]: s for s in linked}.values())
            default = preferred_set(linked)
            names = {e["name"] for e in entries}
            if alias_owners[artwork] == key:
                names.update(name for name in exported_aliases[artwork]
                             if known_names.get(compact(name), key) == key)
            names = sorted(names)
            records.append({"id": identities[key], "name": preferred["name"], "aliases": names,
                            "kind": kind, "level": level, "type": preferred.get("type"),
                            "profile": preferred.get("profile"), "specialMoves": preferred.get("specialMoves") or [],
                            "cardSlots": sorted(bindings[key]),
                            "dotSprites": {"defaultSetId": default["id"] if default else None,
                                           "setIds": sorted(s["id"] for s in linked)}})
    if len(set(identities.values())) != len(identities):
        raise ValueError("Canonical species IDs are not unique")
    pack = ASSETS / "dot_sprites.zip"
    with zipfile.ZipFile(pack, "w", zipfile.ZIP_DEFLATED, compresslevel=9) as output:
        for path, raw in sorted(frames.items()):
            info = zipfile.ZipInfo(path, (1980, 1, 1, 0, 0, 0))
            info.compress_type = zipfile.ZIP_DEFLATED
            output.writestr(info, raw, compresslevel=9)
    ready = [s for s in records if s["kind"] == "DIGIMON" and s["dotSprites"]["setIds"]]
    manifest = {"schemaVersion": 1, "speciesRevision": SPECIES_REVISION, "spriteRevision": SPRITE_REVISION,
                "speciesSource": species_url, "spriteSource": sprite_url,
                "spriteSources": [{"id": "dmc-sprites", "url": sprite_url, "revision": SPRITE_REVISION,
                                   "credits": "Bandai and the credited DMC / Pendulum Color community artists"}] +
                                  [{"id": source["provider"], "url": source["source"],
                                    "revision": "sha256:" + hashlib.sha256(lock.read_bytes()).hexdigest(),
                                    "capturedAt": source["capturedAt"], "credits": source["credits"]}
                                   for source, lock in zip(supplemental_sources, [SUPPLEMENT_LOCK, WIKIMON_LOCK])],
                "packSha256": hashlib.sha256(pack.read_bytes()).hexdigest(), "packBytes": pack.stat().st_size,
                "species": records, "spriteSets": sorted(sets.values(), key=lambda s: s["id"])}
    write_json(ASSETS / "species_catalog.json", manifest)
    write_json(identity_file, dict(sorted(identities.items())))
    report = {"speciesRevision": SPECIES_REVISION, "spriteRevision": SPRITE_REVISION,
              "sources": manifest["spriteSources"], "documentedAliases": len(learned),
              "setsByProvider": dict(Counter(s["provider"] for s in sets.values())),
              "catalogSpecies": len(records), "readyDigimon": len(ready), "bundledSets": len(sets),
              "uniqueFrames": len(frames), "packBytes": pack.stat().st_size,
              "missingDot": [{"id": s["id"], "name": s["name"], "level": s["level"]}
                             for s in records if s["kind"] == "DIGIMON" and not s["dotSprites"]["setIds"]],
              "excludedReferences": [{"name": s["name"], "kind": s["kind"]} for s in records if s["kind"] != "DIGIMON"],
              "rejectedSets": rejected, "supplementRejected": supplement["rejected"] + wikimon["rejected"]}
    write_json(ROOT / "tools/dot_sprite_coverage.json", report)
    print(f"Bundled {len(sets)} sets for {len(ready)}/{len(records)} catalog identities; {len(frames)} unique frames, {pack.stat().st_size:,} bytes")
    print(f"Rejected sets: {len(rejected)}. Coverage report: tools/dot_sprite_coverage.json")
    check()


def check():
    manifest = read_json(ASSETS / "species_catalog.json")
    pack = ASSETS / "dot_sprites.zip"
    if hashlib.sha256(pack.read_bytes()).hexdigest() != manifest["packSha256"]:
        raise ValueError("Bundled pack checksum mismatch")
    sets = {s["id"]: s for s in manifest["spriteSets"]}
    if len(sets) != len(manifest["spriteSets"]) or len({s["spriteId"] for s in sets.values()}) != len(sets):
        raise ValueError("Duplicate artwork identity")
    checked = set()
    with zipfile.ZipFile(pack) as archive:
        for s in sets.values():
            if s["spriteId"] >= -1 or "idle_1" not in s["frames"]:
                raise ValueError("Invalid reserved Sprite identity or missing idle frame")
            for path in s["frames"].values():
                if path in checked:
                    continue
                raw = archive.read(path)
                if len(raw) != s["width"] * s["height"] * 2 or path != f"frames/{hashlib.sha256(raw).hexdigest()}.bin":
                    raise ValueError(f"Invalid frame: {path}")
                checked.add(path)
    species_ids = set()
    for s in manifest["species"]:
        if s["id"] in species_ids:
            raise ValueError("Duplicate species identity")
        species_ids.add(s["id"])
        refs = s["dotSprites"]["setIds"]
        if not all(ref in sets for ref in refs) or (refs and s["dotSprites"]["defaultSetId"] not in refs):
            raise ValueError(f"Broken artwork association: {s['name']}")
    print(f"Offline artifact check passed: {len(species_ids)} species, {len(sets)} sets, {len(checked)} frames")


if __name__ == "__main__":
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--cache-dir", type=Path)
    parser.add_argument("--offline", action="store_true")
    parser.add_argument("--check", action="store_true")
    arguments = parser.parse_args()
    if arguments.check:
        check()
    elif arguments.cache_dir is None:
        parser.error("--cache-dir is required to keep upstream downloads out of the repository")
    else:
        build(arguments.cache_dir, arguments.offline)
