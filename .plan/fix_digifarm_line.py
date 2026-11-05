from pathlib import Path

path = Path("app/src/main/java/com/tamerslab/farm/ui/digifarm/DigifarmScreen.kt")
lines = path.read_bytes().split(b"\n")
index = 317
ending = b"\r" if lines[index].endswith(b"\r") else b""
lines[index] = (
    '                    text = "${farm.name}  \u00b7  ${residents.size}/${farm.capacity}",'.encode("utf-8")
    + ending
)
path.write_bytes(b"\n".join(lines))
print("fixed DigifarmScreen line 318")
