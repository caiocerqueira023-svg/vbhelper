"""Lost Evolution ARM9/overlay decompressor (custom backward LZ77 with trailer).

Ported from the in-ROM routine at ARM9 0x02000950 (ARM disassembly verified) and validated
byte-exactly against the reference test vectors of acemon33/DSDigimonStoryARMDecompressor.

File layout: [data ...][trailer 8 bytes]
  word(-8) = (input_end_skip << 24) | (input_start_offset_from_eof)
  word(-4) = output size delta relative to EOF
Decoding:
  input cursor starts at eof - input_end_skip and moves down to eof - input_start_offset
  output is written backwards from index (delta + input_start_offset) down to 0
  control byte, bits MSB->LSB: 0 = literal (one byte), 1 = back reference:
    first byte read = a, second = b
    offset = (((a << 8) | b) & 0x0FFF) + 2
    length = (a >> 4) + 3
    copies are read before each output decrement
An ARM9 image keeps its first 0x4000 bytes (boot/security stub) uncompressed in front.
"""
import struct
import sys
from pathlib import Path

ROOT = Path(r"C:\Users\julye\IdeaProjects\vbhelper")


def decompress(data: bytes) -> bytes:
    size = len(data)
    if size < 16:
        raise ValueError("file too small to carry a trailer")
    trailer_high = struct.unpack_from("<I", data, size - 8)[0]
    delta = struct.unpack_from("<i", data, size - 4)[0]
    cursor = size - (trailer_high >> 24)
    last = size - (trailer_high & 0xFFFFFF)
    out_size = delta + size - last
    if out_size <= 0:
        raise ValueError(f"implausible output size {out_size}")
    out = bytearray(out_size)
    write = out_size
    while True:
        if cursor <= last:
            return bytes(out)
        cursor -= 1
        control = data[cursor]
        for _ in range(8):
            if control & 0x80:
                cursor -= 1
                first = data[cursor]
                cursor -= 1
                second = data[cursor]
                offset = (((first << 8) | second) & 0x0FFF) + 2
                length = (first >> 4) + 3
                for _ in range(length):
                    value = out[write + offset]
                    write -= 1
                    out[write] = value
            else:
                cursor -= 1
                write -= 1
                out[write] = data[cursor]
            control = (control << 1) & 0xFFFFFFFF
            if cursor <= last:
                return bytes(out)


def decompress_arm9(data: bytes) -> bytes:
    """ARM9 output keeps the first 0x4000 boot/security bytes verbatim."""
    return data[:0x4000] + decompress(data)


def main():
    if len(sys.argv) < 3:
        ref = ROOT / "analysis/dsle/decompressor"
        overlay_in = (ref / "overlay9_0.bin").read_bytes()
        overlay_want = (ref / "overlay9_0_dec.bin").read_bytes()
        got = decompress(overlay_in)
        print(f"overlay9_0: out={len(got)} expected={len(overlay_want)} match={got == overlay_want}")
        arm9_in = (ref / "arm9.bin").read_bytes()
        arm9 = decompress_arm9(arm9_in)
        print(f"arm9: {len(arm9_in)} -> {len(arm9)} bytes")
        return
    source = Path(sys.argv[1])
    target = Path(sys.argv[2])
    is_arm = len(sys.argv) > 3 and sys.argv[3] == "arm9"
    data = source.read_bytes()
    output = decompress_arm9(data) if is_arm else decompress(data)
    target.write_bytes(output)
    print(f"{source.name}: {len(data)} -> {len(output)} bytes -> {target}")


if __name__ == "__main__":
    main()
