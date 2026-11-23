"""Build the final "user-ready" VB Lab Offline v2 APK.

Starts from the clean patcher output (v2 baseline), applies a minimal and
technically-safe AndroidManifest.xml adjustment, repackages the ZIP, re-signs
with the same keystore and verifies the result.

Manifest changes (all other APK entries are byte-identical in content):
  1. RecoveryActivity ("VB 離線備份與復原") is no longer a launcher icon:
     its <category android:name="android.intent.category.LAUNCHER"> is removed,
     but the activity + intent-filter stay so it still launches via ADB
     (adb shell am start -n <pkg>/jp.co.chariot.nfc4digivb.RecoveryActivity).
     -> the phone shows exactly ONE app icon; no Chinese backup/file-picker
        screen can be reached by tapping a wrong icon.
  2. All visible labels "VB Lab Offline 測試版" -> "VB Lab Offline".
  3. Removes the leftover Play "derived APK" meta-data
     (com.android.vending.derived.apk.id), so installers cannot mistake this
     for a derived/split install.

The patcher's mandatory first-run / legal notice (ReminderActivity, class in
classes2.dex) is kept untouched, as is the separate package id, the removed
INTERNET permission, isSplitRequired=false, extractNativeLibs=true, etc.

Usage:
  python build_ux_variant.py --baseline <apk> --out <apk> [--soft]
"""
import argparse
import hashlib
import json
import os
import struct
import subprocess
import sys
import tempfile
import zipfile
from pathlib import Path

PATCHER_DIR = Path(r"C:\Users\julye\Downloads\vbhelper-analysis\digibb-1.4.6\patcher\VB-Lab-Local-Patcher")
sys.path.insert(0, str(PATCHER_DIR))
from manifest_patch import Pool, chunks, u32  # noqa: E402

JAVA = Path(r"C:\Program Files\Java\jdk-21\bin\java.exe")
APKSIGNER = Path(r"C:\Users\julye\AppData\Local\Android\Sdk\build-tools\36.0.0\lib\apksigner.jar")
KEYSTORE = Path(r"C:\Users\julye\Downloads\vb-lab-inputs\vb-offline.p12")
KEY_ALIAS = "vb-offline"
KEYSTORE_ENV = "VB_PATCHER_KEYSTORE_PASSWORD"
OLD_LABEL = "VB Lab Offline 測試版"
NEW_LABEL = "VB Lab Offline"
RECOVERY = "jp.co.chariot.nfc4digivb.RecoveryActivity"


def attrs_of(chunk, pool):
    start, size, count = struct.unpack_from("<HHH", chunk, 24)
    for i in range(count):
        pos = 16 + start + i * size
        attr_name = pool.string(u32(chunk, pos + 4))
        typ = chunk[pos + 15]
        value = u32(chunk, pos + 16)
        yield pos, attr_name, typ, value


def attr_dict(chunk, pool):
    return {
        name: (pool.string(value) if typ == 3 else value)
        for _pos, name, typ, value in attrs_of(chunk, pool)
    }


def subtree_len(parts, index):
    """Number of chunk records that make up the element starting at `index`."""
    depth = 0
    for j in range(index, len(parts)):
        typ = parts[j][0]
        if typ == 0x102:
            depth += 1
        elif typ == 0x103:
            depth -= 1
            if depth == 0:
                return j - index + 1
    raise ValueError("unbalanced XML chunks")


def set_string(chunk, pool, pos, value):
    idx = pool.add(value)
    struct.pack_into("<I", chunk, pos + 8, idx)
    chunk[pos + 15] = 3
    struct.pack_into("<I", chunk, pos + 16, idx)


def patch_manifest(data):
    parts = list(chunks(data))
    pool = Pool(next(chunk for typ, chunk in parts if typ == 1))
    edits = []

    def find_elements(tag, attr_name=None, attr_value=None):
        for i, (typ, chunk) in enumerate(parts):
            if typ != 0x102 or pool.string(u32(chunk, 20)) != tag:
                continue
            values = attr_dict(chunk, pool)
            if attr_name is not None and values.get(attr_name) != attr_value:
                continue
            yield i, chunk

    # 1) RecoveryActivity: drop its LAUNCHER category but keep the activity and
    #    its intent-filter (so it remains exported / ADB-launchable).
    recovery_index = next(i for i, chunk in find_elements("activity", "name", RECOVERY))
    rec_len = subtree_len(parts, recovery_index)
    removed = False
    for j in range(recovery_index + 1, recovery_index + rec_len):
        typ, chunk = parts[j]
        if typ != 0x102 or pool.string(u32(chunk, 20)) != "category":
            continue
        if attr_dict(chunk, pool).get("name") == "android.intent.category.LAUNCHER":
            del parts[j:j + subtree_len(parts, j)]
            removed = True
            break
    if not removed:
        raise ValueError("RecoveryActivity LAUNCHER category not found")
    edits.append("RecoveryActivity: removed LAUNCHER category (icon hidden, ADB launch kept)")

    # 2) Remove leftover Play derived-APK metadata (from the split base APK).
    derived = [i for i, chunk in find_elements("meta-data", "name", "com.android.vending.derived.apk.id")]
    for i in reversed(derived):
        del parts[i:i + subtree_len(parts, i)]
        edits.append("removed meta-data com.android.vending.derived.apk.id")
    if not derived:
        edits.append("meta-data com.android.vending.derived.apk.id not present (nothing to do)")

    # 2b) RecoveryActivity label -> clear English name for ADB/system UIs.
    for i, chunk in find_elements("activity", "name", RECOVERY):
        for pos, name, kind, value in attrs_of(chunk, pool):
            if name == "label" and kind == 3 and pool.string(value) != "VB Lab Offline Backup":
                set_string(chunk, pool, pos, "VB Lab Offline Backup")
                edits.append('RecoveryActivity label -> "VB Lab Offline Backup"')

    # 3) Friendly labels (app name / launcher / activities): drop the Chinese
    #    "測試版" suffix so the icon is clearly the game.
    relabelled = 0
    for i, (typ, chunk) in enumerate(parts):
        if typ != 0x102:
            continue
        tag = pool.string(u32(chunk, 20))
        if tag not in ("application", "activity"):
            continue
        for pos, name, kind, value in attrs_of(chunk, pool):
            if name != "label" or kind != 3:
                continue
            if pool.string(value) == OLD_LABEL:
                set_string(chunk, pool, pos, NEW_LABEL)
                relabelled += 1
    if relabelled == 0:
        raise ValueError("no labels to update")
    edits.append(f're-labelled {relabelled} label(s): "{OLD_LABEL}" -> "{NEW_LABEL}"')

    built = bytearray(data[:8])
    for typ, chunk in parts:
        built += pool.build() if typ == 1 else chunk
    struct.pack_into("<I", built, 4, len(built))
    return bytes(built), edits


def verify_manifest_bytes(data):
    parts = list(chunks(data))
    pool = Pool(next(chunk for typ, chunk in parts if typ == 1))
    launchers, labels, tags = [], [], []
    for typ, chunk in parts:
        if typ == 0x102:
            tag = pool.string(u32(chunk, 20))
            tags.append(tag)
            if tag == "category":
                name = attr_dict(chunk, pool).get("name")
                if name == "android.intent.category.LAUNCHER":
                    launchers.append(tag)
            if tag in ("application", "activity"):
                values = attr_dict(chunk, pool)
                if "label" in values:
                    labels.append(values["label"])
    return {
        "chunk_count": len(parts),
        "element_start_count": tags.count("application") + sum(1 for t in tags if t == "activity"),
        "launcher_categories": len(launchers),
        "labels": labels,
        "has_application": "application" in tags,
    }


def repackage(src, dst, replacement):
    """Copy every entry of `src` (except old signatures), swap `replacement`.

    STORED entries get the same 4-byte data alignment the patcher used, so the
    uncompressed native libs/resources stay aligned like in the baseline APK.
    """
    signatures = {"META-INF/MANIFEST.MF", "META-INF/VB-OFFLI.SF", "META-INF/VB-OFFLI.RSA"}
    written = []
    with zipfile.ZipFile(src) as zin, zipfile.ZipFile(dst, "w", zipfile.ZIP_DEFLATED) as zout:
        for info in zin.infolist():
            if info.filename in signatures:
                continue
            data = replacement.get(info.filename)
            if data is None:
                data = zin.read(info.filename)
            new = zipfile.ZipInfo(info.filename, info.date_time)
            new.compress_type = info.compress_type
            new.external_attr = info.external_attr
            new.internal_attr = info.internal_attr
            new.create_system = info.create_system
            if new.compress_type == zipfile.ZIP_STORED:
                padding = (-(zout.fp.tell() + 30 + len(info.filename.encode("utf-8")))) % 4
                if padding:
                    new.extra = struct.pack("<HH", 0xd935, padding) + bytes(padding)
            zout.writestr(new, data)
            written.append(info.filename)
    return written


def sign(unsigned, signed):
    password = os.environ.get(KEYSTORE_ENV)
    if not password:
        raise SystemExit(f"set {KEYSTORE_ENV} before running this script")
    env = dict(os.environ, **{KEYSTORE_ENV: password})
    cmd = [str(JAVA), "-jar", str(APKSIGNER), "sign", "--ks", str(KEYSTORE),
           "--ks-key-alias", KEY_ALIAS, "--ks-pass", "env:" + KEYSTORE_ENV,
           "--min-sdk-version", "26", "--v4-signing-enabled", "false",
           "--out", str(signed), str(unsigned)]
    subprocess.run(cmd, check=True, env=env)
    subprocess.run([str(JAVA), "-jar", str(APKSIGNER), "verify", "--verbose", "--print-certs", str(signed)],
                   check=True, capture_output=True, text=True)


def sha256(path):
    with path.open("rb") as fh:
        return hashlib.file_digest(fh, "sha256").hexdigest()


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--baseline", type=Path, required=True)
    ap.add_argument("--out", type=Path, required=True)
    ap.add_argument("--keep-unsigned", action="store_true")
    args = ap.parse_args()
    if args.out.exists():
        raise SystemExit(f"output already exists: {args.out}")

    report = {"baseline": str(args.baseline), "out": str(args.out),
              "baseline_sha256": sha256(args.baseline), "baseline_size": args.baseline.stat().st_size}
    with zipfile.ZipFile(args.baseline) as z:
        original_manifest = z.read("AndroidManifest.xml")
    patched_manifest, edits = patch_manifest(original_manifest)
    report["manifest_edits"] = edits
    report["manifest_bytes_before"] = len(original_manifest)
    report["manifest_bytes_after"] = len(patched_manifest)
    report["patched_manifest_sha256"] = hashlib.sha256(patched_manifest).hexdigest()
    report["patched_manifest_check"] = verify_manifest_bytes(patched_manifest)

    work = Path(tempfile.mkdtemp(prefix=".vb-ux-", dir=str(args.out.parent)))
    unsigned = work / "unsigned.apk"
    signed = work / "signed.apk"
    try:
        names = repackage(args.baseline, unsigned, {"AndroidManifest.xml": patched_manifest})
        report["repackage_entry_count"] = len(names)
        sign(unsigned, signed)
        with zipfile.ZipFile(signed) as z:
            report["signed_entry_count"] = len(z.infolist())
            report["zip_testzip"] = z.testzip()
        os.replace(signed, args.out)
    finally:
        if not args.keep_unsigned:
            import shutil
            shutil.rmtree(work, ignore_errors=True)

    report["out_sha256"] = sha256(args.out)
    report["out_size"] = args.out.stat().st_size
    print(json.dumps(report, indent=2, ensure_ascii=True))


if __name__ == "__main__":
    main()
