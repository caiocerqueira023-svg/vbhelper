#!/usr/bin/env python3
"""Re-map stale class references in the committed baseline profiles.

The baseline profiles under app/src/release/generated/baselineProfiles were
generated before the Phase 6 feature-package move, so many `com/tamerslab/...`
class names no longer exist. Running the on-device generator again is the
authoritative fix (see docs/performance/BASELINE.md), but that is device-gated.

This script performs a deterministic best-effort re-map without a build:

* Collect every class currently declared in app/src/main (plus the known
  generated classes: Room *_Impl, BuildConfig, R and proto classes).
* For each profile line that references a com/tamerslab class, map each old
  fully-qualified class name to the unique current class with the same simple
  name (nested classes keep their `$Inner` suffix).
* Drop a line when its owning class cannot be resolved, or when one of the
  referenced parameter/return classes cannot be resolved (the ART profile entry
  would never match such a method anyway).
* Deduplicate mapped lines, preserving order.

Usage (from the repository root):
    python scripts/remap-baseline-profiles.py            # dry run, prints stats
    python scripts/remap-baseline-profiles.py --write    # rewrite both profiles
"""

from __future__ import annotations

import argparse
import re
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
PROFILE_DIR = ROOT / "app" / "src" / "release" / "generated" / "baselineProfiles"
PROFILES = ["baseline-prof.txt", "startup-prof.txt"]
# Every module whose classes are packaged into the release APK, plus the app.
SOURCE_MODULES = ["app", "game-save-core", "companion-protocol", "pc-save-companion", "hardware", "hardware-dim"]

MODIFIERS = (
    "public|private|internal|protected|abstract|open|sealed|data|enum|value|"
    "annotation|inline|inner|expect|actual|external|const|suspend|operator|"
    "infix|tailrec|lateinit|override|companion"
)
DECL_RE = re.compile(rf"\b(?:{MODIFIERS}\s+)*(class|interface|object)\s+([A-Za-z_][A-Za-z0-9_]*)")
JAVA_DECL_RE = re.compile(r"\b(?:class|interface|enum|record)\s+([A-Za-z_][A-Za-z0-9_]*)")
PACKAGE_RE = re.compile(r"^\s*package\s+([A-Za-z0-9_.]+)", re.MULTILINE)
JAVA_PACKAGE_RE = re.compile(r"^\s*package\s+([A-Za-z0-9_.]+)\s*;", re.MULTILINE)
REF_RE = re.compile(r"L(com/tamerslab/[A-Za-z0-9_$]+(?:/[A-Za-z0-9_$]+)*);")
LINE_RE = re.compile(r"^(HSPL|HPL|SPL|PL)(.*)$")
# R8/lambda synthetics are regenerated on every build; old entries cannot match.
SYNTHETIC_SUFFIX_RE = re.compile(r"(\$\d+|\$lambda|ExternalSyntheticLambda|^\$\$)", re.IGNORECASE)

GENERATED_SUFFIXES = ["BuildConfig", "R"]
# Explicit renames that the simple-name heuristic cannot infer: the Kotlin DIM
# sprite port replaced the nested Lombok-style classes with top-level ones.
SPECIAL_OLD_TO_NEW = {
    "com/tamerslab/hardware/dim/sprite/SpriteData$Sprite": "com/tamerslab/hardware/dim/sprite/Sprite",
    "com/tamerslab/hardware/dim/sprite/SpriteData$Sprite$SpriteBuilder":
        "com/tamerslab/hardware/dim/sprite/SpriteBuilder",
}
EXTRA_FQNS: set[str] = set()


def strip_comments_and_strings(text: str) -> str:
    text = re.sub(r"/\*.*?\*/", "", text, flags=re.DOTALL)
    text = re.sub(r"//[^\n]*", "", text)
    return text


def collect_current_classes() -> tuple[dict[str, list[str]], set[str]]:
    """Return simple-name -> [fqn], and the full fqn set."""
    by_simple: dict[str, list[str]] = {}
    all_fqns: set[str] = set()

    def add(name: str, fqn: str) -> None:
        all_fqns.add(fqn)
        by_simple.setdefault(name, []).append(fqn)

    def add_kotlin_file(path: Path) -> None:
        if "generated" in path.parts or "build" in path.parts:
            return
        text = strip_comments_and_strings(path.read_text(encoding="utf-8", errors="replace")).lstrip("\ufeff")
        package_match = PACKAGE_RE.search(text)
        if not package_match:
            return
        package = package_match.group(1)
        for decl_match in DECL_RE.finditer(text):
            add(decl_match.group(2), f"{package}.{decl_match.group(2)}")
        # Kotlin top-level functions/properties compile to a <File>Kt facade class.
        add(path.stem + "Kt", f"{package}.{path.stem}Kt")

    def add_java_file(path: Path) -> None:
        if "generated" in path.parts or "build" in path.parts:
            return
        text = strip_comments_and_strings(path.read_text(encoding="utf-8", errors="replace")).lstrip("\ufeff")
        package_match = JAVA_PACKAGE_RE.search(text)
        if not package_match:
            return
        package = package_match.group(1)
        for decl_match in JAVA_DECL_RE.finditer(text):
            add(decl_match.group(1), f"{package}.{decl_match.group(1)}")

    for module in SOURCE_MODULES:
        module_root = ROOT / module / "src" / "main"
        if not module_root.is_dir():
            continue
        for path in sorted(module_root.rglob("*.kt")):
            add_kotlin_file(path)
        for path in sorted(module_root.rglob("*.java")):
            add_java_file(path)

    # Room generates <Dao>_Impl and, in current versions, <Dao>_<Database>_Impl.
    for simple, fqns in list(by_simple.items()):
        for fqn in fqns:
            add(f"{simple}_Impl", f"{fqn}_Impl")
            if simple.endswith("Dao"):
                add(f"{simple}_AppDatabase_Impl", f"{fqn}_AppDatabase_Impl")

    # Application BuildConfig / R live in the app namespace.
    app_namespace = "com.tamerslab"
    for name in GENERATED_SUFFIXES:
        add(name, f"{app_namespace}.{name}")

    # protobuf-lite generates one Java class per message in the declared package.
    for proto_path in sorted((ROOT / "app" / "src" / "main" / "proto").rglob("*.proto")):
        text = proto_path.read_text(encoding="utf-8", errors="replace")
        pkg_match = re.search(r'option\s+java_package\s*=\s*"([^"]+)"', text)
        if not pkg_match:
            continue
        proto_pkg = pkg_match.group(1)
        for message in re.finditer(r"^\s*message\s+([A-Za-z_][A-Za-z0-9_]*)", text, re.MULTILINE):
            add(message.group(1), f"{proto_pkg}.{message.group(1)}")

    all_fqns |= EXTRA_FQNS
    return by_simple, all_fqns


def build_class_map(by_simple: dict[str, list[str]]) -> dict[str, str]:
    return {simple: fqns[0] for simple, fqns in by_simple.items() if len(set(fqns)) == 1}


def rewrite_line(line: str, class_map: dict[str, str], known: set[str], unresolved: list[str] | None = None) -> tuple[str | None, str]:
    """Return (rewritten line or None, reason)."""
    refs = list(REF_RE.finditer(line))
    if not refs:
        return line, "framework"
    mapped_refs: list[str] = []
    for ref in refs:
        old_path = ref.group(1)
        if old_path in SPECIAL_OLD_TO_NEW:
            mapped_refs.append(SPECIAL_OLD_TO_NEW[old_path].replace(".", "/"))
            continue
        if old_path.replace("/", ".") in known:
            mapped_refs.append(old_path)
            continue
        simple = old_path.rsplit("/", 1)[-1]
        suffix = ""
        if "$" in simple:
            simple, suffix = simple.split("$", 1)
            suffix = "$" + suffix
        if simple == "ComposableSingletons" and suffix.endswith("Kt"):
            # Compose generates ComposableSingletons$<File>Kt next to the Kotlin
            # file facade; only the package changes when the file moves.
            facade = class_map.get(suffix[1:])
            if facade is not None:
                new_fqn = facade.replace(".", "/").rsplit("/", 1)
                mapped_refs.append(f"{new_fqn[0]}/{new_fqn[1].split('$')[0]}$" + suffix[1:])
                continue
        new_fqn = class_map.get(simple)
        if new_fqn is None:
            if unresolved is not None:
                unresolved.append(old_path)
            return None, "unresolved"
        mapped_refs.append(new_fqn.replace(".", "/") + suffix)
    rewritten = REF_RE.sub(lambda m: "L" + mapped_refs.pop(0) + ";", line)
    return rewritten, "ok"


def process(path: Path, write: bool, show_dropped: int = 0) -> dict[str, int]:
    lines = path.read_text(encoding="utf-8").splitlines()
    by_simple, known = collect_current_classes()
    class_map = build_class_map(by_simple)
    stats = {"total": len(lines), "framework": 0, "unchanged": 0, "rewritten": 0, "dropped": 0, "deduped": 0}
    out: list[str] = []
    seen: set[str] = set()
    unresolved_refs: list[str] = []
    shown = 0
    for line in lines:
        line_match = LINE_RE.match(line)
        if not line_match:
            if line not in seen:
                out.append(line)
                seen.add(line)
            stats["unchanged"] += 1
            continue
        new_line, reason = rewrite_line(line, class_map, known, unresolved_refs)
        if new_line is None:
            stats["dropped"] += 1
            if shown < show_dropped:
                shown += 1
                print(f"  DROP unresolved={unresolved_refs[-1]}  {line[:160]}")
            continue
        if new_line in seen:
            stats["deduped"] += 1
            continue
        seen.add(new_line)
        out.append(new_line)
        if reason == "framework":
            stats["framework"] += 1
        elif new_line == line:
            stats["unchanged"] += 1
        else:
            stats["rewritten"] += 1
    if write:
        path.write_text("\n".join(out) + "\n", encoding="utf-8")
    stats["kept"] = len(out)
    return stats


def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument("--write", action="store_true", help="rewrite the profile files")
    parser.add_argument("--show-dropped", type=int, default=0, help="print this many dropped lines with the unresolved class")
    args = parser.parse_args()
    for name in PROFILES:
        path = PROFILE_DIR / name
        stats = process(path, args.write, args.show_dropped)
        print(f"{name}: {stats}")
        if args.write:
            print(f"  wrote {path}")


if __name__ == "__main__":
    main()
