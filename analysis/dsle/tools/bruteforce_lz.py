"""Brute-force the exact backward-LZ variant against the reference decompressed vector."""
import itertools
import struct
from pathlib import Path

ROOT = Path(r"C:\Users\julye\IdeaProjects\vbhelper")
REF = ROOT / "analysis/dsle/decompressor"
DATA = (REF / "overlay9_0.bin").read_bytes()
WANT = (REF / "overlay9_0_dec.bin").read_bytes()


def decompress(data, *, swap_bytes, offset_add, length_shift, read_before_decrement, offset_from_second):
    size = len(data)
    high = struct.unpack_from("<I", data, size - 8)[0]
    delta = struct.unpack_from("<i", data, size - 4)[0]
    cursor = size - (high >> 24)
    last = size - (high & 0xFFFFFF)
    out_size = delta + size - last
    out = bytearray(out_size)
    write = out_size
    try:
        while True:
            if cursor <= last:
                return bytes(out)
            cursor -= 1
            control = data[cursor]
            for _ in range(8):
                if control & 0x80:
                    cursor -= 1
                    a = data[cursor]
                    cursor -= 1
                    b = data[cursor]
                    high_byte, low_byte = (b, a) if swap_bytes else (a, b)
                    value = (high_byte << 8) | low_byte
                    offset = (value & 0x0FFF) + offset_add
                    length = ((high_byte if offset_from_second else low_byte) >> length_shift) + 3
                    for _ in range(length):
                        if read_before_decrement:
                            value_byte = out[write + offset]
                            write -= 1
                            out[write] = value_byte
                        else:
                            write -= 1
                            out[write] = out[write + offset]
                else:
                    cursor -= 1
                    write -= 1
                    out[write] = data[cursor]
                control = (control << 1) & 0xFFFFFFFF
                if cursor <= last:
                    return bytes(out)
    except IndexError:
        return None


def main():
    results = []
    for swap_bytes, offset_add, length_shift, read_before, offset_from_second in itertools.product(
            (False, True), (0, 1, 2), (4, 8), (False, True), (False, True)):
        out = decompress(DATA, swap_bytes=swap_bytes, offset_add=offset_add, length_shift=length_shift,
                         read_before_decrement=read_before, offset_from_second=offset_from_second)
        if out is None or len(out) != len(WANT):
            results.append((0, (swap_bytes, offset_add, length_shift, read_before, offset_from_second)))
            continue
        matching = sum(1 for x, y in zip(out, WANT) if x == y)
        if matching == len(WANT):
            print("EXACT MATCH:", (swap_bytes, offset_add, length_shift, read_before, offset_from_second))
            return
        results.append((matching, (swap_bytes, offset_add, length_shift, read_before, offset_from_second)))
    results.sort(reverse=True)
    print("best candidates (matched bytes / total", len(WANT), "):")
    for matching, params in results[:8]:
        print(f"  {matching:7d} {params}")


if __name__ == "__main__":
    main()
