#!/usr/bin/env python3
"""Free Talk ("Papo") v1 — deterministic content-pack generator (workstream A).

WHAT THIS BUILDS
----------------
    app/src/main/assets/freechat/v1/en/manifest.json
    app/src/main/assets/freechat/v1/en/free_chat.json          (generated priority corpus)
    app/src/main/assets/freechat/v1/en/species_voice.json
    app/src/main/assets/freechat/v1/en/database_fallback.json  (public-dialogue LAST RESORT)

The generated intent/reply corpus is the PRIMARY answer source and is never
mixed with the database pairs. The database fallback is dataset dialogue kept
verbatim from the owner-provided public-corpus samples (personal build; see
`seed_provenance.json` and DISTRIBUTION_GATE.md). It is shipped only so the
runtime can answer when generated content has no match; no generated reply is
ever converted into a DB pair or vice versa.

Optional (only when requested, never shipped):
    tools/freechat/seed_mining_report.json          (--seed-pairs <jsonl>)

ORIGINALITY POLICY (hard rule; plan decision D1 / contract §1)
--------------------------------------------------------------
Everything shipped by this generator is composed from the hand-authored banks
in `tools/freechat/data/` (written for this project) plus FACT ATOMS read from
`app/src/main/assets/species_catalog.json` (name / level / type / specialMoves).
The catalog `profile` prose is never read by this generator. No external corpus
text is copied, and no assistant/real-world prose is allowed in replies;
`validate_freechat.py` re-checks the shipped text against the catalog profiles,
the existing DigiLine packs and the legacy species-chat asset.

The `database_fallback.json` asset is the documented exception: it ships
dataset dialogue (owner responsibility, personal build, distribution gate
removes/replaces it) for LAST-RESORT use only, after every generated layer.

PIPELINE
--------
1. authored data banks: topics (tone/stage frame banks), intents (match-phrase
   banks + reply openers), grounding frames (catalog fact-atom frames), chips,
   fallbacks, safety lexicons;
2. replies are composed per intent: `authored opener + topic tone-frame`, so the
   same topic yields several tones (personality-dependent) and stage variants;
   "deep" topics render all 8 tones x 2 frames per tone;
3. species voices are generated 1:1 from the catalog (no prose);
4. the runtime database fallback is filtered/deduped from `tools/freechat/_seeds/`
   (owner-provided public-corpus samples, git-ignored);
5. JSON is written with sorted keys, explicit sort orders and no timestamps, so
   two runs over the same inputs produce byte-identical files.

DETERMINISM
-----------
No randomness, no clock, no locale-dependent sorting. Every variant choice is an
index derived from the authored order (never from Python hash()). `--seed-pairs`
is used for an offline MINING REPORT only and never changes the shipped assets.

FLAGS
-----
    --scale N   1 (default) = shipped Big-lite profile: deep topics 8 tones x 2
                frames per tone, other topics 4 tones x 1 frame; 2 = every intent
                rendered deep (Big v2 draft scale)
    --limit N   limit intents to the first N sorted ids (smoke runs only; the
                shipped pack must be generated without it)
    --seed-pairs <jsonl>  public-corpus turn pairs used for intent/phrase MINING
                         only (writes tools/freechat/seed_mining_report.json;
                         the shipped corpus is unchanged and never includes seed text)
    --database-seeds <dir-or-jsonl>  owner-provided public-dialogue samples for
                         database_fallback.json (default tools/freechat/_seeds/)

Run:  python tools/freechat/build_freechat.py
"""

from __future__ import annotations

import argparse
import hashlib
import json
import re
import sys
import unicodedata
from pathlib import Path

HERE = Path(__file__).resolve().parent
REPO_ROOT = HERE.parents[1]
ASSET_DIR = REPO_ROOT / "app" / "src" / "main" / "assets"
CATALOG_PATH = ASSET_DIR / "species_catalog.json"
OUT_ROOT = ASSET_DIR / "freechat" / "v1"
OUT_EN = OUT_ROOT / "en"
DATA_DIR = HERE / "data"
SEED_DIR = HERE / "_seeds"
SEED_REPORT_PATH = HERE / "seed_mining_report.json"
DB_FALLBACK_FILE = "database_fallback.json"

SCHEMA_VERSION = 1
CONTENT_VERSION = 1
LOCALE = "en"

# Runtime database fallback (owner requirement 2026-11-18): public-dialogue
# samples ship as LAST-RESORT dataset dialogue only. The generated corpus above
# stays the priority source; these pairs are never used to compose it.
DB_SCHEMA_VERSION = 1
DB_CONTENT_VERSION = 1
DB_SOURCE = "cornell+oasst1+soda samples (personal build)"
DB_PAIR_LIMIT = 5000
DB_PT_PAIR_LIMIT = 300
DB_MAX_RESPONSE_CHARS = 200
DB_MAX_USER_CHARS = 200
DB_LICENSE_BY_SOURCE = {
    "cornell-movie-dialogs": "research-only (personal build)",
    "oasst1-en": "Apache-2.0",
    "oasst1-pt": "Apache-2.0",
    "soda": "CC BY 4.0",
}

# "Deep" topics render all 8 tones x 2 frames per tone in the shipped profile,
# so the high-traffic intents get the deepest variant pools (owner Big v1 ask).
DEEP_TOPICS = {
    "greeting", "tamer_bond", "comfort_low", "identity_self", "training",
    "battle", "evolution", "food_bits", "out_of_world", "rest_quiet",
}

# Contract §1 tone vocabulary. Personality types 1..16 (DigimonPersonalityType) are
# grouped into the tone register each type's authored lines use, so a personality
# always hears a matching register first (engine soft filter, contract §2 rule 4).
TONES = ["warm", "dry", "courage", "compassion", "insight", "friendship", "playful", "formal"]
TONE_TYPES = {
    "warm": [1, 14],          # ADORING, FRIENDLY
    "friendship": [2, 15],    # DEVOTED, SOCIABLE
    "compassion": [3, 4, 16],  # TOLERANT, OVERPROTECTIVE, COMPASSIONATE
    "courage": [5, 6, 8],     # ZEALOUS, BRAVE, DARING
    "playful": [7, 10, 13],   # RECKLESS, SLY, OPPORTUNISTIC
    "insight": [9],           # ENLIGHTENED
    "dry": [11],              # ASTUTE
    "formal": [12],           # STRATEGIC
}
TONE_ENERGY = {"warm": 1, "friendship": 2, "courage": 2, "playful": 2,
               "compassion": 1, "insight": 0, "dry": 0, "formal": 0}
STAGE_TIER_ENERGY = [0, 1, 1, 2]
STAGE_TONE_ORDER = ["warm", "courage", "insight", "dry"]

# Contract §1 / plan §7.3 stage-tier mapping (same convention as DigilineStages).
LEVEL_TO_TIER = {
    "Baby I": 1, "Baby II": 1, "Child": 1,
    "Adult": 2, "Champion": 2, "Armor": 2, "Hybrid": 2,
    "Perfect": 3, "Ultimate": 4,
}
UNKNOWN_FACT_VALUES = {"", "Unknown", "Species Unknown", "(?)", "None"}
DEFAULT_TIER = 2
MIN_TIER = 1
MAX_TIER = 4

# Shipped seed-corpus target (contract §1 floors raised by the owner Big v1 ask;
# validator enforces the same floors).
MIN_INTENTS = 200
MIN_REPLIES = 3200
MIN_MATCH_PHRASES = 1600
SPECIES_VOICE_COUNT = 1662

MAX_LINE_CHARS = 120          # contract §1 keeps authored lines <= 120 chars
MAX_SENTENCES = 2
STOPWORDS = {
    "a", "about", "after", "again", "all", "am", "an", "and", "any", "are", "as", "at", "be",
    "because", "been", "before", "being", "but", "by", "can", "could", "did", "do", "does",
    "doing", "don", "down", "during", "each", "few", "for", "from", "get", "got", "had", "has",
    "have", "having", "he", "her", "here", "hers", "him", "his", "how", "i", "if", "in", "into",
    "is", "it", "its", "just", "let", "like", "me", "more", "most", "my", "no", "nor", "not",
    "now", "of", "off", "on", "once", "only", "or", "other", "our", "out", "over", "own", "so",
    "some", "such", "than", "that", "the", "their", "them", "then", "there", "these", "they",
    "this", "those", "through", "to", "too", "under", "up", "us", "very", "was", "we", "were",
    "what", "when", "where", "which", "while", "who", "whom", "why", "will", "with", "would",
    "you", "your", "yours", "s", "t", "m", "re", "ve", "ll", "d",
}


# Words that must never reach a shipped reply through a catalog fact atom (a
# species name or move that collides with real-world/assistant vocabulary is
# excluded from grounding rendering; default sequence rendering is otherwise safe).
BANNED_ATOM_PATTERN = re.compile(
    r"\b(assistant|human|humans|internet|online|smartphone|phone|wifi|website|"
    r"wikipedia|google|netflix|school|news|api|prompt|algorithm|politician|"
    r"politics|election|president|celebrity|religion|bitcoin|nft|pokemon|mario|"
    r"netherworld|macca|negotiation|chatgpt|openai|llm)\b"
)


class BuildError(RuntimeError):
    """Raised for a data-bank or generated-content problem (no partial output)."""


# ---------------------------------------------------------------------------
# Deterministic helpers (same conventions as tools/digiline/build_packs.py)
# ---------------------------------------------------------------------------
def load_json(path: Path):
    with path.open("r", encoding="utf-8-sig") as handle:
        return json.load(handle)


def write_json(path: Path, payload) -> None:
    path.parent.mkdir(parents=True, exist_ok=True)
    text = json.dumps(payload, ensure_ascii=False, indent=2, sort_keys=True) + "\n"
    with path.open("w", encoding="utf-8", newline="\n") as handle:
        handle.write(text)


def normalize_key(value: str) -> str:
    """Contract §1.1 speciesKey normalization: lowercase letters/digits only."""
    text = unicodedata.normalize("NFKD", str(value))
    text = "".join(ch for ch in text if not unicodedata.combining(ch))
    return re.sub(r"[^a-z0-9]", "", text.lower())


def normalize_loose(value: str) -> str:
    """Case/punctuation-insensitive form used for duplicate-text checks."""
    text = unicodedata.normalize("NFKD", str(value))
    text = "".join(ch for ch in text if not unicodedata.combining(ch))
    return re.sub(r"[^a-z0-9]+", " ", text.lower()).strip()


def sha256_text(text: str) -> str:
    return hashlib.sha256(text.encode("utf-8")).hexdigest()


def stable_index(*parts: object) -> int:
    """Stable, process-independent index source (sha256, never Python hash())."""
    payload = "|".join(str(part) for part in parts).encode("utf-8")
    return int.from_bytes(hashlib.sha256(payload).digest()[:8], "big")


def is_meaningful(value) -> bool:
    return bool(value) and str(value).strip() not in UNKNOWN_FACT_VALUES


def tier_for_level(level) -> int:
    if not is_meaningful(level):
        return DEFAULT_TIER
    return LEVEL_TO_TIER.get(str(level).strip(), DEFAULT_TIER)


def sentence_count(text: str) -> int:
    return len([part for part in re.split(r"[.!?]+", text) if part.strip()])


_LEAD_PROTECTED_WORDS = {"i", "i'm", "i've", "i'll", "i'd"}


def squash_sentences(text: str) -> str:
    """Join an authored multi-sentence line into one clause with an em dash.

    Authored banks occasionally carry two short sentences for rhythm, but a
    composed reply is `opener + frame`, so every part must contribute at most one
    sentence to stay inside the contract's 1-2 sentence rule. This helper is
    deterministic and leaves single-sentence lines untouched (mixed cadence):
    "Good. The day has direction." -> "Good — the day has direction."
    """
    parts = [part.strip() for part in re.split(r"(?<=[.!?])\s+", text.strip()) if part.strip()]
    if len(parts) <= 1:
        return text.strip()
    produced: list[str] = []
    for part in parts:
        stripped = part.rstrip(".!?").strip()
        if not stripped:
            continue
        if produced:
            first, _, rest = stripped.partition(" ")
            if first[:1].isupper() and first.lower() not in _LEAD_PROTECTED_WORDS:
                first = first[0].lower() + first[1:]
            stripped = (first + " " + rest).strip()
        produced.append(stripped)
    squashed = " — ".join(produced) + parts[-1][-1]
    if sentence_count(squashed) != 1:
        raise BuildError(f"internal: squash kept multiple sentences: {text!r} -> {squashed!r}")
    return squashed


def line_errors(text: str, label: str) -> list[str]:
    errors: list[str] = []
    if not text or not text.strip():
        errors.append(f"{label}: empty text")
        return errors
    if len(text) > MAX_LINE_CHARS:
        errors.append(f"{label}: {len(text)} chars > {MAX_LINE_CHARS}: {text!r}")
    if sentence_count(text) > MAX_SENTENCES:
        errors.append(f"{label}: more than {MAX_SENTENCES} sentences: {text!r}")
    if text != text.strip():
        errors.append(f"{label}: leading/trailing whitespace: {text!r}")
    if "  " in text:
        errors.append(f"{label}: repeated spaces: {text!r}")
    if "{" in text or "}" in text:
        errors.append(f"{label}: unresolved template slot: {text!r}")
    return errors


def check_line(text: str, label: str) -> str:
    problems = line_errors(text, label)
    if problems:
        raise BuildError("\n".join(problems))
    return text


# ---------------------------------------------------------------------------
# Catalog + species voices
# ---------------------------------------------------------------------------
def load_catalog() -> dict:
    if not CATALOG_PATH.is_file():
        raise BuildError(f"missing catalog: {CATALOG_PATH}")
    catalog = load_json(CATALOG_PATH)
    species = catalog.get("species")
    if not isinstance(species, list) or not species:
        raise BuildError("species_catalog.json has no species list")
    return catalog


def normalize_species(species: list[dict]) -> list[dict]:
    """Catalog entries sorted by canonical speciesKey; validates key uniqueness."""
    entries: list[dict] = []
    keys: set[str] = set()
    for raw in species:
        name = str(raw.get("name") or "").strip()
        if not name:
            raise BuildError(f"catalog entry without name: {raw.get('id')!r}")
        key = normalize_key(name)
        if not key:
            raise BuildError(f"catalog entry with empty speciesKey: {name!r}")
        if key in keys:
            raise BuildError(f"duplicate speciesKey {key!r} in catalog ({name!r})")
        keys.add(key)
        entries.append(raw)
    entries.sort(key=lambda entry: normalize_key(entry["name"]))
    return entries


def species_atoms(entry: dict) -> dict:
    level = str(entry.get("level") or "").strip()
    kind = str(entry.get("type") or "").strip()
    moves = []
    seen = set()
    for move in entry.get("specialMoves") or []:
        text = str(move or "").strip()
        if text and text not in seen:
            seen.add(text)
            moves.append(text)
    return {
        "speciesKey": normalize_key(entry["name"]),
        "speciesName": str(entry["name"]).strip(),
        "level": level if is_meaningful(level) else None,
        "type": kind if is_meaningful(kind) else None,
        "moves": moves,
        "tier": tier_for_level(level),
    }


def build_species_voice(species: list[dict]) -> dict:
    voices = [species_atoms(entry) for entry in normalize_species(species)]
    if len(voices) != SPECIES_VOICE_COUNT:
        raise BuildError(
            f"expected {SPECIES_VOICE_COUNT} species voices, catalog has {len(voices)}"
        )
    return {"schemaVersion": SCHEMA_VERSION, "voices": voices}


# ---------------------------------------------------------------------------
# Authored data banks
# ---------------------------------------------------------------------------
def load_bank(prefix: str) -> list[dict]:
    files = sorted(DATA_DIR.glob(f"{prefix}*.json"))
    if not files:
        raise BuildError(f"no authored bank files matching {prefix}*.json in {DATA_DIR}")
    rows: list[dict] = []
    for path in files:
        payload = load_json(path)
        if payload.get("schemaVersion") != SCHEMA_VERSION:
            raise BuildError(f"{path.name}: schemaVersion must be {SCHEMA_VERSION}")
        rows.append(payload)
    return rows


def load_topics() -> dict[str, dict]:
    topics: dict[str, dict] = {}
    for payload in load_bank("topics"):
        for key, topic in (payload.get("topics") or {}).items():
            if key in topics:
                raise BuildError(f"duplicate topic {key!r}")
            topics[key] = topic
    if not topics:
        raise BuildError("no topics authored")
    for key, topic in topics.items():
        frames = topic.get("frames") or {}
        for tone in TONES:
            bank = frames.get(tone)
            if not isinstance(bank, list) or not bank:
                raise BuildError(f"topic {key!r}: missing frame bank for tone {tone!r}")
            for index, text in enumerate(bank):
                check_line(str(text), f"topic {key}.frames.{tone}[{index}]")
        stage_frames = topic.get("stageFrames") or {}
        for tier in range(MIN_TIER, MAX_TIER + 1):
            text = stage_frames.get(str(tier))
            if not isinstance(text, str) or not text.strip():
                raise BuildError(f"topic {key!r}: missing stageFrames[{tier}]")
            check_line(text, f"topic {key}.stageFrames[{tier}]")
        pool = topic.get("chipPool") or []
        if not pool:
            raise BuildError(f"topic {key!r}: chipPool is empty")
    return topics


def load_intents() -> list[dict]:
    intents: list[dict] = []
    for payload in load_bank("intents"):
        for intent in payload.get("intents") or []:
            intents.append(intent)
    intents.sort(key=lambda intent: str(intent.get("id", "")))
    if not intents:
        raise BuildError("no intents authored")
    seen_ids: set[str] = set()
    for intent in intents:
        intent_id = str(intent.get("id") or "")
        if not re.fullmatch(r"fc\.[a-z0-9_]+\.[0-9]+", intent_id):
            raise BuildError(f"invalid intent id {intent_id!r} (want fc.<topic>.<n>)")
        if intent_id in seen_ids:
            raise BuildError(f"duplicate intent id {intent_id!r}")
        seen_ids.add(intent_id)
    return intents


def load_grounding() -> list[dict]:
    path = DATA_DIR / "grounding.json"
    payload = load_json(path)
    frames = payload.get("frames") or []
    if not frames:
        raise BuildError(f"{path.name}: no grounding frames")
    for index, frame in enumerate(frames):
        text = str(frame.get("text") or "")
        if not text:
            raise BuildError(f"{path.name}: frame {index} has no text")
        for slot in re.findall(r"\{([a-z_]+)\}", text):
            if slot not in {"name", "level", "type", "move", "tier"}:
                raise BuildError(f"{path.name}: frame {index} uses unknown slot {{{slot}}}")
    return frames


def load_chips() -> dict[str, dict]:
    """Merge every authored chip bank (data/chips*.json)."""
    chips: dict[str, dict] = {}
    texts: set[str] = set()
    rows: list[dict] = []
    for payload in load_bank("chips"):
        rows.extend(payload.get("chips") or [])
    for chip in rows:
        chip_id = str(chip.get("id") or "")
        text = str(chip.get("text") or "").strip()
        intent_id = str(chip.get("intentId") or "")
        if not re.fullmatch(r"chip\.[a-z0-9_]+", chip_id):
            raise BuildError(f"invalid chip id {chip_id!r}")
        if chip_id in chips:
            raise BuildError(f"duplicate chip id {chip_id!r}")
        if not text:
            raise BuildError(f"chip {chip_id!r}: empty text")
        normalized = normalize_loose(text)
        if normalized in texts:
            raise BuildError(f"duplicate chip text {text!r}")
        texts.add(normalized)
        chips[chip_id] = {"id": chip_id, "text": text, "intentId": intent_id}
    if not chips:
        raise BuildError("no chips authored")
    return chips


def load_fallbacks() -> dict[int, list[dict]]:
    payload = load_json(DATA_DIR / "fallbacks.json")
    levels: dict[int, list[dict]] = {}
    texts: set[str] = set()
    for level in range(1, 5):
        rows = (payload.get("levels") or {}).get(str(level)) or []
        if len(rows) < 6:
            raise BuildError(f"fallbacks.json: level {level} needs at least 6 lines")
        cleaned = []
        for index, row in enumerate(rows):
            text = str(row.get("text") or "")
            tone = str(row.get("tone") or "")
            if tone not in TONES:
                raise BuildError(
                    f"fallbacks.json level {level}[{index}]: invalid tone {tone!r}"
                )
            check_line(text, f"fallbacks level {level}[{index}]")
            normalized = normalize_loose(text)
            if normalized in texts:
                raise BuildError(f"fallbacks.json: duplicate fallback text {text!r}")
            texts.add(normalized)
            cleaned.append({"text": text, "tone": tone})
        levels[level] = cleaned
    return levels


def load_safety() -> dict[str, list[str]]:
    payload = load_json(DATA_DIR / "safety.json")
    safety: dict[str, list[str]] = {}
    for klass in ("S1", "S2", "S3", "PII"):
        terms = payload.get(klass)
        if not isinstance(terms, list) or not terms:
            raise BuildError(f"safety.json: class {klass} must be a non-empty list")
        cleaned: set[str] = set()
        for term in terms:
            text = str(term or "").strip().lower()
            if not text:
                raise BuildError(f"safety.json: empty term in {klass}")
            if text != normalize_loose(text):
                raise BuildError(
                    f"safety.json: term {term!r} in {klass} must be normalized "
                    "(lowercase, no accents/punctuation)"
                )
            cleaned.add(text)
        safety[klass] = sorted(cleaned)
    return safety


# ---------------------------------------------------------------------------
# Reply composition
# ---------------------------------------------------------------------------
def tone_skeleton(intent_number: int) -> list[str]:
    """Tone subset per intent; every topic's intents collectively cover all 8 tones."""
    skeletons = [
        ["warm", "dry", "compassion", "playful"],
        ["friendship", "courage", "insight", "formal"],
        ["warm", "insight", "courage", "dry"],
        ["compassion", "playful", "friendship", "formal"],
        ["warm", "dry", "playful", "insight"],
        ["courage", "compassion", "friendship", "formal"],
    ]
    return skeletons[(intent_number - 1) % len(skeletons)]


def variant_profile(topic_key: str, intent_number: int, global_index: int,
                    scale: int, topic: dict) -> tuple[list[str], int]:
    """Tone subset + frames-per-tone for one intent.

    Shipped profile (`--scale 1`): deep topics get all 8 tones x 2 authored frames
    (deep variant pools for the high-traffic intents), other topics keep the
    4-tone skeleton with 1 frame. `--scale 2` renders every intent deep.
    """
    if topic_key in DEEP_TOPICS or scale >= 2:
        tones = [TONES[(global_index * 3 + offset) % len(TONES)] for offset in range(len(TONES))]
        authored = min(len(topic["frames"][tone]) for tone in tones)
        return tones, max(1, min(2, authored))
    return tone_skeleton(intent_number), 1


def derive_keywords(phrases: list[str]) -> list[str]:
    counts: dict[str, int] = {}
    for phrase in phrases:
        tokens = [token for token in normalize_loose(phrase).split(" ") if token]
        for token in tokens:
            if token in STOPWORDS or len(token) < 2:
                continue
            counts[token] = counts.get(token, 0) + 1
    ranked = sorted(counts.items(), key=lambda item: (-item[1], item[0]))
    return [token for token, _ in ranked[:8]]


def derive_tags(topic: str, authored_tags) -> list[str]:
    tags = {"owned", topic}
    for tag in authored_tags or []:
        text = str(tag or "").strip().lower()
        if text:
            tags.add(text)
    return sorted(tags)


class PackBuilder:
    def __init__(self, catalog: dict, scale: int, limit: int):
        self.catalog = catalog
        self.scale = scale
        self.limit = limit
        self.topics = load_topics()
        self.intents_data = load_intents()
        self.grounding = load_grounding()
        self.chips = load_chips()
        self.fallbacks = load_fallbacks()
        self.safety = load_safety()
        self.species = normalize_species(catalog["species"])
        self.species_voice = build_species_voice(catalog["species"])
        self.voice_keys = [voice["speciesKey"] for voice in self.species_voice["voices"]]
        self.known_species_keys = set(self.voice_keys)
        self.valid_grounding_species = self._grounding_species()
        self.reply_texts: dict[str, str] = {}
        self.reply_ids: set[str] = set()
        self.phrase_owner: dict[str, str] = {}
        self.intent_ids: set[str] = {str(intent["id"]) for intent in self.intents_data}
        self.stats = {
            "intents": 0,
            "replies": 0,
            "match_phrases": 0,
            "chips": len(self.chips),
            "stage_replies": 0,
            "grounded_replies": 0,
        }

    # -- input validation -------------------------------------------------
    def _grounding_species(self) -> list[dict]:
        """Species that can render every grounding slot without overlong lines."""
        rows = []
        for entry in self.species:
            atoms = species_atoms(entry)
            if atoms["level"] is None or atoms["type"] is None or not atoms["moves"]:
                continue
            if len(atoms["speciesName"]) > 28:
                continue
            if len(atoms["moves"][0]) > 26:
                continue
            atom_text = f"{atoms['speciesName']} {atoms['type']} {atoms['moves'][0]}".lower()
            if BANNED_ATOM_PATTERN.search(atom_text):
                continue
            rows.append(atoms)
        if len(rows) < 300:
            raise BuildError(
                f"grounding: only {len(rows)} species usable with full fact atoms (need >= 300)"
            )
        return rows

    def validate_inputs(self) -> None:
        for intent in self.intents_data:
            intent_id = str(intent["id"])
            topic_key = str(intent.get("topic"))
            if topic_key not in self.topics:
                raise BuildError(f"{intent_id}: unknown topic {topic_key!r}")
            topic = self.topics[topic_key]
            phrases = intent.get("matchPhrases") or []
            if len(phrases) < 6:
                raise BuildError(f"{intent_id}: needs at least 6 match phrases")
            for phrase in phrases:
                normalized = normalize_loose(phrase)
                if not normalized:
                    raise BuildError(f"{intent_id}: empty match phrase")
                if normalized in self.phrase_owner:
                    raise BuildError(
                        f"{intent_id}: duplicate match phrase {phrase!r} "
                        f"(already on {self.phrase_owner[normalized]})"
                    )
                self.phrase_owner[normalized] = intent_id
            openers = intent.get("openers") or []
            if len(openers) != 3:
                raise BuildError(f"{intent_id}: needs exactly 3 authored openers")
            for index, opener in enumerate(openers):
                check_line(str(opener), f"{intent_id}.openers[{index}]")
                if not re.search(r"[.!?]$", str(opener)):
                    raise BuildError(f"{intent_id}.openers[{index}]: opener must end with punctuation")
            fallback_id = str(intent.get("fallbackId") or "")
            if not re.fullmatch(r"fallback\.[1-4]", fallback_id):
                raise BuildError(f"{intent_id}: fallbackId {fallback_id!r} must be fallback.1..4")
            conditions = intent.get("conditions") or {}
            audience = conditions.get("audience", "owned")
            if audience not in ("owned", "wild", None):
                raise BuildError(f"{intent_id}: invalid audience {audience!r}")
            for band in conditions.get("moodBands") or []:
                if band not in ("LOW", "SETTLED", "POSITIVE"):
                    raise BuildError(f"{intent_id}: invalid mood band {band!r}")
            for tier in conditions.get("stageTiers") or []:
                if tier not in (1, 2, 3, 4):
                    raise BuildError(f"{intent_id}: invalid stage tier {tier!r}")
            for type_number in conditions.get("types") or []:
                if type_number not in range(1, 17):
                    raise BuildError(f"{intent_id}: invalid personality type {type_number!r}")
            for chip_id in topic["chipPool"]:
                if chip_id not in self.chips:
                    raise BuildError(f"topic {topic_key!r}: unknown chip id {chip_id!r}")
        for chip in self.chips.values():
            if chip["intentId"] not in self.intent_ids:
                raise BuildError(f"chip {chip['id']!r}: unknown intentId {chip['intentId']!r}")
            target = next(
                intent for intent in self.intents_data if intent["id"] == chip["intentId"]
            )
            target_phrases = {normalize_loose(p) for p in target.get("matchPhrases") or []}
            if normalize_loose(chip["text"]) not in target_phrases:
                raise BuildError(
                    f"chip {chip['id']!r}: text {chip['text']!r} must be one of "
                    f"{chip['intentId']} match phrases (retriever consistency)"
                )
        if len(self.intents_data) < MIN_INTENTS:
            raise BuildError(
                f"authored intents: {len(self.intents_data)} < shipped target {MIN_INTENTS}"
            )

    # -- generation -------------------------------------------------------
    def _add_reply(self, replies: list[dict], intent_id: str, text: str, tone: str,
                   types: list[int], stage_tiers: list[int], mood_delta: int,
                   energy: int, lore_refs: list[str]) -> None:
        check_line(text, f"{intent_id} reply")
        normalized = normalize_loose(text)
        if normalized in self.reply_texts:
            raise BuildError(
                f"{intent_id}: duplicate reply text {text!r} "
                f"(already generated as {self.reply_texts[normalized]})"
            )
        reply_id = f"{intent_id}.r{len(replies) + 1}"
        if reply_id in self.reply_ids:
            raise BuildError(f"duplicate reply id {reply_id!r}")
        self.reply_ids.add(reply_id)
        self.reply_texts[normalized] = reply_id
        replies.append({
            "id": reply_id,
            "text": text,
            "tone": tone,
            "stageTiers": stage_tiers,
            "types": types,
            "moodDelta": mood_delta,
            "energy": energy,
            "loreRefs": lore_refs,
            "authoringStatus": "draft",
        })

    def _render_grounding(self, frame: dict, atoms: dict) -> str:
        text = str(frame["text"])
        replacements = {
            "name": atoms["speciesName"],
            "level": atoms["level"],
            "type": atoms["type"],
            "move": atoms["moves"][0],
            "tier": str(atoms["tier"]),
        }
        for slot, value in replacements.items():
            text = text.replace("{" + slot + "}", str(value))
        return text

    def build_intent(self, intent: dict, global_index: int) -> dict:
        intent_id = str(intent["id"])
        topic_key = str(intent["topic"])
        topic = self.topics[topic_key]
        intent_number = int(intent_id.rsplit(".", 1)[1])
        openers = [str(opener) for opener in intent["openers"]]
        replies: list[dict] = []

        tones, frames_per_tone = variant_profile(
            topic_key, intent_number, global_index, self.scale, topic
        )

        typed_positions = {0, len(tones) - 1}
        for opener in openers:
            opener_clause = squash_sentences(opener)
            for position, tone in enumerate(tones):
                frame_bank = topic["frames"][tone]
                for frame_index in range(frames_per_tone):
                    frame = squash_sentences(str(frame_bank[frame_index % len(frame_bank)]))
                    types = TONE_TYPES[tone] if position in typed_positions else []
                    self._add_reply(
                        replies, intent_id, f"{opener_clause} {frame}", tone, types, [],
                        int(intent.get("moodDelta") or 0), TONE_ENERGY[tone], [],
                    )

        if intent.get("stage"):
            for tier in range(MIN_TIER, MAX_TIER + 1):
                opener = squash_sentences(openers[(tier - 1) % len(openers)])
                frame = squash_sentences(str(topic["stageFrames"][str(tier)]))
                tone = STAGE_TONE_ORDER[(global_index + tier) % len(STAGE_TONE_ORDER)]
                self._add_reply(
                    replies, intent_id, f"{opener} {frame}", tone, [], [tier], 0,
                    STAGE_TIER_ENERGY[tier - 1], [],
                )
                self.stats["stage_replies"] += 1

        if intent.get("grounded"):
            for frame_index, frame in enumerate(self.grounding):
                species = self.valid_grounding_species[
                    (global_index * 37 + frame_index * 131) % len(self.valid_grounding_species)
                ]
                text = self._render_grounding(frame, species)
                tone = TONES[(global_index + frame_index) % len(TONES)]
                self._add_reply(
                    replies, intent_id, text, tone, [], [], 0, 1, [species["speciesKey"]],
                )
                self.stats["grounded_replies"] += 1

        conditions = intent.get("conditions") or {}
        chips = self._intent_chips(topic, intent_id, intent_number)

        return {
            "id": intent_id,
            "topic": topic_key,
            "tags": derive_tags(topic_key, intent.get("tags")),
            "matchPhrases": [str(phrase) for phrase in intent["matchPhrases"]],
            "keywords": derive_keywords([str(phrase) for phrase in intent["matchPhrases"]]),
            "conditions": {
                "audience": conditions.get("audience", "owned"),
                "stageTiers": sorted(conditions.get("stageTiers") or []),
                "types": sorted(conditions.get("types") or []),
                "cores": sorted(conditions.get("cores") or []),
                "moodBands": sorted(conditions.get("moodBands") or []),
                "recentEvents": sorted(conditions.get("recentEvents") or []),
            },
            "replies": replies,
            "chips": chips,
            "fallbackId": str(intent.get("fallbackId") or "fallback.2"),
        }

    def _intent_chips(self, topic: dict, intent_id: str, intent_number: int) -> list[dict]:
        pool = list(topic["chipPool"])
        chips: list[dict] = []
        for offset in range(2):
            index = (intent_number - 1 + offset) % len(pool)
            chip_id = pool[index]
            if self.chips[chip_id]["intentId"] == intent_id:
                chip_id = pool[(index + 1) % len(pool)]
            chips.append(dict(self.chips[chip_id]))
        unique = {chip["id"]: chip for chip in chips}
        return [unique[chip_id] for chip_id in sorted(unique)]

    def build_fallbacks(self) -> list[dict]:
        fallbacks = []
        for level in range(1, 5):
            replies = []
            for index, row in enumerate(self.fallbacks[level]):
                reply_id = f"fallback.{level}.r{index + 1}"
                if reply_id in self.reply_ids:
                    raise BuildError(f"duplicate fallback reply id {reply_id!r}")
                normalized = normalize_loose(row["text"])
                if normalized in self.reply_texts:
                    raise BuildError(
                        f"fallback {reply_id}: duplicate text {row['text']!r} "
                        f"(already generated as {self.reply_texts[normalized]})"
                    )
                self.reply_ids.add(reply_id)
                self.reply_texts[normalized] = reply_id
                replies.append({
                    "id": reply_id,
                    "text": row["text"],
                    "tone": row["tone"],
                    "stageTiers": [],
                    "types": [],
                    "moodDelta": 0,
                    "energy": 0,
                    "loreRefs": [],
                    "authoringStatus": "draft",
                })
            fallbacks.append({"level": level, "replies": replies})
        return fallbacks

    def build(self) -> tuple[dict, dict]:
        self.validate_inputs()
        intents = self.intents_data
        if self.limit > 0:
            intents = intents[: self.limit]
        built: list[dict] = []
        for global_index, intent in enumerate(intents):
            built.append(self.build_intent(intent, global_index))
        self.stats["intents"] = len(built)
        self.stats["replies"] = sum(len(intent["replies"]) for intent in built)
        self.stats["match_phrases"] = sum(len(intent["matchPhrases"]) for intent in built)
        # Smoke runs (--limit) must stay self-consistent: drop chips whose target
        # intent is not part of the limited pack (and keep top-level chips in sync).
        built_ids = {intent["id"] for intent in built}
        if self.limit > 0:
            for intent in built:
                intent["chips"] = [
                    chip for chip in intent["chips"] if chip["intentId"] in built_ids
                ]
        top_chips = [
            self.chips[chip_id] for chip_id in sorted(self.chips)
            if self.chips[chip_id]["intentId"] in built_ids
        ]
        fallbacks = self.build_fallbacks()
        pack = {
            "schemaVersion": SCHEMA_VERSION,
            "contentVersion": CONTENT_VERSION,
            "locale": LOCALE,
            "intents": built,
            "chips": top_chips,
            "fallbacks": fallbacks,
            "safety": {klass: list(terms) for klass, terms in self.safety.items()},
        }
        manifest = {
            "schemaVersion": SCHEMA_VERSION,
            "contentVersion": CONTENT_VERSION,
            "locale": LOCALE,
            "files": {
                "databaseFallback": DB_FALLBACK_FILE,
                "intents": "free_chat.json",
                "speciesVoice": "species_voice.json",
            },
        }
        if self.limit <= 0:
            if self.stats["intents"] < MIN_INTENTS:
                raise BuildError("generated intents below shipped target")
            if self.stats["replies"] < MIN_REPLIES:
                raise BuildError("generated replies below shipped target")
            if self.stats["match_phrases"] < MIN_MATCH_PHRASES:
                raise BuildError("generated match phrases below shipped target")
        return manifest, pack


# ---------------------------------------------------------------------------
# Runtime database fallback (owner requirement 2026-11-18)
#
# LAST-RESORT dataset dialogue only. The generated intent/reply corpus is the
# priority answer source; these pairs are never used to compose generated text.
# Owner accepts responsibility for the public-corpus samples (personal build);
# DISTRIBUTION_GATE.md removes/replaces this asset before distribution.
# ---------------------------------------------------------------------------
DB_ASSISTANT_BANNED = [
    "as an ai", "i'm an ai", "i am an ai", "language model", "neural network",
    "i'm here to help", "i am here to help", "how can i help", "how may i assist",
    "let me know if", "i'm sorry, but i can't", "i cannot help", "i can't help with",
    "artificial intelligence", "openai", "chatgpt", "virtual assistant", "happy to help",
    "is there anything else", "i'm just a", "training data", "prompt engineering",
    "i do not currently have", "i don't currently have", "i am programmed", "i'm programmed",
    "i recommend", "i suggest", "hope this helps", "in conclusion", "consult your",
    "i'm sorry, i can't", "i am unable", "as a language model",
]

# Word-boundary patterns for meta/out-of-world replies that read as a chatbot or a
# real-world scene instead of in-world dialogue. Applied to the response only.
DB_RESPONSE_BANNED_PATTERNS = [
    re.compile(r"\bchat\s?bot\b"),
    re.compile(r"\ba bot\b"),
    re.compile(r"\b(ai|a\.i\.)\b"),
    re.compile(r"\bassistant\b"),
    re.compile(r"\bprogrammed\b"),
    re.compile(r"\balgorithm\b"),
    re.compile(r"\bwikipedia\b"),
    re.compile(r"\binternet\b"),
    re.compile(r"\bsmartphone\b"),
    re.compile(r"\bpresident\b"),
    re.compile(r"\brabbi\b"),
    re.compile(r"\belection\b"),
    re.compile(r"\bsee\b.*\bwebsite\b"),
]

# Stage-direction / screenplay junk that reads like a movie script, not a chat reply.
DB_RESPONSE_JUNK_PATTERNS = [
    re.compile(r"^\s*[\(\[].*[\)\]]"),
    re.compile(r"--"),
    re.compile(r"^\s*(int|ext)\.\s"),
]
DB_SEXUAL_BANNED = [
    "sex", "sexy", "sexual", "nude", "naked", "porn", "erotic", "orgasm", "masturbat",
    "blowjob", "condom", "breasts", "boobs", "hookup", "hook up", "make love",
    "sleep with me", "virginity", "virgin", "prostitute", "stripper", "seduce",
]
DB_ABUSE_BANNED = [
    "rape", "raped", "molest", "suicide", "kill yourself", "murder", "stab", "shoot",
    "gun", "knife", "torture", "beat you", "hit you", "hurt you", "threaten",
    "i'll kill", "i will kill", "kill you", "you're dead", "you are dead", "shut up",
    "get out of here", "i hate you", "bitch", "asshole", "bastard", "dick", "pussy",
    "cunt", "whore", "slut", "idiot", "stupid", "moron",
]
# Profanity/romance roots matched as substrings on the normalized text (the
# word-boundary lists above miss inflections such as "fuckin'").
DB_ROOT_BANNED = {
    "fuck": "profanity", "shit": "profanity", "bullshit": "profanity",
    "goddamn": "profanity", "damn": "profanity", "motherfuck": "profanity",
    "dickhead": "profanity", "nigg": "abusive", "fag": "abusive",
    "kiss": "romance", "making out": "romance",
}
DB_PII_PATTERNS = [
    re.compile(r"[\w.+-]+@[\w-]+\.[\w.]+"),                     # e-mail
    re.compile(r"https?://|www\."),                              # URLs
    re.compile(r"(?<!\w)@[A-Za-z0-9_]{2,}"),                     # @handles
    re.compile(r"(?<!\d)(?:\+?\d[\s.-]?){7,}\d(?!\d)"),          # phone-like digit runs
    re.compile(r"(?<!\w)\d{3}-\d{2}-\d{4}(?!\w)"),               # SSN-like
]
DB_PII_PHRASES = [
    "my address", "where i live", "phone number", "social security", "credit card",
    "my number is", "call me at", "text me at", "email me at",
]
# Language sanity: corpus samples occasionally contain non-EN rows or numeric-only
# arithmetic rows; a last-resort line must at least read as short EN/PT dialogue.
DB_LANG_MARKERS = set("""
the and you to a i is it that of me my your we this be are for on with have not can so but
just know like want no yes ok okay thank thanks sorry please what how why where when who did
do does was were will would could should if at in from about there here they he she them his
her our us all very really good great well right now then too also because or up down out one
two time day people thing going get got think thought said say see look come came make made
take took let tell told give gave never always been being has had more most only some any every
much many other new old big small long last first after before over under again still even back
way around through between both few own same another such off while into during without within
against above below near far since until though although yet whether either neither each ones
voce nao sim obrigado obrigada oi ola por para que com uma meu minha isso aqui eu bem tudo muito
tambem agora porque como estou esta ser tem ter fazer vai vou quer quero pode posso dele dela
voces eles elas foi era sou desculpa desculpe bom boa dia noite tarde
""".split())


def _language_markers(response: str) -> int:
    tokens = [token for token in normalize_loose(response).split(" ") if token]
    return sum(1 for token in tokens if token in DB_LANG_MARKERS)


def _db_row_rejection(user: str, response: str) -> str | None:
    """Return a rejection reason for an owner-provided pair, or None to keep it."""
    if len(user) < 2 or len(response) < 2:
        return "too-short"
    if not re.search(r"[A-Za-z]", user):
        return "no-letters"
    response_alpha_words = [
        token for token in normalize_loose(response).split(" ") if re.search(r"[a-z]", token)
    ]
    if len(response_alpha_words) < 2:
        return "too-thin"
    if _language_markers(response) < 2:
        return "language"
    for text, label in ((user, "user"), (response, "response")):
        normalized = normalize_loose(text)
        if not normalized:
            return "empty"
        if any(ch in text for ch in "\u0000\u0001\u0002\u0003"):
            return "control-characters"
        for root, reason in DB_ROOT_BANNED.items():
            if root in normalized:
                return reason
        for term in DB_SEXUAL_BANNED:
            if re.search(rf"(?<![a-z]){re.escape(term)}(?![a-z])", normalized):
                return "sexual"
        for term in DB_ABUSE_BANNED:
            if re.search(rf"(?<![a-z]){re.escape(term)}(?![a-z])", normalized):
                return "abusive"
        for pattern in DB_PII_PATTERNS:
            if pattern.search(text):
                return "pii"
        for phrase in DB_PII_PHRASES:
            if phrase in normalized:
                return "pii"
    response_normalized = normalize_loose(response)
    for phrase in DB_ASSISTANT_BANNED:
        if phrase in response_normalized:
            return "assistant-phrase"
    for pattern in DB_RESPONSE_BANNED_PATTERNS:
        if pattern.search(response_normalized):
            return "assistant-phrase"
    for pattern in DB_RESPONSE_JUNK_PATTERNS:
        if pattern.search(response):
            return "screenplay"
    # A last-resort line must read like one conversational turn, not a monologue.
    if len(re.findall(r"[.!?]", response)) > 3:
        return "too-many-sentences"
    if len(response) > DB_MAX_RESPONSE_CHARS:
        return "too-long"
    return None


def load_database_seed_rows(seeds_path: Path) -> tuple[list[dict], list[str], dict[str, int]]:
    """Read every owner-provided JSONL seed file, filtered and deduped."""
    if seeds_path.is_dir():
        paths = sorted(seeds_path.glob("*.jsonl"))
    elif seeds_path.is_file():
        paths = [seeds_path]
    else:
        raise BuildError(
            f"database seeds not found: {seeds_path} (pass --database-seeds <dir-or-jsonl>)"
        )
    if not paths:
        raise BuildError(
            f"no *.jsonl database seed files in {seeds_path} "
            "(pass --database-seeds <dir-or-jsonl>)"
        )
    kept: dict[tuple[str, str], dict] = {}
    rejected: dict[str, int] = {}
    for path in paths:
        with path.open("r", encoding="utf-8-sig") as handle:
            for line_number, raw in enumerate(handle, start=1):
                line = raw.strip()
                if not line:
                    continue
                try:
                    row = json.loads(line)
                except json.JSONDecodeError as error:
                    raise BuildError(
                        f"{path.name}:{line_number}: invalid JSONL ({error})"
                    ) from error
                if not isinstance(row, dict):
                    continue
                user = str(row.get("user") or "").strip()
                response = str(row.get("response") or "").strip()
                source = str(row.get("source") or "").strip()
                license_text = str(row.get("license") or "").strip() or DB_LICENSE_BY_SOURCE.get(source, "")
                if not source or not license_text:
                    rejected["missing-source-license"] = rejected.get("missing-source-license", 0) + 1
                    continue
                if len(response) > DB_MAX_RESPONSE_CHARS or len(user) > DB_MAX_USER_CHARS:
                    rejected["too-long"] = rejected.get("too-long", 0) + 1
                    continue
                reason = _db_row_rejection(user, response)
                if reason is not None:
                    rejected[reason] = rejected.get(reason, 0) + 1
                    continue
                key = (normalize_loose(user), normalize_loose(response))
                if key in kept:
                    rejected["duplicate"] = rejected.get("duplicate", 0) + 1
                    continue
                kept[key] = {
                    "user": user,
                    "response": response,
                    "source": source,
                    "license": license_text,
                }
    rows = [kept[key] for key in sorted(kept)]
    return rows, [path.name for path in paths], rejected


def build_database_fallback(seeds_path: Path, smoke: bool) -> tuple[dict, dict]:
    """Build the deterministic last-resort pair asset from the seed samples."""
    rows, seed_files, rejected = load_database_seed_rows(seeds_path)

    def sort_key(pair: dict):
        return (
            pair["source"],
            normalize_loose(pair["user"]),
            normalize_loose(pair["response"]),
            pair["response"],
        )

    rows.sort(key=sort_key)
    pt_rows = [row for row in rows if row["source"].endswith("-pt")]
    other_rows = [row for row in rows if not row["source"].endswith("-pt")]
    selected_pt = pt_rows[:DB_PT_PAIR_LIMIT]
    selected_other = other_rows[: DB_PAIR_LIMIT - len(selected_pt)]
    selected = selected_other + selected_pt
    selected.sort(key=sort_key)
    if not selected:
        raise BuildError(f"database fallback: no usable pairs in {seeds_path}")
    if not smoke and len(selected) < 3000:
        raise BuildError(
            f"database fallback: {len(selected)} pairs < shipped floor 3000 "
            f"(seed files: {', '.join(seed_files)})"
        )
    by_source: dict[str, int] = {}
    for pair in selected:
        by_source[pair["source"]] = by_source.get(pair["source"], 0) + 1
    payload = {
        "schemaVersion": DB_SCHEMA_VERSION,
        "contentVersion": DB_CONTENT_VERSION,
        "locale": LOCALE,
        "source": DB_SOURCE,
        "pairs": selected,
    }
    stats = {
        "pairCount": len(selected),
        "bySource": {key: by_source[key] for key in sorted(by_source)},
        "seedFiles": seed_files,
        "rejected": {key: rejected[key] for key in sorted(rejected)},
        "availableSeedPairs": len(rows),
    }
    return payload, stats


# ---------------------------------------------------------------------------
# Optional seed-pair mining (report only; shipped corpus is never seed text)
# ---------------------------------------------------------------------------
def mine_seed_pairs(path: Path, intents: list[dict]) -> dict:
    """Mine dialogue-act n-grams from public turn pairs for coverage reporting.

    The seed text is NEVER copied into the pack: this function only counts
    candidate user phrasings and reports which authored intents already cover
    them, so a content owner can see coverage gaps. The shipped assets do not
    depend on this input at all.
    """
    if not path.is_file():
        raise BuildError(f"--seed-pairs file not found: {path}")
    user_turns: list[str] = []
    pair_count = 0
    user_keys = ("prompt", "user", "player", "input", "question", "tamer", "text")
    with path.open("r", encoding="utf-8-sig") as handle:
        for line_number, raw in enumerate(handle, start=1):
            line = raw.strip()
            if not line:
                continue
            try:
                row = json.loads(line)
            except json.JSONDecodeError as error:
                raise BuildError(f"{path.name}:{line_number}: invalid JSONL ({error})") from error
            if not isinstance(row, dict):
                continue
            found = [str(row[key]) for key in user_keys if isinstance(row.get(key), str)]
            if not found:
                found = [value for value in row.values() if isinstance(value, str)]
            if found:
                pair_count += 1
                user_turns.extend(found)

    phrase_counts: dict[str, int] = {}
    for turn in user_turns:
        tokens = [token for token in normalize_loose(turn).split(" ") if token]
        for size in (2, 3):
            for start in range(0, max(0, len(tokens) - size + 1)):
                phrase = " ".join(tokens[start:start + size])
                if len(phrase) < 6:
                    continue
                phrase_counts[phrase] = phrase_counts.get(phrase, 0) + 1

    intent_tokens = {}
    for intent in intents:
        tokens: set[str] = set()
        for phrase in intent.get("matchPhrases") or []:
            tokens.update(token for token in normalize_loose(phrase).split(" ") if token)
        intent_tokens[str(intent["id"])] = tokens

    candidates = []
    covered = 0
    for phrase, count in sorted(phrase_counts.items(), key=lambda item: (-item[1], item[0])):
        if count < 2:
            continue
        words = set(phrase.split(" "))
        covered_by = sorted(
            intent_id for intent_id, tokens in intent_tokens.items() if words <= tokens
        )
        if covered_by:
            covered += 1
        if len(candidates) < 200:
            candidates.append({
                "phrase": phrase,
                "count": count,
                "coveredBy": covered_by[:5],
            })
    mined = len([count for count in phrase_counts.values() if count >= 2])
    return {
        "schemaVersion": SCHEMA_VERSION,
        "source": path.name,
        "pairCount": pair_count,
        "userTurnCount": len(user_turns),
        "candidatePhrases": candidates,
        "miningOnly": True,
        "shippedTextFromSeed": 0,
        "note": (
            "Offline mining output only. The generator never copies seed text: the authored "
            "banks in tools/freechat/data/ are written for this project and the shipped pack is "
            "byte-identical whether or not --seed-pairs is passed."
        ),
        "coverage": {
            "candidateCount": mined,
            "coveredCandidateCount": covered,
            "uncoveredCandidateCount": mined - covered,
        },
    }


# ---------------------------------------------------------------------------
# CLI
# ---------------------------------------------------------------------------
def parse_args(argv: list[str]) -> argparse.Namespace:
    parser = argparse.ArgumentParser(
        description="Deterministic Free Talk v1 content-pack generator (workstream A)."
    )
    parser.add_argument(
        "--scale", type=int, default=1,
        help="1 (default) = seed corpus target; 2 = expanded rendering (all tones x 2 frames)",
    )
    parser.add_argument(
        "--limit", type=int, default=0,
        help="limit intents to the first N sorted ids (smoke runs only)",
    )
    parser.add_argument(
        "--seed-pairs", type=Path, default=None,
        help="optional public-corpus JSONL turn pairs; mined for a report only, never copied",
    )
    parser.add_argument(
        "--database-seeds", type=Path, default=SEED_DIR,
        help="owner-provided public-dialogue JSONL samples (dir or file) for "
             f"{DB_FALLBACK_FILE} (default tools/freechat/_seeds/)",
    )
    return parser.parse_args(argv)


def main(argv: list[str] | None = None) -> int:
    args = parse_args(list(argv if argv is not None else sys.argv[1:]))
    if args.scale < 1:
        print("error: --scale must be >= 1", file=sys.stderr)
        return 2
    if args.limit < 0:
        print("error: --limit must be >= 0", file=sys.stderr)
        return 2
    try:
        catalog = load_catalog()
        builder = PackBuilder(catalog, scale=args.scale, limit=args.limit)
        manifest, pack = builder.build()
        database, database_stats = build_database_fallback(
            args.database_seeds, smoke=args.limit > 0
        )
    except BuildError as error:
        print(f"error: {error}", file=sys.stderr)
        return 1

    write_json(OUT_EN / "manifest.json", manifest)
    write_json(OUT_EN / "free_chat.json", pack)
    write_json(OUT_EN / "species_voice.json", builder.species_voice)
    write_json(OUT_EN / DB_FALLBACK_FILE, database)

    stats = builder.stats
    print("Free Talk v1 content pack generated")
    print(f"  scale:          {args.scale}")
    print(f"  locale:         {LOCALE}")
    print(f"  contentVersion: {CONTENT_VERSION}")
    print(f"  intents:        {stats['intents']}")
    print(f"  replies:        {stats['replies']} "
          f"(stage {stats['stage_replies']}, grounded {stats['grounded_replies']})")
    print(f"  fallback lines: {sum(len(level) for level in builder.fallbacks.values())}")
    print(f"  match phrases:  {stats['match_phrases']}")
    print(f"  chips:          {stats['chips']}")
    print(f"  species voices: {len(builder.species_voice['voices'])}")
    print(f"  database pairs: {database_stats['pairCount']} "
          f"from {', '.join(database_stats['seedFiles'])}")
    print(f"  database by source: {json.dumps(database_stats['bySource'], sort_keys=True)}")
    if database_stats["rejected"]:
        print(f"  database rejected: {json.dumps(database_stats['rejected'], sort_keys=True)}")
    for name in ("manifest.json", "free_chat.json", "species_voice.json", DB_FALLBACK_FILE):
        data = (OUT_EN / name).read_bytes()
        print(f"  sha256 {name}: {hashlib.sha256(data).hexdigest()}")
    if args.limit > 0:
        print(f"  note: --limit {args.limit} produced a SMOKE pack, not the shipped target")
    if args.seed_pairs is not None:
        report = mine_seed_pairs(args.seed_pairs, builder.intents_data)
        write_json(SEED_REPORT_PATH, report)
        print(
            "  seed-pairs: mined "
            f"{report['coverage']['candidateCount']} candidate phrases, "
            f"{report['coverage']['coveredCandidateCount']} already covered; "
            f"report -> {SEED_REPORT_PATH.name} (assets unchanged)"
        )
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
