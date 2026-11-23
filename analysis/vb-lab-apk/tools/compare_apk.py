"""Compare two APKs entry-by-entry (names, sizes, CRCs, hashes) and signing blocks."""
import hashlib
import json
import struct
import sys
import zipfile
from pathlib import Path


def signing_block_sha(path):
    """Locate the APK Signing Block (between file data and central directory) and hash it."""
    data = path.read_bytes()
    eocd = data.rfind(b"PK\x05\x06")
    if eocd < 0:
        return None
    cd_offset = struct.unpack_from("<I", data, eocd + 16)[0]
    if data[cd_offset - 16:cd_offset] != b"APK Sig Block 42":
        return None
    size = struct.unpack_from("<Q", data, cd_offset - 24)[0]
    start = cd_offset - size - 8
    block = data[start:cd_offset]
    return {"offset": start, "size": len(block), "sha256": hashlib.sha256(block).hexdigest()}


def main():
    a, b = Path(sys.argv[1]), Path(sys.argv[2])
    out = {"a": str(a), "b": str(b)}
    with zipfile.ZipFile(a) as za, zipfile.ZipFile(b) as zb:
        ia = {i.filename: i for i in za.infolist()}
        ib = {i.filename: i for i in zb.infolist()}
        out["entry_count"] = {"a": len(ia), "b": len(ib)}
        out["names_only_in_a"] = sorted(set(ia) - set(ib))
        out["names_only_in_b"] = sorted(set(ib) - set(ia))
        diffs = []
        for name in sorted(set(ia) & set(ib)):
            x, y = ia[name], ib[name]
            same_meta = (x.file_size == y.file_size and x.compress_type == y.compress_type)
            if same_meta:
                if name.startswith(("classes", "AndroidManifest", "resources.arsc")):
                    same = hashlib.sha256(za.read(name)).digest() == hashlib.sha256(zb.read(name)).digest()
                else:
                    same = x.CRC == y.CRC
            else:
                same = False
            if not same:
                diffs.append({
                    "name": name,
                    "a": {"size": x.file_size, "csize": x.compress_size, "crc": format(x.CRC, "08x"), "method": x.compress_type},
                    "b": {"size": y.file_size, "csize": y.compress_size, "crc": format(y.CRC, "08x"), "method": y.compress_type},
                })
        out["differing_entries"] = diffs
        out["differing_count"] = len(diffs)
    out["signing_block_a"] = signing_block_sha(a)
    out["signing_block_b"] = signing_block_sha(b)
    print(json.dumps(out, indent=2, ensure_ascii=False))


if __name__ == "__main__":
    main()
