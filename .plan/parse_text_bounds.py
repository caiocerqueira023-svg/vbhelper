import re
import sys

path = sys.argv[1]
text = open(path, encoding="utf-8").read()
pattern = re.compile(
    r'(text|content-desc)="([^"]+)"[^>]*bounds="\[(\d+),(\d+)\]\[(\d+),(\d+)\]"'
)
for match in pattern.finditer(text):
    kind, label, x1, y1, x2, y2 = match.groups()
    if label.strip():
        print(kind, label, x1, y1, x2, y2)
