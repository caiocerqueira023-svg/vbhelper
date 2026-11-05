import re
import sys

path = sys.argv[1] if len(sys.argv) > 1 else ".plan/ui2.xml"
text = open(path, encoding="utf-8").read()
for match in re.finditer(r'text="([^"]+)"', text):
    value = match.group(1).strip()
    if value:
        print(value)
