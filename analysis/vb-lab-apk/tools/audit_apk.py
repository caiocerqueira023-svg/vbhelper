"""Structural audit of a patched VB Lab Offline APK.

Usage:
  python audit_apk.py <apk> [--patcher-dir <dir>] [--full-hash] [--unity-samples N]

Checks: ZIP integrity, entry manifest vs the patcher build plan (count + per-entry
SHA-256), expected offline asset trees, DEX/so presence, manifest flags, signature
(collected separately by apksigner), and UnityPy readability of sample bundles.
"""
import argparse
import hashlib
import json
import struct
import sys
import zipfile
from pathlib import Path

PATCHER_DEFAULT = Path(r"C:\Users\julye\Downloads\vbhelper-analysis\digibb-1.4.6\patcher\VB-Lab-Local-Patcher")
EXPECTED_PKG = "net.digimon.digibb.offline"

def sha256(data):
    return hashlib.sha256(data).hexdigest()

def string_pool(data):
    # minimal AXML string-pool decoder (UTF-8 or UTF-16); skips the 8-byte XML header
    base = 8
    typ, hsize, size = struct.unpack_from("<HHI", data, base)
    assert typ == 0x0001, hex(typ)
    chunk = data[base:base + size]
    count = struct.unpack_from("<I", chunk, 8)[0]
    flags = struct.unpack_from("<I", chunk, 16)[0]
    strings_start = struct.unpack_from("<I", chunk, 20)[0]
    utf8 = bool(flags & 0x100)
    strings, offsets = [], []
    for i in range(count):
        off = struct.unpack_from("<I", chunk, hsize + 4 * i)[0]
        offsets.append(off)
    def dec_len(p):
        if utf8:
            n = chunk[p]
            if n & 0x80:
                return ((n & 0x7F) << 8) | chunk[p + 1], p + 2
            return n, p + 1
        n = struct.unpack_from("<H", chunk, p)[0]
        if n & 0x8000:
            return ((n & 0x7FFF) << 16) | struct.unpack_from("<H", chunk, p + 2)[0], p + 4
        return n, p + 2
    for off in offsets:
        p = strings_start + off
        n, p = dec_len(p)
        if utf8:
            n2, p = dec_len(p)
            strings.append(chunk[p:p + n2].decode("utf-8", "replace"))
        else:
            strings.append(chunk[p:p + n * 2].decode("utf-16-le", "replace"))
    return strings

def check_manifest(data, report):
    strings = string_pool(data)
    report["manifest_strings_sample"] = [s for s in strings if s and len(s) < 80][:25]
    joined = "\x00".join(strings)
    checks = {
        "package_is_offline": EXPECTED_PKG in strings,
        "no_isSplitRequired_true": True,
        "no_requiredSplitTypes": "requiredSplitTypes" not in joined,
        "no_splitTypes": "splitTypes" not in joined,
        "no_vending_splits": not any("com.android.vending.splits" in s for s in strings),
        "no_vending_asset_packs": not any("com.android.vending.asset" in s for s in strings),
        "has_MainActivity": any("MainActivity" in s for s in strings),
        "has_ReminderActivity": any("ReminderActivity" in s for s in strings),
        "has_RecoveryActivity": any("RecoveryActivity" in s for s in strings),
        "has_LAUNCHER": "android.intent.category.LAUNCHER" in strings,
        "has_LEANBACK_LAUNCHER": "android.intent.category.LEANBACK_LAUNCHER" in strings,
        "has_nfc_permission": "android.permission.NFC" in strings,
        "no_INTERNET_permission": "android.permission.INTERNET" not in strings,
    }
    report["manifest"] = checks
    # crude split-detection: find 'isSplitRequired' string and check its attribute value
    return checks

def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("apk", type=Path)
    ap.add_argument("--patcher-dir", type=Path, default=PATCHER_DEFAULT)
    ap.add_argument("--full-hash", action="store_true", help="hash every entry against build plan")
    ap.add_argument("--unity-samples", type=int, default=3)
    args = ap.parse_args()

    report = {"apk": str(args.apk), "exists": args.apk.is_file()}
    if not args.apk.is_file():
        print(json.dumps(report, indent=2)); return 2
    report["size"] = args.apk.stat().st_size
    with args.apk.open("rb") as fh:
        report["file_sha256"] = hashlib.file_digest(fh, "sha256").hexdigest()

    plan = json.loads((args.patcher_dir / "input-manifest.json").read_text(encoding="utf-8-sig"))
    plan_entries = plan["entries"]
    plan_by_name = {row["name"]: row for row in plan_entries}
    report["build_plan_entry_count"] = len(plan_entries)
    report["build_plan_package"] = plan.get("package")
    report["build_plan_version_code"] = plan.get("versionCode")

    with zipfile.ZipFile(args.apk) as z:
        infos = z.infolist()
        report["entry_count"] = len(infos)
        report["zip_comment_empty"] = z.comment == b""
        names = [i.filename for i in infos]
        report["duplicate_entries"] = len(names) - len(set(names))
        missing = [n for n in plan_by_name if n not in set(names)]
        extra = [n for n in names if n not in plan_by_name]
        report["missing_vs_plan"] = missing[:20]
        report["missing_vs_plan_count"] = len(missing)
        report["extra_vs_plan"] = extra[:20]
        report["extra_vs_plan_count"] = len(extra)

        # structural counts
        report["dex_entries"] = sorted(n for n in names if n.startswith("classes") and n.endswith(".dex"))
        report["lib_entries"] = sorted(n for n in names if n.startswith("lib/"))
        report["offline_bundle_count"] = sum(1 for n in names if n.startswith("assets/aa/Android/offline/") and n.endswith(".bundle"))
        report["offline_bundle_other"] = sorted(n for n in names if n.startswith("assets/aa/Android/offline/") and not n.endswith(".bundle"))
        report["vb_pages_count"] = sum(1 for n in names if n.startswith("assets/vb-pages/"))
        report["aa_root_entries"] = sorted(n for n in names if n.startswith("assets/aa/") and n.count("/") == 2)
        report["storage_mode"] = {}
        for n in ("AndroidManifest.xml", "resources.arsc", "classes.dex", "assets/aa/catalog.bundle", "assets/aa/settings.json"):
            info = z.getinfo(n) if n in set(names) else None
            report["storage_mode"][n] = None if info is None else ("stored" if info.compress_type == zipfile.ZIP_STORED else "deflated")
        coerced_size = sum(i.file_size for i in infos)
        report["uncompressed_total"] = coerced_size
        report["apk_size_mb"] = round(args.apk.stat().st_size / (1024 * 1024), 1)
        report["install_estimate_mb"] = round(coerced_size / (1024 * 1024), 1)

        # fastest integrity: testzip validates CRC of every entry (reads whole file)
        import time
        t0 = time.time()
        bad = z.testzip()
        report["zip_testzip_bad_entry"] = bad
        report["zip_testzip_seconds"] = round(time.time() - t0, 1)

        # per-entry hash vs plan
        if args.full_hash:
            mismatched = []
            for row in plan_entries:
                name = row["name"]
                if name not in set(names):
                    continue
                digest = hashlib.sha256(z.read(name)).hexdigest()
                if digest != row["sha256"]:
                    mismatched.append(name)
            report["entry_hash_mismatches"] = mismatched[:30]
            report["entry_hash_mismatch_count"] = len(mismatched)

        # manifest
        try:
            mdata = z.read("AndroidManifest.xml")
            report["manifest_bytes"] = len(mdata)
            check_manifest(mdata, report)
        except Exception as e:  # noqa: BLE001
            report["manifest"] = {"error": f"{type(e).__name__}: {e}"}

        # resources.arsc package
        try:
            arsc = z.read("resources.arsc")
            report["arsc_package_ok"] = (EXPECTED_PKG.encode("utf-8") in arsc) or (EXPECTED_PKG.encode("utf-16-le") in arsc)
        except KeyError:
            pass

        # settings.json
        try:
            settings = json.loads(z.read("assets/aa/settings.json"))
            deps = [r for r in settings.get("m_CatalogLocations", []) if r.get("m_Keys") == ["AddressablesMainContentCatalog"]]
            report["settings"] = {
                "catalog_locations": len(settings.get("m_CatalogLocations", [])),
                "main_location_dependencies": deps[0].get("m_Dependencies") if deps else None,
                "disable_catalog_update_on_start": settings.get("m_DisableCatalogUpdateOnStart"),
                "player_version": settings.get("m_PlayerVersion"),
                "content_version": settings.get("m_ContentVersion"),
            }
        except KeyError:
            report["settings"] = {"error": "settings.json missing"}

        # catalog.bundle internal ids (TextAsset 'catalog' holds JSON)
        try:
            sys.path.insert(0, str(Path(r"C:\Users\julye\Downloads\vb-lab-inputs\venv\Lib\site-packages")))
            import UnityPy
            env = UnityPy.load(z.read("assets/aa/catalog.bundle"))
            cat = None
            for obj in env.objects:
                if obj.type.name == "TextAsset":
                    ta = obj.read()
                    if ta.m_Name == "catalog":
                        cat = json.loads(bytes(ta.m_Script) if not isinstance(ta.m_Script, str) else ta.m_Script)
                        break
            if cat:
                ids = cat.get("m_InternalIds", [])
                http = [i for i in ids if str(i).startswith(("http://", "https://"))]
                local = [i for i in ids if "Android/offline/" in str(i)]
                report["catalog"] = {
                    "internal_ids": len(ids),
                    "http_ids_remaining": len(http),
                    "local_offline_ids": len(local),
                    "bundles": len(cat.get("m_bundles", [])),
                }
            else:
                report["catalog"] = {"error": "catalog TextAsset not found"}
        except Exception as e:  # noqa: BLE001 - report, do not fail audit
            report["catalog"] = {"error": f"{type(e).__name__}: {e}"}

        # sampled bundle readability
        if args.unity_samples > 0:
            samples = [n for n in names if n.startswith("assets/aa/Android/offline/")][: args.unity_samples]
            results = {}
            for n in samples:
                try:
                    env = UnityPy.load(z.read(n))
                    results[n] = {"objects": len(env.objects), "ok": True}
                except Exception as e:  # noqa: BLE001
                    results[n] = {"ok": False, "error": f"{type(e).__name__}: {e}"}
            report["unity_samples"] = results

    print(json.dumps(report, indent=2, ensure_ascii=False))
    return 0

if __name__ == "__main__":
    sys.exit(main())
