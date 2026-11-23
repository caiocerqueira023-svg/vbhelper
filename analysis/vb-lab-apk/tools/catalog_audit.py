"""Deep audit of the patched Addressables catalog + settings + native libs.

Usage: python catalog_audit.py <apk> [--bundles-zip <vb_lab_aa.zip>]
Reports how every Addressables internal id resolves against the APK entries.
"""
import argparse
import json
import struct
import sys
import zipfile
from pathlib import Path

sys.path.insert(0, str(Path(r"C:\Users\julye\Downloads\vb-lab-inputs\venv\Lib\site-packages")))
import UnityPy  # noqa: E402

sys.path.insert(0, str(Path(__file__).resolve().parent))
from audit_apk import string_pool  # noqa: E402


def load_catalog_textasset(blob):
    env = UnityPy.load(blob)
    for obj in env.objects:
        if obj.type.name == "TextAsset":
            ta = obj.read()
            if ta.m_Name in ("catalog", "catalog_1.4.6"):
                raw = ta.m_Script
                return json.loads(bytes(raw).decode("utf-8") if not isinstance(raw, str) else raw)
    return None


def summarize_ids(cat):
    ids = cat.get("m_InternalIds", [])
    buckets = {"http": [], "offline_local": [], "other": []}
    for i in ids:
        s = str(i)
        if s.startswith(("http://", "https://")):
            buckets["http"].append(s)
        elif "Android/offline/" in s:
            buckets["offline_local"].append(s)
        else:
            buckets["other"].append(s)
    return ids, buckets


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("apk", type=Path)
    ap.add_argument("--bundles-zip", type=Path)
    args = ap.parse_args()
    out = {}
    with zipfile.ZipFile(args.apk) as z:
        names = set(z.namelist())
        cat = load_catalog_textasset(z.read("assets/aa/catalog.bundle"))
        if cat is None:
            print(json.dumps({"error": "catalog TextAsset not found"}, indent=2)); return 2
        out["catalog_keys"] = sorted(cat.keys())[:40]
        ids, buckets = summarize_ids(cat)
        out["internal_ids"] = len(ids)
        out["http_count"] = len(buckets["http"])
        out["offline_local_count"] = len(buckets["offline_local"])
        out["other_count"] = len(buckets["other"])
        out["other_samples"] = buckets["other"][:25]
        out["http_samples"] = buckets["http"][:10]
        out["offline_samples"] = [s.rsplit("/", 1)[-1] for s in buckets["offline_local"][:5]]
        # every offline local id -> expected entry name
        apk_bundles = {n.rsplit("/", 1)[-1]: n for n in names if n.startswith("assets/aa/Android/offline/")}
        referenced = [s.rsplit("/", 1)[-1] for s in buckets["offline_local"]]
        referenced_unique = set(referenced)
        out["offline_unique_referenced"] = len(referenced_unique)
        out["offline_present_in_apk"] = len(referenced_unique & set(apk_bundles))
        out["offline_missing_from_apk"] = sorted(referenced_unique - set(apk_bundles))[:20]
        out["offline_missing_count"] = len(referenced_unique - set(apk_bundles))
        out["apk_bundles_not_in_catalog"] = sorted(set(apk_bundles) - referenced_unique)[:20]
        out["apk_bundles_not_in_catalog_count"] = len(set(apk_bundles) - referenced_unique)
        # per-bundle datapoints
        out["m_Bundles_present"] = "m_bundles" in cat
        for key in ("m_Bundles", "m_bundles"):
            if key in cat:
                out[key + "_len"] = len(cat[key])
        # settings
        settings = json.loads(z.read("assets/aa/settings.json"))
        out["settings_keys"] = sorted(settings.keys())
        out["settings_catalog_locations"] = settings.get("m_CatalogLocations")
        out["settings_disable_update"] = settings.get("m_DisableCatalogUpdateOnStart")
        # original catalog comparison
        if args.bundles_zip and args.bundles_zip.is_file():
            with zipfile.ZipFile(args.bundles_zip) as bz:
                orig_raw = bz.read("asset/20190428/Android/catalog_1.4.6.json")
            orig = json.loads(orig_raw)
            oids, obuckets = summarize_ids(orig)
            out["original_catalog"] = {
                "internal_ids": len(oids),
                "http_count": len(obuckets["http"]),
                "other_count": len(obuckets["other"]),
                "other_samples": obuckets["other"][:10],
                "prefix_sample": oids[0] if oids else None,
                "sha256_matches_recipe": None,
            }
            import hashlib
            out["original_catalog"]["sha256"] = hashlib.sha256(orig_raw).hexdigest()
    print(json.dumps(out, indent=2, ensure_ascii=False))
    return 0


if __name__ == "__main__":
    sys.exit(main())
