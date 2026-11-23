"""Look for an integrity word at the end of the serialized Lost Evolution player state."""
import hashlib
import struct
import zlib
from pathlib import Path

ROOT = Path(r"C:\Users\julye\IdeaProjects\vbhelper")
FILES = {
    "genuine": ROOT / "analysis/dsle/device/now/le-backup1008.sav",
    "newgame": ROOT / "analysis/dsle/device/now2/le-afteraccident.sav",
    "appsend": ROOT / "analysis/dsle/device/now3/le-current.sav",
}

for name, path in FILES.items():
    if not path.exists():
        print("missing", name)
        continue
    data = path.read_bytes()
    base = 0x8000
    state = base + 0x20
    print(f"== {name}")
    for offset in (0x4068, 0x4070, 0x4074, 0x4078, 0x407C, 0x4080, 0x4084, 0x4088):
        value = struct.unpack_from("<I", data, state + offset)[0]
        print(f"   player+{offset:#06x} = {value:#010x}")
    body = data[state:state + 0x4078]
    digest = hashlib.md5(body).digest()
    sha = hashlib.sha1(body).digest()
    candidates = {
        "crc32": zlib.crc32(body) & 0xFFFFFFFF,
        "adler32": zlib.adler32(body) & 0xFFFFFFFF,
        "sum32le": sum(struct.unpack_from("<" + "I" * (len(body) // 4), body)) & 0xFFFFFFFF,
        "md5[0]": struct.unpack_from("<I", digest, 0)[0],
        "md5[1]": struct.unpack_from("<I", digest, 4)[0],
        "md5[2]": struct.unpack_from("<I", digest, 8)[0],
        "md5[3]": struct.unpack_from("<I", digest, 12)[0],
        "sha1[0]": struct.unpack_from("<I", sha, 0)[0],
        "sha1[4]": struct.unpack_from("<I", sha, 16)[0],
    }
    print("   computed:", {key: hex(value) for key, value in candidates.items()})
