from pathlib import Path

roots = [Path("app/src/main/java"), Path("app/src/main/res")]
pairs = [
    ("\u00c3\u0192\u00c2\u00a9", "\u00e9"),
    ("\u00c3\u0192\u00c2\u00a1", "\u00e1"),
    ("\u00c3\u0192\u00c2\u00a3", "\u00e3"),
    ("\u00c3\u0192\u00c2\u00ba", "\u00fa"),
    ("\u00c3\u0192\u00c2\u00b3", "\u00f3"),
    ("\u00c3\u0192\u00c2\u00ad", "\u00ed"),
    ("\u00c3\u0192\u00c2\u00a7", "\u00e7"),
    ("\u00c3\u0192\u00c2\u00aa", "\u00ea"),
    ("\u00c3\u0192\u00c2\u00b4", "\u00f4"),
    ("\u00c3\u0192\u00c2\u00a2", "\u00e2"),
    ("\u00c3\u0192\u00e2\u20ac\u201d", "\u00d7"),
    ("\u00c3\u00a9", "\u00e9"),
    ("\u00c3\u00a1", "\u00e1"),
    ("\u00c3\u00a3", "\u00e3"),
    ("\u00c3\u00ba", "\u00fa"),
    ("\u00c3\u00b3", "\u00f3"),
    ("\u00c3\u00ad", "\u00ed"),
    ("\u00c3\u00a7", "\u00e7"),
    ("\u00c3\u00aa", "\u00ea"),
    ("\u00c3\u00b4", "\u00f4"),
    ("\u00c3\u00a0", "\u00e0"),
    ("\u00c3\u00a2", "\u00e2"),
    ("\u00c3\u00b5", "\u00f5"),
    ("\u00c3\u0089", "\u00c9"),
    ("\u00c3\u0087", "\u00c7"),
    ("\u00c3\u2014", "\u00d7"),
    ("\u00c3\u201a\u00c2\u00b1", "\u00b1"),
    ("\u00c3\u201a\u00c2\u00b0", "\u00b0"),
    ("\u00c3\u201a\u00c2\u00b7", "\u00b7"),
    ("\u00c3\u201a", ""),
    ("\u00c2\u00b7", "\u00b7"),
    ("\u00c2\u00b1", "\u00b1"),
    ("\u00c2\u00b0", "\u00b0"),
    ("\u00e2\u20ac\u201d", "\u2014"),
    ("\u00e2\u20ac\u201c", "\u2013"),
    ("\u00e2\u20ac\u2122", "'"),
    ("\u00e2\u20ac\u0153", '"'),
    ("\u00e2\u20ac", '"'),
]
changed = []
for root in roots:
    for path in root.rglob("*"):
        if path.suffix.lower() not in (".kt", ".xml") or not path.is_file():
            continue
        try:
            original = path.read_text(encoding="utf-8")
        except UnicodeDecodeError:
            continue
        text = original
        for old, new in pairs:
            text = text.replace(old, new)
        if text != original:
            path.write_text(text, encoding="utf-8")
            changed.append(str(path))
print("files repaired:", len(changed))
for item in changed[:60]:
    print(" ", item)
