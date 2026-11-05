import re

text = open(".plan/ui.xml", encoding="utf-8").read()
nodes = re.findall(r"content-desc=\"([^\"]+)\"[^>]*bounds=\"\[(\d+),(\d+)\]\[(\d+),(\d+)\]\"", text)
for name, x1, y1, x2, y2 in nodes:
    print(name, x1, y1, x2, y2)
