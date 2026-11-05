import re
import sys

path = sys.argv[1]
text = open(path, encoding="utf-8").read()
for match in re.finditer(
    r'content-desc="([^"]+)"[^>]*bounds="\[(\d+),(\d+)\]\[(\d+),(\d+)\]"', text
):
    print(match.group(1), match.group(2, 3, 4, 5))
